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

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.data.model.DownloadEntry;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.util.cache.CacheUtils;
import com.hmsoft.pentaxgallery.MyApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadQueue {

    private static final String CACHE_KEY = "downloadQueue.cache";

    private static List<DownloadEntry> sDownloadQueue;
    private static DownloadFinishedReceiver sDownloadFinishedReceiver = null;
    private final static ImageList sIageList = new DownloadQueueImageList();

    private static OnDowloadFinishedListener onDowloadFinishedListener;

    public interface OnDowloadFinishedListener {
        void onDownloadFinished(ImageData imageData, long donloadId, int remainingDownloads, boolean wasCanceled);
    }

    public static void loadFromCache(ImageList sourceImageList, boolean forceLoad) {
        if(sourceImageList == null || sourceImageList.length() == 0) {
            return;
        }

        if(sDownloadQueue == null || forceLoad) {
            String cacheValue = CacheUtils.getString(CACHE_KEY);
            if(cacheValue == null) {
                return;
            }

            sDownloadQueue = new ArrayList<DownloadEntry>();
            DownloadManager downloadManager = (DownloadManager) MyApplication.ApplicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
            try {
                JSONArray jsonArray = new JSONArray(cacheValue);
                for(int c = 0; c < jsonArray.length(); c++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(c);
                    String fileName = jsonObject.getString(DownloadEntry.UNIQUE_FILE_NAME);
                    ImageData imageData = sourceImageList.findByUniqueFileName(fileName);
                    if(imageData != null) {
                        long downloadId = jsonObject.getLong(DownloadEntry.DOWNLOAD_ID);
                        if(downloadId != 0) {
                            Cursor cursor = downloadManager.query(new DownloadManager.Query().setFilterById(downloadId));
                            if(cursor != null && cursor.getCount() > 0 && cursor.moveToFirst()) {
                                int status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
                                if(status == DownloadManager.STATUS_SUCCESSFUL) {
                                    downloadId = 0;
                                }
                            } else {
                                downloadId = 0;
                            }
                        }

                        DownloadEntry downloadEntry = new DownloadEntry(imageData);
                        downloadEntry.setDownloadId(downloadId);
                        sDownloadQueue.add(downloadEntry);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveToCache() {
        JSONArray jsonArray = new JSONArray();
        if(sDownloadQueue != null) {
            for (DownloadEntry downloadEntry : sDownloadQueue) {
                JSONObject jsonObject = downloadEntry.toJSONbject();
                if(jsonObject != null) {
                    jsonArray.put(jsonObject);
                }
            }
        }
        CacheUtils.saveString(CACHE_KEY, jsonArray.toString());
    }

    public static ImageList getDownloadedFilesImageList() {
         File localDownloadsPath = MyApplication.ApplicationContext.getLocalDownloadsPath();
         File[] files = localDownloadsPath.listFiles();
         for (File file : files) {

         }
        return null;
    }

    public static void setOnDowloadFinishedListener(OnDowloadFinishedListener onDowloadFinishedListener) {
        DownloadQueue.onDowloadFinishedListener = onDowloadFinishedListener;
    }

    private static void doDowloadFinished(ImageData imageData, long donloadId, boolean wasCanceled) {
        if(onDowloadFinishedListener != null) {
            onDowloadFinishedListener.onDownloadFinished(imageData, donloadId, sDownloadQueue.size(), wasCanceled);
        }
    }

    public static DownloadEntry getNextDownload() {
        return findDownloadEntry(0);
    }

    public static DownloadEntry findDownloadEntry(ImageData imageData) {
        if (sDownloadQueue == null) {
            return null;
        }

        for (DownloadEntry downloadEntry : sDownloadQueue) {
            if (downloadEntry.getImageData() == imageData) {
                return downloadEntry;
            }
        }

        return null;
    }

    public static DownloadEntry findDownloadEntry(long downloadId) {
        if (sDownloadQueue == null) {
            return null;
        }

        for (DownloadEntry downloadEntry : sDownloadQueue) {
            if (downloadEntry.getDownloadId() == downloadId) {
                return downloadEntry;
            }
        }

        return null;
    }

    public static boolean isDownloading() {
        if (sDownloadQueue == null) {
            return false;
        }

        for (DownloadEntry downloadEntry : sDownloadQueue) {
            if (downloadEntry.getDownloadId() > 0) {
                return true;
            }
        }

        return false;
    }

    public static DownloadEntry addDownloadQueue(ImageData imageData) {
        if (sDownloadQueue == null) {
            sDownloadQueue = new ArrayList<DownloadEntry>();
        }

        if (!isInDownloadQueue(imageData)) {
            DownloadEntry downloadEntry = new DownloadEntry(imageData);
            sDownloadQueue.add(downloadEntry);
            processDownloadQueue();
            return downloadEntry;
        }
        return null;
    }

    public static void removeFromDownloadQueue(ImageData imageData) {

        DownloadEntry downloadEntry = findDownloadEntry(imageData);
        if (downloadEntry != null) {
            long downloadId = downloadEntry.getDownloadId();
            if (downloadId > 0) {
                DownloadManager downloadManager = (DownloadManager) MyApplication.ApplicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
                downloadManager.remove(downloadId);
            }
            remove(downloadEntry, true);
        }
    }

    public static boolean isInDownloadQueue(ImageData imageData) {
        return findDownloadEntry(imageData) != null;
    }

    public static void processDownloadQueue() {
        if (!isDownloading()) {
            DownloadEntry downloadEntry = getNextDownload();
            if (downloadEntry != null) {
                download(downloadEntry);
            }
        }
    }

    public static void download(ImageData imageData) {
        DownloadEntry downloadEntry = findDownloadEntry(imageData);
        if (downloadEntry != null) {
            download(downloadEntry);
        }
    }

    private static void download(DownloadEntry downloadEntry) {

        ImageData imageData = downloadEntry.getImageData();
        String url = imageData.getDownloadUrl();

        Context context = MyApplication.ApplicationContext;

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setDescription(context.getString(R.string.download_title))
                .setTitle(imageData.fileName)
                .setAllowedOverMetered(false)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setDestinationUri(Uri.fromFile(imageData.getLocalPath()));

        if (sDownloadFinishedReceiver == null) {
            sDownloadFinishedReceiver = new DownloadFinishedReceiver();
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            MyApplication.ApplicationContext.registerReceiver(sDownloadFinishedReceiver, filter);
        }

        long id = downloadManager.enqueue(request);
        downloadEntry.setDownloadId(id);
    }

    private static void remove(DownloadEntry downloadEntry, boolean canceled) {
        sDownloadQueue.remove(downloadEntry);
        doDowloadFinished(downloadEntry.mImageData, downloadEntry.getDownloadId(), canceled);
    }

    public static ImageList getImageList() {
        return sIageList;
    }

    private static class DownloadFinishedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            DownloadEntry downloadEntry = DownloadQueue.findDownloadEntry(id);
            if (downloadEntry != null) {
                DownloadQueue.remove(downloadEntry, false);
                DownloadQueue.processDownloadQueue();
            }
        }
    }

    private static class DownloadQueueImageList extends ImageList {

        @Override
        public ImageData getImage(int index) {
            if (sDownloadQueue == null) {
                return null;
            }
            if(index < sDownloadQueue.size()) {
                return sDownloadQueue.get(index).mImageData;
            }
            return null;
        }

        @Override
        public int length() {
            if (sDownloadQueue == null) {
                return 0;
            }
            return sDownloadQueue.size();
        }
    }
}
