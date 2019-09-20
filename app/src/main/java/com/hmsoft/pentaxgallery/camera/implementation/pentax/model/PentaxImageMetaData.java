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


import android.media.ExifInterface;

import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;

import java.io.File;

public class PentaxImageMetaData extends ImageMetaData {


    public static final String TAG_SHUTTER_SPEED_VALUE = "ShutterSpeedValue";

    private ExifInterface mExifInterface = null;

    public ExifInterface getExifInterface() {
        return mExifInterface;
    }
  
    public PentaxImageMetaData(File file, ExifInterface exifInterface) {
        super(
            file.getParent(),
            file.getName(),
            true,
            exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0),
            "Pentax",
            exifInterface.getAttribute(ExifInterface.TAG_DATETIME),
            exifInterface.getAttribute(ExifInterface.TAG_APERTURE),
            exifInterface.getAttribute(ExifInterface.TAG_ISO),
            "xv",
            exifInterface.getAttribute(TAG_SHUTTER_SPEED_VALUE),
            "" /*latlng*/
        );

        mExifInterface = exifInterface;
    }            
}
