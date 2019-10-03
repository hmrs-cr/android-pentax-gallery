/*
 * Copyright (C) 2018 Mauricio Rodriguez (ranametal@users.sf.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmsoft.pentaxgallery.camera.model;


import android.graphics.Bitmap;
import android.net.Uri;

import com.hmsoft.pentaxgallery.BuildConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public abstract class ImageData {

    private static final String TAG = "ImageData";

    public final String directory;
    public final String fileName;

    public final String fullPath;
    public final String uniqueFileName;
  
    public final boolean isRaw;
  
    protected ImageMetaData mMetaData;
    protected StorageData mStorageData;

    protected Boolean mExistsOnLocalStorage;

    protected final String dataKey;
    private File dataFile;
    private ImageList imageList;

    private boolean mIsDownloadQueue;
    private boolean mIsFlagged;
    private Bitmap mThumbBitmap;
  
    private Properties mProperties;

    public ImageData(String directory, String fileName) {
        this.directory = directory;
        this.fileName = fileName;
        this.fullPath = directory + "/" + fileName;
        this.uniqueFileName = directory + "-" + fileName;
        this.dataKey = uniqueFileName + ".data";
        this.isRaw = !fileName.toLowerCase().endsWith(".jpg");
        this.imageList = imageList;
    }

    public boolean match(String text) {
        if(text == null || fileName == null) {
            return false;
        }
        return fileName.contains(text);
    }

    @Override
    public String toString() {
        return fullPath;
    }

    public void setThumbBitmap(Bitmap bitmap) {
        mThumbBitmap = bitmap;
    }

    public Bitmap getThumbBitmap() {
        return mThumbBitmap;
    }

    public abstract String getViewUrl();

    public abstract String getThumbUrl();

    public abstract String getDownloadUrl();

    public abstract File getLocalPath();
  
    public abstract ImageMetaData readMetadata();

    public abstract Uri getLocalStorageUri();

    public abstract void setLocalStorageUri(Uri localUri);
        
    public boolean existsOnLocalStorage() {
        if(mExistsOnLocalStorage == null) {
            updateExistsOnLocalStorage();
        }
        return mExistsOnLocalStorage;
    }

    public void updateExistsOnLocalStorage() {
        mExistsOnLocalStorage  = getLocalStorageUri() != null;
    }

    public ImageMetaData getMetaData() {
      return mMetaData;
    }
  
    public void setMetaData(ImageMetaData metaData) {
        if(BuildConfig.DEBUG) {
            if (metaData != null && metaData.fileName != null && !metaData.fileName.contains(fileName)) {
                throw new RuntimeException(String.format("%s != %s", metaData.fileName, fileName));
            }
        }
       mMetaData = metaData;

    }

    public StorageData getStorageData() {
        return mStorageData;
    }

    public void setStorageData(StorageData storageData) {
        mStorageData = storageData;
    }

    public boolean isFlagged() {
        return mIsFlagged;
    }

    public void setIsFlagged(boolean mIsFlagged) {
        this.mIsFlagged = mIsFlagged;
    }

    public boolean inDownloadQueue() {
        return mIsDownloadQueue;
    }

    public void setIsInDownloadQueue(boolean isDownloadQueue) {
        this.mIsDownloadQueue = isDownloadQueue;
    }

    private File getDataFile() {
        if(dataFile == null) {
            CameraData cameraData = mStorageData.getCameraData();
            File parentDir = new File(cameraData.getStorageDirectory(), "Images" + File.separator +
                    mStorageData.name);
            parentDir.mkdirs();
            dataFile = new File(parentDir, this.dataKey);
        }
        return dataFile;
    }

    public void saveData() {
        saveData(getDataFile());
    }

    private void saveData(File dataFile) {
       try {           
           Properties properties = this.getProperties();           
           FileOutputStream outputStream = new FileOutputStream(dataFile);
           properties.store(outputStream, "");
           outputStream.close();
       } catch (IOException e) {
           e.printStackTrace();
       }
    }

    public void readData() {
        readData(getDataFile());
    }

    private void readData(File dataFile) {
        try {            
            if(dataFile.exists()) {
                if (mProperties == null) {
                    mProperties = new Properties();
                }
                FileInputStream inputStream = new FileInputStream(dataFile);
                mProperties.load(inputStream);

                mIsFlagged = Boolean.parseBoolean(mProperties.getProperty("IsFlagged"));
                mIsDownloadQueue = Boolean.parseBoolean(mProperties.getProperty("InDownloadQueue"));

                inputStream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
  
    public Properties getProperties() {
        if( mProperties == null) {
            mProperties = new Properties();
        }
        mProperties.setProperty("IsFlagged", Boolean.toString(mIsFlagged));
        mProperties.setProperty("InDownloadQueue", Boolean.toString(mIsDownloadQueue));
        if(mMetaData != null) {
            mProperties.setProperty("CameraModel", mMetaData.cameraModel);
            mProperties.setProperty("DateTime", mMetaData.dateTime);
        }
        if (mStorageData != null) {
            mProperties.setProperty("Storage", mStorageData.name);
        }
        return mProperties;
    }

}
