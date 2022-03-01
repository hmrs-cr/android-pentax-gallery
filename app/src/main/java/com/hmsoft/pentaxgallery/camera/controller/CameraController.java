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

package com.hmsoft.pentaxgallery.camera.controller;

import android.location.Location;

import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraChange;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.CameraPreferences;
import com.hmsoft.pentaxgallery.camera.model.CameraParams;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;

import org.json.JSONException;

import java.util.Date;

public interface CameraController {

    interface OnAsyncCommandExecutedListener {
        void onAsyncCommandExecuted(BaseResponse response);
    }

    interface OnCameraDisconnectedListener {
        void onCameraDisconnected();
    }

    interface OnCameraChangeListener {
        void onCameraChange(CameraChange change);
    }

    interface OnLiveViewFrameReceivedListener {
        void onLiveViewFrameReceived(byte[] frameData);
    }

    class AsyncCommandExecutedListenerRunnable implements Runnable {

        private final OnAsyncCommandExecutedListener mOnAsyncCommandExecutedListener;
        private final  BaseResponse mResponse;

        public AsyncCommandExecutedListenerRunnable(OnAsyncCommandExecutedListener onAsyncCommandExecutedListener, BaseResponse response) {
            mOnAsyncCommandExecutedListener = onAsyncCommandExecutedListener;
            mResponse = response;
        }

        @Override
        public void run() {
            if(mOnAsyncCommandExecutedListener != null) {
                mOnAsyncCommandExecutedListener.onAsyncCommandExecuted(mResponse);
            }
        }
    }

    void setPreferences(CameraPreferences preferences);

    BaseResponse ping();
    void ping(final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);
  
    boolean connectToCamera();
    CameraData getDeviceInfo();

    ImageListData getImageList();
    ImageListData getImageList(StorageData storage);
    ImageListData createImageList(String json) throws JSONException;

    BaseResponse powerOff();
    void powerOff(final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);    

    ImageMetaData getImageInfo(ImageData imageData);
    void getImageInfo(final ImageData imageData, final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    void setOnCameraDisconnectedListener(OnCameraDisconnectedListener onCameraDisconnectedListener);
    void addCameraChangeListener(OnCameraChangeListener onCameraChangeListener);
    void removeCameraChangeListener(OnCameraChangeListener onCameraChangeListener);

    void startLiveView(OnLiveViewFrameReceivedListener onLiveViewFrameReceivedListener);
    void pauseLiveView();
    void stopLiveView();

    BaseResponse shoot();
    void shoot(OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    BaseResponse focus();
    void focus(OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    CameraParams getCameraParams();
    void getCameraParams(OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    BaseResponse enableGeoTagging(boolean enabled);
    void enableGeoTagging(boolean enabled, OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    BaseResponse updateGpsLocation(Location location);
    void updateGpsLocation(Location location, OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    BaseResponse updateDateTime(Date dateTime);
    void updateDateTime(Date dateTime, OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

}
