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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;

public abstract  class ImageListData extends BaseResponse {

    private File dataFile;
    public final ImageList dirList;

    public abstract ImageList createImageList(JSONArray jsonArray) throws JSONException;

    public ImageListData(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public ImageListData(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public ImageListData(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
        dirList =  createImageList(jsonObject.optJSONArray("dirs")); //PentaxImageList(jsonObject.optJSONArray("dirs"));
    }

    public static File getDataFile(StorageData storage) {
        CameraData cameraData = storage.getCameraData();
        File parentDir = new File(cameraData.getCameraFilesDirectory(), StorageData.FOLDER_IMAGES);
        parentDir.mkdirs();
        return new File(parentDir, storage.name + ".list");
    }

    private File getDataFile() {
        if(dataFile == null) {
            StorageData storage = dirList.getStorageData();
            dataFile = getDataFile(storage);
        }
        return dataFile;
    }
  
    public void saveData() {
        saveData(getDataFile());
    }
}
