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

import java.util.LinkedList;
import java.util.List;

public class CameraData extends BaseResponse {

    public final String model;
    public final String firmwareVersion;
    public final String macAddress;
    public final String serialNo;

    public final String key;
    public final String ssid;

    public final List<StorageData> storages = new LinkedList<>();

    public CameraData(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public CameraData(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public CameraData(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
        model = jsonObject.optString("model");
        firmwareVersion = jsonObject.optString("firmwareVersion");
        macAddress = jsonObject.optString("macAddress");
        serialNo = jsonObject.optString("serialNo");

        key = jsonObject.optString("key");
        ssid = jsonObject.optString("ssid");

        JSONArray storajesArray = jsonObject.optJSONArray("storages");
        if (storajesArray != null) {
            int len = storajesArray.length();
            for (int c = 0; c < len; c++) {
                JSONObject storageJsonObject = storajesArray.getJSONObject(c);
                StorageData storageData = new StorageData(storageJsonObject);
                if(storageData.active && storageData.available) {
                    storages.add(storageData);
                }
            }
        }

        if(storages.size() == 0) {
            storages.add(StorageData.DefaultStorage);
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return model;
    }
}
