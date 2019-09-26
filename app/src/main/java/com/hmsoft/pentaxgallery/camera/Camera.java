package com.hmsoft.pentaxgallery.camera;

import android.support.annotation.WorkerThread;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.FilteredImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.WifiHelper;

import java.io.File;
import java.util.List;

public class Camera {
    
    private static final String TAG = "Camera";
  
    private final CameraController mController;
  
    private boolean mCameraConnected;
    private CameraData mCameraData;
    private int mCurrentStorageIndex;
    private FilteredImageList mFilteredImageList = null;

    public Camera(CameraController controller) {
        this.mController = controller;
    }

    @WorkerThread
    public CameraData connect() {
      CameraData cameraData = null;
      mCameraConnected = false;

      int retryes = 3;
      int ci = 0;
      while (!mCameraConnected) {

          if (mController.connectToCamera()) {
              BaseResponse response = mController.ping();
              if (response != null && response.success) {
                  cameraData = mController.getDeviceInfo(true);
              }
          } else {
              Logger.warning(TAG, "Could not bind to WiFi");
          }

          if (cameraData == null) {

              if(retryes-- == 0) {
                  Logger.warning(TAG, "Too many failed attempts to connect");
                  break;
              }

              List<CameraData> cameras = CameraData.getRegisteredCameras();
              if (cameras != null && cameras.size() > 0) {
                  WifiHelper.turnWifiOn(MyApplication.ApplicationContext, 1000);

                  if(ci == cameras.size()) {
                      Logger.warning(TAG, "All registered cameras failed to connect");
                      break;
                  }

                  cameraData = cameras.get(ci++);
                  if(BuildConfig.DEBUG) Logger.debug(TAG, "Attempting to connect to " + cameraData.ssid);

                  boolean success = WifiHelper.connectToWifi(MyApplication.ApplicationContext,
                          cameraData.ssid, cameraData.key);
                  if (!success) {
                      Logger.warning(TAG, "Could not connect to " + cameraData.ssid);
                      cameraData = null;
                  } else {
                      Logger.warning(TAG, "Connected to " + cameraData.ssid);
                  }
              } else {
                  Logger.warning(TAG, "No previously connected cameras found.");
                  break;
              }
          }

          mCameraConnected = cameraData != null;
      }

      if(cameraData == null) {
          cameraData = mController.getDeviceInfo(false);
          if(BuildConfig.DEBUG) Logger.debug(TAG, "Camera data from cache: " + cameraData);
      }

      setCameraData(cameraData);
      
       int activeStorageIndex = -1;
        if(cameraData != null) {
            for (StorageData storage : cameraData.storages) {
                activeStorageIndex++;
                if (storage.active) {
                    break;
                }
            }
        }

        int storageIndex = Math.max(activeStorageIndex, 0);
        
        setCurrentStorageIndex(storageIndex);
        if(cameraData != null && mCameraConnected) {
            cameraData.saveData();
        }
      
      return cameraData;
    }

    @WorkerThread
    public ImageList loadImageList(boolean ignoreCache) {
        ImageListData imageListResponse = mController.getImageList(getCurrentStorage(),
                mCameraConnected || ignoreCache);
      
        if(imageListResponse != null) {
          setImageList(imageListResponse.dirList);
          return imageListResponse.dirList;
        }

        setImageList(null);
        return null;
    }
  
    public  void setImageList(ImageList list) {
        StorageData storageData = getCurrentStorage();
        storageData.setImageList(list);
        if(list != null) {
            list.setStorageData(storageData);
        }
    }
  
    public boolean isConnected() {
      return mCameraConnected;
    }
    
    public boolean isFiltered() {
      return mFilteredImageList != null;
    }
  
    public CameraData getCameraData() {
        return mCameraData;
    }

    public void setCameraData(CameraData cameraData) {
        mCameraData = cameraData;
    }
  
    public int getCurrentStorageIndex() {
        return mCurrentStorageIndex;
    }

    public void setCurrentStorageIndex(int currentStorageIndex) {
        mCurrentStorageIndex = currentStorageIndex;
        setImageFilter(null);
    }
  
    public StorageData getCurrentStorage() {
        if(mCameraData != null) {
            if(mCurrentStorageIndex < 0 || mCurrentStorageIndex >= mCameraData.storages.size()) {
                mCurrentStorageIndex = 0;
            }
            return  mCameraData.storages.get(mCurrentStorageIndex);
        }
        return StorageData.DefaultStorage;
    }

    public ImageList getImageList() {        

        if(isFiltered()) {
            return mFilteredImageList.getList();
        }

        StorageData storageData = getCurrentStorage();
        return storageData != null ? storageData.getImageList() : null;
    }
  
    public void setImageFilter(FilteredImageList.ImageFilter filter) {
        if(filter != null) {
            StorageData storageData = getCurrentStorage();
            mFilteredImageList = new FilteredImageList(storageData.getImageList(), filter);
            mFilteredImageList.setStorageData(storageData);
        } else {
            if(BuildConfig.DEBUG) Logger.debug(TAG, "No Filter set");
            mFilteredImageList = null;
        }
    }

    public void setImageFilterText(String filter) {
        if(filter != null) {
            StorageData storageData = getCurrentStorage();
            if(BuildConfig.DEBUG) Logger.debug(TAG, "Filter:"+filter);
            mFilteredImageList = new FilteredImageList(storageData.getImageList());
            mFilteredImageList.setStorageData(storageData);
            mFilteredImageList.setFilter(filter);
        } else {
            if(BuildConfig.DEBUG) Logger.debug(TAG, "No Filter set");
            mFilteredImageList = null;
        }
    }
  
    public boolean hasFilter(FilteredImageList.ImageFilter filter) {
        return mFilteredImageList != null && mFilteredImageList.hasFilter(filter);
    }
  
    public int imageCount() {
        ImageList imageList = getImageList();
        if(imageList != null) {
            return imageList.length();
        }
        return 0;
    }

    public ImageMetaData getImageInfo(ImageData imageData) {
        return mController.getImageInfo(imageData);
    }

    public CameraController getController() {
        return mController;
    }

    public void addImageToStorage(String storage, String filepath) {
        if(mCameraData != null) {
            for (StorageData storageData : mCameraData.storages) {
                if(storageData.name.equals(storage)) {
                    File file = new File(filepath);
                    String dirName = file.getParent();
                    String fileName = file.getName();
                    storageData.getImageList().insertImage(dirName, fileName);
                    break;
                }
            }
        }
    }
}