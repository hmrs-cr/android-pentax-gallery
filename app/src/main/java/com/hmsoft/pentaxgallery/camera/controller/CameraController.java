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

import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;

public interface CameraController {

    interface OnAsyncCommandExecutedListener {
        void onAsyncCommandExecuted(BaseResponse response);
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

    CameraData getDeviceInfo(boolean ignoreCache);

    ImageListData getImageList();
    ImageListData getImageList(StorageData storage, boolean ignoreCache);

    BaseResponse powerOff();
    void powerOff(final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);

    boolean connectToCamera();

    ImageMetaData getImageInfo(ImageData imageData);
    void getImageInfo(final ImageData imageData, final CameraController.OnAsyncCommandExecutedListener onAsyncCommandExecutedListener);


    CameraData getDefaultCameraData();


}
