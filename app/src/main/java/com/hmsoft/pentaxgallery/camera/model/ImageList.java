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

import java.util.ArrayList;
import java.util.List;

public abstract class ImageList {

    protected final List<ImageData> mImageList = new ArrayList<>();
    private StorageData mStorage;

    public ImageList() {  }

    public ImageList(String jsonData) throws JSONException {
        this(new JSONObject(new JSONTokener(jsonData)));
    }

    public ImageList(JSONObject jsonObject) throws JSONException {
        this(jsonObject.getJSONArray("dirs"));
    }

    public ImageList(JSONArray jsonArray) throws JSONException {
        int len = jsonArray != null ? jsonArray.length() : 0;
        for(int c = len-1; c > -1; c--) {
            JSONObject jsonObject = jsonArray.getJSONObject(c);
            String dirName = jsonObject.getString("name");
            JSONArray fileArray = jsonObject.getJSONArray("files");
            int fileCount = fileArray.length();
            for(int i = fileCount - 1; i > -1; i--) {
                String fileName = fileArray.getString(i);
                addImage(dirName, fileName);
            }
        }
    }

    private boolean addImage(String dirName, String fileName) {
        ImageData imageData = createImageData(dirName, fileName);
        return mImageList.add(imageData);
    }

    public void insertImage(String dirName, String fileName) {
        ImageData imageData = createImageData(dirName, fileName);
        mImageList.add(0, imageData);
    }

    protected abstract ImageData createImageData(String dirName, String fileName);

    public int getFirstMatchIntex(String match) {
        for (int c = 0; c < mImageList.size(); c++) {
            ImageData imageData = getImage(c);
            if (imageData.match(match)) {
                return c;
            }
        }
        return -1;
    }

    public ImageData getImage(int index) {
        if(index < mImageList.size()) {
            ImageData imageData = mImageList.get(index);
            imageData.setStorageData(mStorage);
            return imageData;
        }
        return null;
    }

    public int length() {
        return mImageList.size();
    }

    public ImageData findByUniqueFileName(String fileName) {
        for (int c = 0; c < mImageList.size(); c++) {
            ImageData imageData = getImage(c);
            if (imageData.uniqueFileName.equals(fileName)) {
                return imageData;
            }
        }
        return null;
    }

    public String getFlaggedList() {
        return getFlaggedList(" ");
    }

    public String getFlaggedList(String separator) {
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < mImageList.size(); c++) {
            ImageData imageData = getImage(c);
            if (imageData.isFlagged()) {
                String fileName = imageData.fileName.substring(0, imageData.fileName.lastIndexOf('.'));
                sb.append(fileName).append(separator);
            }
        }
        return sb.toString();
    }

    public void setStorageData(StorageData storage) {
        mStorage = storage;
    }
}
