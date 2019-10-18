package com.hmsoft.pentaxgallery.camera;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.implementation.pentax.PentaxController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.FilteredImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.Utils;
import com.hmsoft.pentaxgallery.util.WifiHelper;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.List;

import androidx.annotation.WorkerThread;

public class Camera {

    public static final Camera instance = new Camera(new PentaxController());

    private static final String TAG = "Camera";
  
    private final CameraController mController;

    private CameraData mLatestConnectedCamera;
    private boolean mCameraConnected;
    private CameraData mCameraData;
    private int mCurrentStorageIndex;
    private FilteredImageList mFilteredImageList = null;
    private List<CameraData> mCameras;

    public interface OnWifiConnectionAttemptListener {
        void onWifiConnectionAttempt(String ssid);
    }

    /*package*/ Camera(CameraController controller) {
        this.mController = controller;
    }

    @WorkerThread
    public CameraData connect() {
        return connect((String)null);
    }

    @WorkerThread
    public CameraData connect(String cameraId) {
        return connect(cameraId, null);
    }

    public CameraData connect(OnWifiConnectionAttemptListener listener) {
        return connect(null, listener);
    }

    @WorkerThread
    public CameraData connect(String cameraId, OnWifiConnectionAttemptListener listener) {
      CameraData cameraData = null;
      mCameraConnected = false;
      CameraData latestConnectedCamera = null;

      if(cameraId != null) {
          for(CameraData camera : getRegisteredCameras()) {
              if(camera.cameraId.equals(cameraId)) {
                  latestConnectedCamera = camera;
                  break;
              }
          }
      } else {
          int retryes = 3;
          int ci = 0;

          latestConnectedCamera = mLatestConnectedCamera;
          while (!mCameraConnected) {

              if (mController.connectToCamera()) {
                  BaseResponse response = mController.ping();
                  if (response != null && response.success) {
                      cameraData = mController.getDeviceInfo();
                  }
              } else {
                  Logger.warning(TAG, "Could not bind to WiFi");
              }

              if (cameraData == null) {

                  if (retryes-- == 0) {
                      Logger.warning(TAG, "Too many failed attempts to connect");
                      break;
                  }

                  List<CameraData> cameras = getRegisteredCameras();

                  if (cameras != null && cameras.size() > 0) {
                      if (latestConnectedCamera == null) {
                          latestConnectedCamera = cameras.get(0);
                      }

                      WifiHelper.turnWifiOn(MyApplication.ApplicationContext, 1000);

                      if (ci == cameras.size()) {
                          if(BuildConfig.DEBUG) Logger.debug(TAG, "All registered mCameras failed to connect");
                          break;
                      }

                      cameraData = cameras.get(ci++);

                      if (listener != null) {
                          listener.onWifiConnectionAttempt(cameraData.ssid);
                      }

                      if (ci == 0) {
                          WifiHelper.startWifiScan(MyApplication.ApplicationContext);
                          WifiHelper.waitForScanResultsAvailable(7500);
                      }

                      if (!WifiHelper.isWifiInRange(cameraData.ssid)) {
                          if (BuildConfig.DEBUG)
                              Logger.debug(TAG, cameraData.ssid + " not in range.");
                          cameraData = null;
                          continue;
                      }

                      if (BuildConfig.DEBUG)
                          Logger.debug(TAG, "Attempting to connect to " + cameraData.ssid);

                      boolean success = WifiHelper.connectToWifi(MyApplication.ApplicationContext,
                              cameraData.ssid, cameraData.key);
                      if (!success) {
                          Logger.warning(TAG, "Could not connect to " + cameraData.ssid);
                          cameraData = null;
                      } else {
                          Logger.warning(TAG, "Connected to " + cameraData.ssid);
                      }
                  } else {
                      Logger.warning(TAG, "No previously connected mCameras found.");
                      break;
                  }
              }

              mCameraConnected = cameraData != null;
          }
      }

      if(cameraData == null) {
          cameraData = latestConnectedCamera;
          if(BuildConfig.DEBUG) Logger.debug(TAG, "Camera data from cache: " + cameraData);
      }

      mLatestConnectedCamera = cameraData;
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

    public List<CameraData> getRegisteredCameras() {
        if (mCameras == null && !TaskExecutor.isMainUIThread()) {
            mCameras = CameraData.getRegisteredCameras();
        }
        return mCameras;
    }

    @WorkerThread
    public ImageList loadImageList() {
        ImageListData imageListResponse = mCameraConnected    ?
                mController.getImageList(getCurrentStorage()) :
                null;
      
        if(imageListResponse != null) {
            setImageList(imageListResponse.dirList);
            imageListResponse.saveData();
            return imageListResponse.dirList;
        }
              
        imageListResponse = mCameraData != null ? createImageListResponseFromFile() : null;
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
        CameraData cameraData = mCameraData;
        if (cameraData == null) {
            cameraData = CameraData.DefaultCameraData;
        }


        if (mCurrentStorageIndex < 0 || (mCurrentStorageIndex >= cameraData.storages.size())) {
            mCurrentStorageIndex = 0;
        }
        return cameraData.storages.get(mCurrentStorageIndex);
    }

    public ImageList getImageList() {        

        if(isFiltered()) {
            return mFilteredImageList.getList();
        }

        StorageData storageData = getCurrentStorage();
        return storageData != null ? storageData.getImageList() : null;
    }

    public boolean imageListHasMixedFormats() {
        StorageData storageData = getCurrentStorage();
        return storageData != null && storageData.getImageList() != null &&
                storageData.getImageList().hasMixedFormats;
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

    public ImageData addImageToStorage(String storage, String filepath) {
        ImageData imageData = null;
        if(mCameraData != null) {
            for (StorageData storageData : mCameraData.storages) {
                if(storageData.name.equals(storage)) {
                    File file = new File(filepath);
                    String dirName = file.getParent();
                    String fileName = file.getName();
                    imageData = storageData.getImageList().insertImage(dirName, fileName);
                    imageData.setStorageData(storageData);
                    break;
                }
            }
            if(imageData != null && imageData.getStorageData().equals(getCurrentStorage())) {
                if((imageData.isRaw && hasFilter(FilteredImageList.RawFilter)) ||
                        (!imageData.isRaw && hasFilter(FilteredImageList.JpgFilter))) {
                    rebuildFilter();
                }
            }
        }
        return imageData;
    }

    public void rebuildFilter() {
        ImageList imageList = getImageList();
        if(imageList instanceof FilteredImageList) {
            ((FilteredImageList)imageList).rebuildFilter();
        }
    }

    public void powerOff() {
        mController.powerOff(null);
    }
  
    private ImageListData createImageListResponseFromFile() {
        File file = ImageListData.getDataFile(getCurrentStorage());
        String json = null;
        try {
            json = Utils.readTextFile(file);
            return mController.createImageList(json);
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
}