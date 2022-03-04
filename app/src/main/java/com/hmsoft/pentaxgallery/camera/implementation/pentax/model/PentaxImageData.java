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

import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.camera.implementation.pentax.UrlHelper;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.util.Logger;

import java.io.File;
import java.io.IOException;

import androidx.exifinterface.media.ExifInterface;

public class PentaxImageData extends ImageData {

    PentaxImageData(String directory, String fileName) {
        super(directory, fileName);
    }
  
    @Override
    public ImageMetaData readMetadata() {
        if(existsOnLocalStorage()) {
            ExifInterface exifInterface = null;
            try {
                ParcelFileDescriptor fd = MyApplication.ApplicationContext.getContentResolver().openFileDescriptor(getLocalStorageUri(), "r");
                exifInterface = new ExifInterface(fd.getFileDescriptor());
                setMetaData(new PentaxImageMetaData(exifInterface));
                fd.close();
                if(BuildConfig.DEBUG) Logger.debug(fileName, "Image metadata loaded from downloaded picture");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return getMetaData();
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
}
