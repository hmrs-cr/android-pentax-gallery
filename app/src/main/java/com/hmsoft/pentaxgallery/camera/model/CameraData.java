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

import android.text.format.DateFormat;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class CameraData extends BaseResponse {

    public final String manufacturer;
    public final String model;
    public final String firmwareVersion;
    public final String macAddress;
    public final String serialNo;
    public final String dateAdded;

    public final String key;
    public final String ssid;

    public final List<StorageData> storages = new LinkedList<>();

    public CameraData(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public CameraData(String ssid, String key) {
        super(200, "");

        manufacturer = null;
        model = null;
        firmwareVersion = null;
        macAddress = null;
        serialNo = null;
        dateAdded = null;

        this.key = key;
        this.ssid = ssid;
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
        manufacturer = jsonObject.optString("manufacturer");
        dateAdded = jsonObject.optString("dataAdded");

        key = jsonObject.optString("key");
        ssid = jsonObject.optString("ssid");

        JSONArray storajesArray = jsonObject.optJSONArray("storages");
        if (storajesArray != null) {
            int len = storajesArray.length();
            for (int c = 0; c < len; c++) {
                JSONObject storageJsonObject = storajesArray.getJSONObject(c);
                StorageData storageData = new StorageData(storageJsonObject);
                if(storageData.available) {
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



    private static File getStorageDirectory() {
        File storageDirectory;

        if((storageDirectory = MyApplication.ApplicationContext.getExternalFilesDir("cameras")) == null) {
            storageDirectory = MyApplication.ApplicationContext.getFilesDir();
        }

        storageDirectory.mkdirs();

        return storageDirectory;
    }

    public void saveData() {
        File file = new File(getStorageDirectory(), macAddress.replace(":", "") + "." + serialNo);
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("manufacturer", manufacturer);
            jsonObject.put("model", model);
            jsonObject.put("serialNo", serialNo);
            jsonObject.put("key", key);
            jsonObject.put("ssid", ssid);
            jsonObject.put("macAddress", macAddress);
            jsonObject.put("dataAdded", DateFormat.format("yyyyMMddHHmmss", new Date()));

            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(new FileOutputStream(file));
            outputStreamWriter.write(BuildConfig.DEBUG ? jsonObject.toString(4) : jsonObject.toString());
            outputStreamWriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static CameraData readFromFile(File file) {
        FileInputStream fi = null;
        try {
            fi = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fi));
            StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();

            try {
                return new CameraData(sb.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<CameraData> getRegisteredCameras() {
        File folder = getStorageDirectory();
        String[] files = folder.list();
        List<CameraData> result = new ArrayList<>(files.length);

        for(String file : files) {
            CameraData cameraData = readFromFile(new File(folder, file));
            if(cameraData != null) {
                result.add(cameraData);
            }
        }

        Logger.debug("Sorting", "" + result.size());
        Collections.sort(result, new Comparator<CameraData>() {
            @Override
            public int compare(CameraData o1, CameraData o2) {
                if(o1 == null || o1.dateAdded == null) {
                    return 0;
                }
                if(o2 == null || o2.dateAdded == null) {
                    return 0;
                }
                int r = o2.dateAdded.compareTo(o1.dateAdded);


                Logger.debug("Sorting", o1.dateAdded + " " + o2.dateAdded + " = " + r);
                return  r;
            }
        });

        return result;
    }
}
