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

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.camera.util.HttpHelper;
import com.hmsoft.pentaxgallery.util.DefaultSettings;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.cache.CacheUtils;

import org.json.JSONException;

import java.net.HttpURLConnection;

public class PentaxController implements CameraController {

    private final static String METADATA_CACHE_KEY = ".metadata";
    private final static String IMAGELIST_CACHE_KEY = "image_list_";

    private final int connectTimeOut;
    private final int readTimeOut;

    public PentaxController() {
        DefaultSettings settings = DefaultSettings.getsInstance();
        connectTimeOut = settings.getIntValue(DefaultSettings.DEFAULT_CONNECT_TIME_OUT);
        readTimeOut = settings.getIntValue(DefaultSettings.DEFAULT_READ_TIME_OUT);
    }

    protected String getDeviceInfoJson() {
        return HttpHelper.getStringResponse(UrlHelper.URL_DEVICE_INFO, connectTimeOut, readTimeOut);
    }

    protected String getImageListJson(StorageData storage) {
        return HttpHelper.getStringResponse(UrlHelper.getImageListUrl(storage), connectTimeOut, readTimeOut);
    }

    protected String getImageInfoJson(ImageData imageData) {
        return HttpHelper.getStringResponse(UrlHelper.getInfoUrl(imageData), connectTimeOut,  readTimeOut);
    }

    protected String powerOffJson() {
        return HttpHelper.getStringResponse(UrlHelper.URL_POWEROFF, HttpHelper.RequestMethod.POST);
    }

    protected String pingJson() {
        return HttpHelper.getStringResponse(UrlHelper.URL_PING, HttpHelper.RequestMethod.GET);
    }


    public  CameraData getDeviceInfo(boolean ignoreCache) {


        String cacheKey = "camera.data";

        String cachedResponse = null;
        String response = ignoreCache || (cachedResponse = CacheUtils.getString(cacheKey)) == null
                ? getDeviceInfoJson() : cachedResponse;

        CameraData cameraData = null;

        if(response != null) {
            if(response != cachedResponse) {
                CacheUtils.saveString(cacheKey, response);
            }
            try {
                return new CameraData(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        return cameraData;
    }

    public ImageListData getImageList() {
        return getImageList(StorageData.DefaultStorage, false);
    }

    public ImageListData getImageList(StorageData storage, boolean ignoreCache) {

        String cacheKey = IMAGELIST_CACHE_KEY + storage.name;

        String cachedResponse = null;
        String response = ignoreCache || (cachedResponse = CacheUtils.getString(cacheKey)) == null
                ? getImageListJson(storage) : cachedResponse;

        if(response != null) {
            if(response != cachedResponse) {
                CacheUtils.saveString(cacheKey, response);
            }
            try {
                return new ImageListData(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public BaseResponse powerOff() {
        String response = powerOffJson();
        try {
            return  response != null ? new BaseResponse(response) : null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Not working on K-1
    public void powerOff(final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener) {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                BaseResponse response = powerOff();
                TaskExecutor.executeOnUIThread(new CameraController.AsyncCommandExecutedListenerRunnable(onAsyncCommandExecutedListener, response));

            }
        });
    }

    public BaseResponse ping() {
        String response = pingJson();
        try {
            return response != null ? new BaseResponse(response) : null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void ping(final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener) {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                BaseResponse response = ping();
                TaskExecutor.executeOnUIThread(new CameraController.AsyncCommandExecutedListenerRunnable(onAsyncCommandExecutedListener, response));

            }
        });
    }

    public boolean connectToCamera() {
        return HttpHelper.bindToWifi();
    }

    public ImageMetaData getImageInfo(ImageData imageData) {        
        ImageMetaData imageMetaData;
        synchronized (imageData) {
            imageData.readMetadata();
            imageMetaData = imageData.getMetaData();
            if (imageMetaData == null) {
                try {
                    String cacheKey = imageData.directory + "_" + imageData.fileName + METADATA_CACHE_KEY;
                    String response = CacheUtils.getString(cacheKey);
                    if (response != null) {
                        imageMetaData = new ImageMetaData(response);
                        if(BuildConfig.DEBUG) Logger.debug(imageData.fileName, "Image metadata loaded from disk cache");
                    } else {
                        response = getImageInfoJson(imageData);
                        if (response != null) {
                            imageMetaData = new ImageMetaData(response);
                            if(BuildConfig.DEBUG) Logger.debug(imageData.fileName, "Image metadata loaded from camera");
                            if (imageMetaData.errCode == HttpURLConnection.HTTP_OK) {
                                CacheUtils.saveString(cacheKey, response);
                            }
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                imageData.setMetaData(imageMetaData);
            }
        }
        return imageMetaData;
    }

    public void getImageInfo(final ImageData imageData, final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener) {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                ImageMetaData imageMetaData = getImageInfo(imageData);
                TaskExecutor.executeOnUIThread(new CameraController.AsyncCommandExecutedListenerRunnable(onAsyncCommandExecutedListener, imageMetaData));
            }
        });
    }

}
