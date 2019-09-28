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

package com.hmsoft.pentaxgallery.camera.implementation.pentax.model;

import android.database.Cursor;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.MediaStore;

import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.camera.implementation.pentax.UrlHelper;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.util.DefaultSettings;

import java.io.File;
import java.io.IOException;

public class PentaxImageData extends ImageData {

    private File mLocalPath;
    private Uri mLocalUri = null;

    public PentaxImageData(String directory, String fileName) {
        super(directory, fileName);
        updateExistsOnLocasStorage();
    }
  
    @Override
    public ImageMetaData readMetadata() {
        if(existsOnLocalStorage()) {
            ExifInterface exifInterface = null;
            try {
                exifInterface = new ExifInterface(getLocalPath().getAbsolutePath());
                setMetaData(new PentaxImageMetaData(getLocalPath(), exifInterface));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return getMetaData();
    }

    private static final String orderByMediaStoreCursor = MediaStore.Images.Media.DATE_TAKEN  + " DESC";
    private static final String[] projectionMediaStoreCursor = new String[] {
            MediaStore.Images.Media._ID,
    };

    private Cursor getMediaStoreCursor() {
        Cursor cursor = MediaStore.Images.Media.query(
                MyApplication.ApplicationContext.getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projectionMediaStoreCursor,
                MediaStore.Images.Media.DISPLAY_NAME + " = '" + this.uniqueFileName + "'",
                orderByMediaStoreCursor);

        return cursor;
    }

    @Override
    public Uri getLocalStorageUri() {
        if(mLocalUri == null) {
            Cursor cursor = getMediaStoreCursor();
            if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                String id = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media._ID));
                mLocalUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            }
            if(cursor != null) {
                cursor.close();
            }
        }
        return mLocalUri;
    }

    @Override
    public String getViewUrl() {
        String viewUrl = UrlHelper.getViewUrl(this);
        return viewUrl;
    }

    @Override
    public String getThumbUrl() {
        String thumbUrl = UrlHelper.getThumbUrl(this);
        return thumbUrl;
    }

    @Override
    public String getDownloadUrl() {
        String downloadUrl = UrlHelper.getDownloadUrl(this);
        return downloadUrl;
    }

    @Override
    public File getLocalPath() {
        if(mLocalPath == null) {
            String location = DefaultSettings.getsInstance().getStringValue(DefaultSettings.DOWNLOAD_LOCATION);

            if (isRaw) {
                location += " (RAW)";
            }

            mLocalPath = new File(location, uniqueFileName);
        }
        return mLocalPath;
    }
}
