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

import org.json.JSONObject;

import java.io.File;

public class StorageData {
    /*private*/ static final String FOLDER_IMAGES = "images";

    public final boolean active;
    public final boolean available;
    public final boolean equipped;
    public final String format;
    public final String name;
    public final int remain;
    public final String reservePriority;
    public final boolean writable;
    public final String displayName;

    private ImageList mImageList;
    private final CameraData mCameraData;

    public StorageData(CameraData cameraData, JSONObject jsonObject) {
        active = jsonObject.optBoolean("active");
        available = jsonObject.optBoolean("available");
        equipped = jsonObject.optBoolean("equipped");
        format = jsonObject.optString("format");
        name = jsonObject.optString("name");
        remain = jsonObject.optInt("remain");
        reservePriority = jsonObject.optString("reservePriority");
        writable = jsonObject.optBoolean("writable");

        displayName = String.format("%s (%s)", name, format).toUpperCase();

        mCameraData = cameraData;
    }

    public StorageData(CameraData cameraData) {
        String defaultVal = "";
        active = false;
        available = false;
        equipped = false;
        format = defaultVal;
        name = "sd1";
        remain = 0;
        reservePriority = defaultVal;
        writable = false;
        displayName = "";
        mCameraData = cameraData;
    }

    public CameraData getCameraData() {
        return mCameraData;
    }

    public ImageList getImageList() {
        return mImageList;
    }

    public void setImageList(ImageList imageList) {
        mImageList = imageList;
    }

    @Override
    public String toString() {
        String count = "-";
        if(mImageList != null) {
            count = String.valueOf(mImageList.length());
        }
        return String.format("%s (%s/%s)", name, count, format).toUpperCase();
    }

    public File getImageDataDirectory() {
        CameraData cameraData = getCameraData();
        return new File(cameraData.getStorageDirectory(), FOLDER_IMAGES + File.separator + name);
    }
}
