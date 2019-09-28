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


import android.database.Cursor;
import android.graphics.Bitmap;
import android.provider.MediaStore;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.util.Logger;

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

    private static final String orderByMediaStoreCursor = MediaStore.Images.Media.DATE_TAKEN  + " DESC";
    private static final String[] projectionMediaStoreCursor = new String[] {
            MediaStore.Images.Media._ID,
    };

    public ImageData(String directory, String fileName) {
        this.directory = directory;
        this.fileName = fileName;
        this.fullPath = directory + "/" + fileName;
        this.uniqueFileName = directory + "-" + fileName;
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

    public boolean existsOnLocalStorage() {
        if(mExistsOnLocalStorage == null) {
            updateExistsOnLocasStorage();
        }
        return mExistsOnLocalStorage.booleanValue();
    }

    public void updateExistsOnLocasStorage() {
        File localPath = getLocalPath();
        mExistsOnLocalStorage  = localPath != null && localPath.exists() && localPath.isFile();

        /*
        // TODO: Use this approach when targeting most recent Android API levels.
        Cursor cursor = getMediaStoreCursor();
        mExistsOnLocalStorage  = cursor != null && cursor.getCount() > 0;
        cursor.close();
         */
        if(BuildConfig.DEBUG) Logger.debug(TAG, "mExistsOnLocalStorage: " + uniqueFileName + ": " +mExistsOnLocalStorage);
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

    public Cursor getMediaStoreCursor() {
        Cursor cursor = MediaStore.Images.Media.query(
                MyApplication.ApplicationContext.getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projectionMediaStoreCursor,
                MediaStore.Images.Media.DISPLAY_NAME + " = '" + this.uniqueFileName + "'",
                orderByMediaStoreCursor);

        return cursor;
    }
}
