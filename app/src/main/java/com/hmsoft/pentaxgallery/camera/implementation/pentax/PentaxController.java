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
import com.hmsoft.pentaxgallery.camera.implementation.pentax.model.PentaxImageListData;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraChange;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.camera.util.HttpHelper;
import com.hmsoft.pentaxgallery.util.DefaultSettings;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

import org.json.JSONException;

import java.io.EOFException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class PentaxController implements CameraController {
    
    private static final int NORMAL_CLOSURE_STATUS = 1000;

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

    private WebSocket cameraWebSocket;
    private OnCameraChangeListener cameraChangeListener;
    private WebSocketListener webSocketListener =  new WebSocketListener() {
        private static final String TAG = "CameraWebSocketClient";

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if(Logger.DEBUG) Logger.debug(TAG, "onOpen: " + response);
        }

        @Override
        public void onMessage(WebSocket webSocket, final String response) {
            if(Logger.DEBUG) Logger.debug(TAG, "onMessage: " + response);
            if(cameraChangeListener != null) {
                TaskExecutor.executeOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            cameraChangeListener.onCameraChange(new CameraChange(response));
                        } catch (JSONException e) {
                            Logger.warning(TAG, "onMessage", e);
                        }
                    }
                });
            }
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            webSocket.close(NORMAL_CLOSURE_STATUS, null);
            if(Logger.DEBUG) Logger.debug(TAG, "onClosing: " + reason);
        }
      
        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            cameraWebSocket = null;
            if(Logger.DEBUG) Logger.debug(TAG, "onClosed: " + reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable error, Response response) {
          cameraWebSocket = null;
          webSocket.close(NORMAL_CLOSURE_STATUS, null);  
          if(error instanceof EOFException) {
                
            } else {
                Logger.warning(TAG, response != null ? response.toString() : "", error);
            }
        }
    };

    public boolean connectToCamera() {
        return HttpHelper.bindToWifi();
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
    
    public  CameraData getDeviceInfo() {
        CameraData cameraData = null;
        String response = getDeviceInfoJson();
        if(response != null) {            
            try {
                return new CameraData(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        
        return cameraData;
    }

    public ImageListData getImageList() {
        return getImageList(null);
    }

    public ImageListData getImageList(StorageData storage) {        
        
        String response = getImageListJson(storage);
        if(response != null) {
            try {
                return createImageList(response);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public ImageListData createImageList(String json) throws JSONException {
        return new PentaxImageListData(json);
    }

    public ImageMetaData getImageInfo(ImageData imageData) {
        synchronized (imageData) {
            if (imageData.getMetaData() == null) {
                try {
                    String response = getImageInfoJson(imageData);
                    if (response != null) {
                        imageData.setMetaData(new ImageMetaData(response));
                        imageData.saveData();
                        if(BuildConfig.DEBUG) Logger.debug(imageData.fileName, "Image metadata loaded from camera");
                    } else  {
                        imageData.setMetaData(imageData.readMetadata());
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return imageData.getMetaData();
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

    public BaseResponse powerOff() {
        String response = powerOffJson();
        try {
            return  response != null ? new BaseResponse(response) : null;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void powerOff(final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener) {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                BaseResponse response = powerOff();
                if(onAsyncCommandExecutedListener != null) {
                    TaskExecutor.executeOnUIThread(new CameraController.AsyncCommandExecutedListenerRunnable(onAsyncCommandExecutedListener, response));
                }

            }
        });
    }

    public void setCameraChangeListener(OnCameraChangeListener onCameraChangeListener) {
        if (this.cameraWebSocket != null) {
            this.cameraWebSocket.close(NORMAL_CLOSURE_STATUS, null);
            this.cameraWebSocket = null;
        }

        this.cameraChangeListener = onCameraChangeListener;
        if (onCameraChangeListener != null) {
            Request request = new Request.Builder().url(UrlHelper.URL_WEBSOCKET).build();

            OkHttpClient client = new OkHttpClient();
            this.cameraWebSocket = client.newWebSocket(request, this.webSocketListener);
            client.dispatcher().executorService().shutdown();
        }
    }

}
