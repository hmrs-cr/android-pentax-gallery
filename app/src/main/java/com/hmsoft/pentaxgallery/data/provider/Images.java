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

package com.hmsoft.pentaxgallery.data.provider;

import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.FilteredImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.StorageData;

public class Images {

    private static CameraData sCameraData;
    private static int sCurrentStorageIndex;

    private static boolean sShowDownloadQueueOnly = false;
    private static FilteredImageList sFilteredImageList = null;

    public static void setFilter(String filter) {
        if(filter != null && filter.length() > 0) {
            sFilteredImageList = new FilteredImageList(getCurrentStorage().getImageList(), filter);
        } else {
            sFilteredImageList = null;
        }
    }

    public static boolean isFiltered() {
        return sFilteredImageList != null;
    }

    public synchronized static ImageList getImageList() {
        if(sShowDownloadQueueOnly) {
            return DownloadQueue.getImageList();
        }
        if(sFilteredImageList != null) {
            return sFilteredImageList;
        }
        StorageData storageData = getCurrentStorage();
        return storageData != null ? storageData.getImageList() : null;
    }

    public synchronized static void setImageList(ImageList list) {
        StorageData storageData = getCurrentStorage();
        storageData.setImageList(list);
        if(list != null) {
            list.setStorageData(storageData);
        }
    }

    public synchronized static CameraData getCameraData() {
        return sCameraData;
    }

    public synchronized static void setCameraData(CameraData cameraData) {
        sCameraData = cameraData;
    }

    public synchronized static int imageCount() {
        ImageList imageList = getImageList();
        if(imageList != null) {
            return imageList.length();
        }
        return 0;
    }

    public static boolean isShowDownloadQueueOnly() {
        return sShowDownloadQueueOnly;
    }

    public static void setShowDownloadQueueOnly(boolean showDownloadQueueOnly) {
        sShowDownloadQueueOnly = showDownloadQueueOnly;
    }

    public static StorageData getCurrentStorage() {
        return sCameraData != null ? sCameraData.storages.get(sCurrentStorageIndex) : StorageData.DefaultStorage;
    }

    public static int getCurrentStorageIndex() {
        return sCurrentStorageIndex;
    }

    public static void setCurrentStorageIndex(int currentStorageIndex) {
        sCurrentStorageIndex = currentStorageIndex;
        setFilter(null);
    }
}
