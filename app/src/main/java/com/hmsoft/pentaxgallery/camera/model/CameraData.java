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

import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.util.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class CameraData extends BaseResponse {

    public static final CameraData DefaultCameraData = new CameraData();
    private static final String FOLDER_CAMERAS = "cameras";
    private static final String FILE_NAME_CAMERA_DATA = "camera.data";

    public final String manufacturer;
    public final String model;
    public final String firmwareVersion;
    public final String macAddress;
    public final String serialNo;
    public final String dateAdded;
    public final String cameraId;
    public final int battery;
    public final boolean hot;
    public final boolean geoTagging;
    public final String gpsInfo;

    public final String key;
    public final String ssid;
    public final CameraPreferences preferences;

    public final int hashCode;

    public final List<StorageData> storages = new LinkedList<>();
    private File storageDirectory;

    public CameraData(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    private CameraData() {
        super(200, "");

        manufacturer = "";
        model = "";
        firmwareVersion = "";
        macAddress = "Default";
        serialNo = "Default";
        dateAdded = null;

        cameraId = "default.camera";

        geoTagging = false;
        gpsInfo = "";

        this.key = null;
        this.ssid = null;

        hashCode = 0;
        battery = -1;
        hot = false;

        storages.add(new StorageData(this));

        preferences = CameraPreferences.Default;
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

        cameraId = macAddress.replace(":", "") + "." + serialNo;
        hashCode = cameraId.hashCode();

        key = jsonObject.optString("key");
        ssid = jsonObject.optString("ssid");

        battery = jsonObject.optInt("battery", -1);
        hot = jsonObject.optBoolean("hot");
        geoTagging = "on".equals(jsonObject.optString("geoTagging"));
        gpsInfo = jsonObject.optString("gpsInfo");

        preferences = new CameraPreferences(this);
        preferences.load();

        JSONArray storajesArray = jsonObject.optJSONArray("storages");
        if (storajesArray != null) {
            int len = storajesArray.length();
            for (int c = 0; c < len; c++) {
                JSONObject storageJsonObject = storajesArray.getJSONObject(c);
                StorageData storageData = new StorageData(this, storageJsonObject);
                if(storageData.available) {
                    storages.add(storageData);
                }
            }
        }

        if(storages.size() == 0) {
            storages.add(new StorageData(this));
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return model;
    }

    private static File getParentStorageDirectory() {
        return new File(MyApplication.ApplicationContext.getFilesDir(), FOLDER_CAMERAS);
    }

    public File getStorageDirectory() {
        if(storageDirectory == null) {
            storageDirectory = new File(getParentStorageDirectory(),cameraId);
            storageDirectory.mkdirs();
        }
        return storageDirectory;
    }

    public void saveData() {
        if (mJSONObject == null) {
            mJSONObject = new JSONObject();
        }
        try {
            mJSONObject.put("dataAdded", DateFormat.format("yyyyMMddHHmmss", new Date()));
            saveData(new File(getStorageDirectory(), FILE_NAME_CAMERA_DATA));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private static CameraData createFromFile(File file) {
        try {
            String json = Utils.readTextFile(file);
            try {
                return new CameraData(json);
            } catch (JSONException e) {
                e.printStackTrace();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static List<CameraData> getRegisteredCameras() {
        File folder = getParentStorageDirectory();
        if(!folder.exists()) {
            return new ArrayList<>(0);
        }

        File[] files = folder.listFiles();
        List<CameraData> result = new ArrayList<>(files.length);

        for(File cameraFolder : files) {
            if (cameraFolder.isDirectory()) {
                File cameraDataFile = new File(cameraFolder, FILE_NAME_CAMERA_DATA);
                if(cameraDataFile.exists() && cameraDataFile.isFile()) {
                    CameraData cameraData = createFromFile(cameraDataFile);
                    if (cameraData != null) {
                        result.add(cameraData);
                    }
                }
            }
        }

        Collections.sort(result, new Comparator<CameraData>() {
            @Override
            public int compare(CameraData o1, CameraData o2) {
                if(o1 == null || o1.dateAdded == null) {
                    return 0;
                }
                if(o2 == null || o2.dateAdded == null) {
                    return 0;
                }
                return o2.dateAdded.compareTo(o1.dateAdded);
            }
        });

        return result;
    }

    public String[] getParamList(String paramName) {
        JSONArray jsonArray = getJSONObject().optJSONArray(paramName + "List");

        String[] result = null;
        if(jsonArray != null) {
            result = new String[jsonArray.length()];
            for(int c = 0; c < result.length; c++) {
                result[c] = jsonArray.optString(c);
            }
        }

        return result;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
