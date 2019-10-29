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


package com.hmsoft.pentaxgallery.camera.implementation.pentax;

import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;

public final class UrlHelper {

    private UrlHelper() { }

    private final static String URL_BASE = "http://192.168.0.1/v1";
    protected final static String URL_LIVE_VIEW = URL_BASE + "/liveview";
    public static final String URL_FOCUS = URL_BASE + "/lens/focus";
    protected final static String URL_CAMERA_PARAMS = URL_BASE + "/params/camera";
    final static String URL_WEBSOCKET = "ws://192.168.0.1/v1/changes";
    private final static String URL_PHOTOS = URL_BASE + "/photos";
    private final static String URL_DOWNLOAD = URL_PHOTOS + "/";
    final static String URL_DEVICE_INFO = URL_BASE + "/props";
    final static String URL_POWEROFF = URL_BASE + "/device/finish";
    final static String URL_PING = URL_BASE + "/ping";
    final static String URL_SHOOT = URL_BASE + "/camera/shoot";
    private final static String STORAGE_PARAM = "storage=";

    private static String appendStorageParam(String url, StorageData storage) {
        if(!url.contains(STORAGE_PARAM) && storage != null && storage.name != null && !storage.name.equals("")) {
            url += url.contains("?") ? "&" : "?";
            url += STORAGE_PARAM + storage.name;
        }

        return url;
    }

    private static String getDownloadUrl(ImageData imageData, boolean noParams) {
        String url = URL_DOWNLOAD + imageData.fullPath;
        if(noParams) {
            return url;
        }
        return appendStorageParam(url, imageData.getStorageData());
    }

    public static String getDownloadUrl(ImageData imageData) {
        return  getDownloadUrl(imageData, false);
    }

    public static String getThumbUrl(ImageData imageData) {
        if(!imageData.isRaw) {
            return appendStorageParam(getDownloadUrl(imageData, true) + "?size=thumb", imageData.getStorageData());
        }
        return getViewUrl(imageData);
    }

    public static String getViewUrl(ImageData imageData) {
        return appendStorageParam(getDownloadUrl(imageData, true) + "?size=view", imageData.getStorageData());
    }

    public static String getInfoUrl(ImageData imageData) {
        return appendStorageParam(getDownloadUrl(imageData, true) + "/info", imageData.getStorageData());
    }

    public static String getImageListUrl(StorageData storage) {
        return appendStorageParam(UrlHelper.URL_PHOTOS, storage);
    }
}