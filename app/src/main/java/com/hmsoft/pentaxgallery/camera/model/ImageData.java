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
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

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
    private int mGalleryId;
    private Bitmap mThumbBitmap;
  
    private JSONObject mJSONObject;

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
            File parentDir = mStorageData.getImageDataDirectory();
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
           JSONObject jsonObject = this.getJSONObject();
           Utils.saveTextFile(dataFile, BuildConfig.DEBUG ? jsonObject.toString(4) : jsonObject.toString());
       } catch (JSONException | IOException e) {
           e.printStackTrace();
       }
    }

    public void readData() {
        readData(getDataFile());
    }

    private void readData(File dataFile) {
        try {            
            if(dataFile.exists()) {
                String json = Utils.readTextFile(dataFile);
                mJSONObject = new JSONObject(json);

                mGalleryId = mJSONObject.optInt("galleryId", 0);
                mIsFlagged = mJSONObject.optBoolean("isFlagged", false);
                mIsDownloadQueue = mJSONObject.optBoolean("inDownloadQueue", false);
                JSONObject metadata = mJSONObject.optJSONObject("metadata");
                if(metadata != null) {
                    setMetaData(new ImageMetaData(metadata));
                    if(BuildConfig.DEBUG) Logger.debug(fileName, "Image metadata loaded from local file");
                }
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }
  
    public JSONObject getJSONObject() {
        if (mJSONObject == null) {
            mJSONObject = new JSONObject();
        }
        try {
            mJSONObject.put("isFlagged", Boolean.toString(mIsFlagged));
            mJSONObject.put("inDownloadQueue", Boolean.toString(mIsDownloadQueue));
            mJSONObject.put("galleryId", Integer.toString(mGalleryId));
            if (mMetaData != null) {
                mJSONObject.put("metadata", mMetaData.getJSONObject());
            }
            return mJSONObject;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getGalleryId() {
        return mGalleryId;
    }

    public void setGalleryId(int mGalleryId) {
        this.mGalleryId = mGalleryId;
    }
}
