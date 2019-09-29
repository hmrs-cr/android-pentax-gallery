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
import android.text.format.DateUtils;

import com.hmsoft.pentaxgallery.BuildConfig;

import java.io.File;

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

    public final String flaggedCacheKey;

    private boolean mIsDownloadQueue;
    private boolean mIsFlagged;
    private Bitmap mThumbBitmap;


    private static final long uid = (System.currentTimeMillis() - 416136900000L)  / (DateUtils.WEEK_IN_MILLIS * 4);

    public ImageData(String directory, String fileName) {
        this.directory = directory;
        this.fileName = fileName;
        this.fullPath = directory + "/" + fileName;
        this.uniqueFileName = uid + "-" + directory + "-" + fileName;
        this.flaggedCacheKey = uniqueFileName.substring(0, uniqueFileName.lastIndexOf('.')) + ".flagged";
        this.isRaw = !fileName.toLowerCase().endsWith(".jpg");
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
}
