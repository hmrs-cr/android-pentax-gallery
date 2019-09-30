package com.hmsoft.pentaxgallery.service;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.widget.Toast;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.CameraFactory;
import com.hmsoft.pentaxgallery.camera.model.FilteredImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.ui.ImageGridActivity;
import com.hmsoft.pentaxgallery.util.DefaultSettings;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.cache.CacheUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * An {@link IntentService} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 * <p>
 * TODO: Customize class - update intent actions, extra parameters and static
 * helper methods.
 */
public class DownloadService extends IntentService {

    private static final String TAG = "DownloadService";

    private static int shouldCancelId = -1;

    public static final int DOWNLOAD_FINISHED = 1000;
    public static final int UPDATE_PROGRESS = 1001;

    public static final int DOWNLOAD_STATUS_SUCCESS = 0;
    public static final int DOWNLOAD_STATUS_CANCELED = 1;
    public static final int DOWNLOAD_STATUS_ERROR = 2;
    public static final int DOWNLOAD_STATUS_TOO_MANY_ERRORS = 3;

    private static final String ACTION_DOWNLOAD = "com.hmsoft.pentaxgallery.service.action.DOWNLOAD";
    public static final String EXTRA_PROGRESS = "com.hmsoft.pentaxgallery.service.extra.PROGRESS";
    public static final String EXTRA_DOWNLOAD_ID = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_ID";
    public static final String EXTRA_DOWNLOAD_STATUS = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_STATUS";
    public static final String EXTRA_DOWNLOAD_STATUS_MESSAGE = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_STATUS_MESSAGE";
    public static final String EXTRA_LOCAL_URI = "com.hmsoft.pentaxgallery.service.extra.LOCAL_URI";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private static int downloadId = 0;

    private static int errorCount = 0;

    private static final String EXTRA_DOWNLOAD_URL = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_URL";

    public interface OnDownloadFinishedListener {
        void onDownloadProgress(ImageData imageData, long donloadId, int progress);
        void onDownloadFinished(ImageData imageData, long donloadId, int remainingDownloads, 
                                boolean wasCanceled);        
    }
  
    public static final FilteredImageList.ImageFilter DownloadQueueFilter = new FilteredImageList.ImageFilter() {
        @Override
        public boolean passFilter(ImageData imageData) {
            return imageData.inDownloadQueue();
        }

        @Override
        public ImageList getImageList() {
            return Queue.getImageList();
        }
    };

    public DownloadService() {
        super("DownloadService");
    }    

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_DOWNLOAD.equals(action)) {
                final String url = intent.getStringExtra(EXTRA_DOWNLOAD_URL);
                final int downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1);
                ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");


                if(BuildConfig.DEBUG) {
                    Logger.debug(TAG, "Download start: " + downloadId);
                }

                handleActionDownload(url, downloadId, receiver);

                if(BuildConfig.DEBUG) {
                    Logger.debug(TAG, "Download end: " + downloadId);
                }
            }
        }
    }


    @Override
    public void onCreate() {
        super.onCreate();
        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onCreate");
    }

    @Override
    public void onStart(@Nullable Intent intent, int startId) {
        super.onStart(intent, startId);
        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onStart");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onDestroy");
    }

    public static int download(Context context, String url) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_DOWNLOAD_URL, url);
        intent.putExtra("receiver", Queue.DownloadResultReceiver);
        intent.putExtra(EXTRA_DOWNLOAD_ID, ++downloadId);
        context.startService(intent);
        if(BuildConfig.DEBUG) Logger.debug(TAG, "Download added to service: " + downloadId);
        return downloadId;
    }

    public static void cancelCurrentDownload() {
        cancelDownload(0);
    }

    public synchronized static void cancelDownload(int downloadId) {
        shouldCancelId = downloadId;
    }
  
    public static void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Context context = MyApplication.ApplicationContext;
            String name = context.getString(R.string.download_notification_channel_name);
            String description = context.getString(R.string.download_notification_channel_desc);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(Queue.CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(null,null);
            channel.enableLights(false);
            channel.enableVibration(false);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = MyApplication.ApplicationContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    public static void setOnDowloadFinishedListener(OnDownloadFinishedListener onDowloadFinishedListener) {
        Queue.onDowloadFinishedListener = onDowloadFinishedListener;
    }
    
    public static DownloadEntry findDownloadEntry(ImageData imageData) {
        return Queue.findDownloadEntry(imageData);
    }
    
    public static void download(ImageData imageData) {
        DownloadEntry downloadEntry = findDownloadEntry(imageData);
        if (downloadEntry != null) {
            Queue.download(downloadEntry);
        }
    }
    
    public static void removeFromDownloadQueue(ImageData imageData) {
        DownloadEntry downloadEntry = findDownloadEntry(imageData);
        imageData.setIsInDownloadQueue(false);
        if (downloadEntry != null) {
            int downloadId = downloadEntry.getDownloadId();
            if(downloadId > 0) {
                cancelDownload(downloadEntry.getDownloadId());
            }
            Queue.remove(downloadEntry, true);
        }
    }
    
    public static DownloadEntry addDownloadQueue(ImageData imageData) {
       return Queue.addDownloadQueue(imageData, Queue.inBatchDownload);
    }
    
    public static void saveQueueToCache() {
        Queue.saveToCache();    
    }
    
    public static void processDownloadQueue() {
        int added = Queue.processAllDownloadQueue();
    }
    
    public static void setInBatchDownload(boolean inBatchDownload) {
        Queue.inBatchDownload = inBatchDownload;
    }

    public static void loadQueueFromCache(ImageList sourceImageList, boolean forceLoad) {
        Queue.loadFromCache(sourceImageList, forceLoad);
    }

    private void handleActionDownload(String downloadUrl, int downloadId, ResultReceiver receiver) {

        DownloadEntry downloadEntry = Queue.findDownloadEntry(downloadId);
        if(downloadEntry == null) {
            if(BuildConfig.DEBUG) {
                Logger.debug(TAG, "No download with id " + downloadId + " found");
                return;
            }
        }


        boolean canceled = false;
        int status;
        String statusMessage = "";
        int fileLength = 0;

        Bundle resultData = new Bundle();
        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);

        resultData.putInt(EXTRA_PROGRESS, 0);
        receiver.send(UPDATE_PROGRESS, resultData);

        Uri uri = null;

        if (errorCount >= 3) {
            resultData.putInt(EXTRA_DOWNLOAD_STATUS, DOWNLOAD_STATUS_TOO_MANY_ERRORS);
            receiver.send(DOWNLOAD_FINISHED, resultData);
            errorCount = 0;
            return;
        }

        Context context = MyApplication.ApplicationContext;
        ContentResolver cr = context.getContentResolver();

        try {
            cancelDownload(-1);
            //create url and connect
            URL url = new URL(downloadUrl);
            URLConnection connection = url.openConnection();

            DefaultSettings settings = DefaultSettings.getsInstance();
            int connectTimeOut = settings.getIntValue(DefaultSettings.DEFAULT_CONNECT_TIME_OUT);
            connection.setConnectTimeout(connectTimeOut * 500);

            connection.connect();

            // this will be useful so that you can show a typical 0-100% progress bar
            fileLength = connection.getContentLength();


            ImageData imageData  = downloadEntry.getImageData();
            ImageMetaData imageMetaData = CameraFactory.DefaultCamera.getImageInfo(imageData);

            uri = imageData.getLocalStorageUri();
            if(uri == null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, imageData.uniqueFileName);
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageData.uniqueFileName);
                values.put(MediaStore.Images.Media.DESCRIPTION, imageData.uniqueFileName);
                values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.SIZE, fileLength);
                if(imageMetaData != null) {
                    try {
                        Date date = dateFormat.parse(imageMetaData.dateTime);
                        values.put(MediaStore.Images.Media.ORIENTATION, imageMetaData.orientationDegrees);
                        values.put(MediaStore.Images.Media.DATE_TAKEN, date.getTime());
                    } catch (ParseException e) {
                        Logger.warning(TAG, "Error parsing date: " + imageMetaData.dateTime, e);
                    }
                }

                if (Build.VERSION.SDK_INT < /*Build.VERSION_CODES.Q*/ 29) {
                    values.put(MediaStore.MediaColumns.DATA, imageData.getLocalPath().getAbsolutePath());
                } else {
                    //MediaStore.MediaColumns.RELATIVE_PATH
                }

                uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    Logger.warning(TAG, "Failed to insert image in gallery " + imageData.fileName);
                }
            }

            /* TODO: Fix raw image download */

            // download the file
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = context.getContentResolver().openOutputStream(uri);

            byte[] data = new byte[1024];
            long total = 0;
            int lastProgress = 0;
            int count;
            try {
                while ((count = input.read(data)) != -1) {

                    if (shouldCancelId == downloadId || shouldCancelId == 0) {
                        canceled = true;
                        break;
                    }

                    total += count;
                    int progress = (int) (total * 100 / fileLength);
                    if (progress > lastProgress) {
                        // publishing the progress....
                        resultData = new Bundle();
                        resultData.putInt(EXTRA_PROGRESS, progress);
                        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
                        receiver.send(UPDATE_PROGRESS, resultData);
                        lastProgress = progress;
                    }

                    output.write(data, 0, count);
                }
                status = DOWNLOAD_STATUS_SUCCESS;
                errorCount = 0;
            } finally {
                // close streams
                output.flush();
                output.close();
                input.close();

                if (canceled) {
                    status = DOWNLOAD_STATUS_CANCELED;
                }
            }
        } catch (IOException e) {
            Logger.warning(TAG, "Error downloading file", e);
            status = DOWNLOAD_STATUS_ERROR;
            statusMessage = e.getLocalizedMessage();
            errorCount++;
        }

        if (status != DOWNLOAD_STATUS_SUCCESS && uri != null) {
            cr.delete(uri, null, null);
        }

        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
        resultData.putInt(EXTRA_DOWNLOAD_STATUS, status);
        resultData.putString(EXTRA_DOWNLOAD_STATUS_MESSAGE, statusMessage);
        if (uri != null) {
            resultData.putString(EXTRA_LOCAL_URI, uri.toString());
        }
        receiver.send(DOWNLOAD_FINISHED, resultData);
    }
    
    private static class Queue {

        private static final String CACHE_KEY = "downloadQueue.cache";

        private static final String TAG = "Queue";
        private static final String CHANNEL_ID = "DownloadChannel";

        private static final int PROGRESS_NOTIFICATION_ID = 5;
        private static final int DONE_NOTIFICATION_ID = 6;

        private static Hashtable<Integer, DownloadEntry> sDownloadQueueDict = null;
        private static List<DownloadEntry> sDownloadQueue;
        private final static ImageList sIageList = new DownloadQueueImageList();
        private static int downloadCount = 0;
        private static int errorCount = 0;

        private static class DownloadReceiver extends ResultReceiver {

            public DownloadReceiver(Handler handler) {
                super(handler);
            }

            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {

                super.onReceiveResult(resultCode, resultData);
                Context context = MyApplication.ApplicationContext;

                int id = resultData.getInt(EXTRA_DOWNLOAD_ID);
                DownloadEntry downloadEntry = Queue.findDownloadEntry(id);

                if (resultCode == UPDATE_PROGRESS) {
                    int progress = resultData.getInt(EXTRA_PROGRESS);
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "Update Progress: " + id + " - " + progress + "%");
                    if(downloadEntry != null) {
                        downloadNotification(downloadEntry.getImageData(), progress);
                        downloadEntry.setProgress(progress);
                        doDownloadProgress(downloadEntry.getImageData(), id, progress);
                    }
                } else if (resultCode == DOWNLOAD_FINISHED) {
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "Finished: " + id);
                    if (downloadEntry != null) {

                        int status = resultData.getInt(EXTRA_DOWNLOAD_STATUS);

                        if(status == DOWNLOAD_STATUS_SUCCESS) {
                            Queue.downloadCount++;
                            String localUri = resultData.getString(EXTRA_LOCAL_URI);
                            if(localUri != null && localUri.length() > 0) {
                                downloadEntry.getImageData().setLocalStorageUri(Uri.parse(localUri));
                            }
                        } else if(status == DOWNLOAD_STATUS_ERROR) {
                            String message = resultData.getString(EXTRA_DOWNLOAD_STATUS_MESSAGE);
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                            Queue.errorCount++;
                        } else if(status == DOWNLOAD_STATUS_CANCELED) {
                            Toast.makeText(context, "Download canceled: " + downloadEntry.getImageData().fileName, Toast.LENGTH_LONG).show();
                        } else if(status == DOWNLOAD_STATUS_TOO_MANY_ERRORS) {
                            Toast.makeText(context, "Too many errors.", Toast.LENGTH_LONG).show();
                            cancelAll();
                            return;
                        }

                        downloadNotification(downloadEntry.getImageData(), 100);
                        Queue.remove(downloadEntry, false);
                        Queue.processAllDownloadQueue();
                    }
                }
            }
        }        
        
        private static class DownloadQueueImageList extends ImageList {

            @Override
            protected ImageData createImageData(String dirName, String fileName) {
                return null;
            }

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
        
        private static PowerManager.WakeLock sWackeLock;
        
        private static OnDownloadFinishedListener onDowloadFinishedListener;

        private static void doDownloadProgress(ImageData imageData, long donloadId, int progress) {
            if(onDowloadFinishedListener != null) {
                onDowloadFinishedListener.onDownloadProgress(imageData, donloadId, progress);
            }
        }

        private static void doDowloadFinished(ImageData imageData, long donloadId, boolean wasCanceled) {

            if(onDowloadFinishedListener != null) {
                onDowloadFinishedListener.onDownloadFinished(imageData, donloadId, sDownloadQueue.size(), wasCanceled);
            }

            if(sDownloadQueue.size() == 0) {
                inBatchDownload = false;
                if(sWackeLock != null) {
                    sWackeLock.release();
                    sWackeLock = null;
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "WakeLock released");
                }
                downloadNotification(null, donloadId > 0 ? 0 : -1);
            }
        }

        private static void cancelAll() {
            cancelCurrentDownload();
            for(DownloadEntry downloadEntry : sDownloadQueue) {
                downloadEntry.getImageData().setIsInDownloadQueue(false);
            }
            sDownloadQueue.clear();
            if(sDownloadQueueDict != null) {
                sDownloadQueueDict.clear();
            }
            doDowloadFinished(null, -1, true);
        }

        /*private*/ static void remove(DownloadEntry downloadEntry, boolean canceled) {
            sDownloadQueue.remove(downloadEntry);
            if(sDownloadQueueDict != null && downloadEntry.getDownloadId() > 0) {
                sDownloadQueueDict.remove(downloadEntry.getDownloadId());
            }
            downloadEntry.getImageData().setIsInDownloadQueue(false);
            doDowloadFinished(downloadEntry.mImageData, downloadEntry.getDownloadId(), canceled);
        }
              
        /*public*/ static boolean inBatchDownload;        
        
        public static ResultReceiver DownloadResultReceiver = new DownloadReceiver(new Handler());

        public static void downloadNotification(ImageData imageData, int progress) {
            Context context = MyApplication.ApplicationContext;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

            PendingIntent pi = ImageGridActivity.getPendingIntent();
            builder.setSmallIcon(R.drawable.ic_cloud_download_white_24dp)
                       .setLocalOnly(true)
                       .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                       .setContentIntent(pi);

            if(imageData != null) {
                //i.putExtra(ImageGridActivity.EXTRA_START_DOWNLOADS, true);

                builder.setContentTitle(context.getString(R.string.download_notification_title))
                       .setContentText(String.format("%s (%d)", imageData.fileName, sDownloadQueue.size()))
                       .setOngoing(true)
                       .setLargeIcon(imageData.getThumbBitmap())
                       .setProgress(100, progress, progress == 0);

                notificationManager.cancel(DONE_NOTIFICATION_ID);
                notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build());
            } else {
                notificationManager.cancel(PROGRESS_NOTIFICATION_ID);

                if(Queue.downloadCount > 0) {
                    builder.setContentText(String.format(context.getString(R.string.download_done_notification_text), downloadCount))
                           .setContentTitle(context.getString(R.string.download_done_notification_title));
                    notificationManager.notify(DONE_NOTIFICATION_ID, builder.build());
                    Queue.downloadCount = 0;
                }

                Queue.errorCount = 0;

                if(progress > -1) {
                   Toast.makeText(context, R.string.donwload_done_notification_title, Toast.LENGTH_LONG).show();
                }
            }
        }

        /*public*/ static void loadFromCache(ImageList sourceImageList, boolean forceLoad) {
            if(sourceImageList == null || sourceImageList.length() == 0) {
                return;
            }

            if(sDownloadQueue == null || forceLoad) {
                String cacheValue = CacheUtils.getString(CACHE_KEY);
                if(cacheValue == null) {
                    return;
                }

                sDownloadQueue = new ArrayList<DownloadEntry>();
                try {
                    JSONArray jsonArray = new JSONArray(cacheValue);
                    for(int c = 0; c < jsonArray.length(); c++) {
                        JSONObject jsonObject = jsonArray.getJSONObject(c);
                        String fileName = jsonObject.getString(DownloadEntry.UNIQUE_FILE_NAME);
                        ImageData imageData = sourceImageList.findByUniqueFileName(fileName);
                        if(imageData != null) {
                            DownloadEntry downloadEntry = new DownloadEntry(imageData);
                            sDownloadQueue.add(downloadEntry);
                        }
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }

        /*public*/ static void saveToCache() {
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

        public static DownloadEntry getNextDownload() {
            return findDownloadEntry(0);
        }

        /*private*/ static void download(DownloadEntry downloadEntry) {

            if(sWackeLock == null) {
                sWackeLock = MyApplication.acquireWakeLock();
            }

            ImageData imageData = downloadEntry.getImageData();
            String url = imageData.getDownloadUrl();

            Context context = MyApplication.ApplicationContext;

            int id = DownloadService.download(context, url);
            downloadEntry.setDownloadId(id);

            if(BuildConfig.DEBUG) Logger.debug(TAG, "Starting download: " + imageData.fileName + ", ID: " + id);
        }
        
        /*public*/ static DownloadEntry findDownloadEntry(ImageData imageData) {
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

        public synchronized static DownloadEntry findDownloadEntry(int downloadId) {
            if (sDownloadQueue == null) {
                return null;
            }

             if (sDownloadQueueDict == null) {
                 sDownloadQueueDict = new Hashtable<>();
             }

             if(downloadId > 0 && sDownloadQueueDict.containsKey(downloadId)) {
                 return sDownloadQueueDict.get(downloadId);
             }

            for (DownloadEntry downloadEntry : sDownloadQueue) {
                if (downloadEntry.getDownloadId() == downloadId) {
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "Found download entry in list: " + downloadId);
                    if(downloadId > 0) {
                        sDownloadQueueDict.put(downloadId, downloadEntry);
                    }
                    return downloadEntry;
                }
            }

            if(BuildConfig.DEBUG) Logger.warning(TAG, "No download entry found: " + downloadId);

            return null;
        }

        public static boolean isDownloading() {
            if (sDownloadQueue == null) {
                return false;
            }

            for (DownloadEntry downloadEntry : sDownloadQueue) {
                if (downloadEntry.getDownloadId() > 0) {
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "Downloading:" + downloadEntry.getDownloadId());
                    return true;
                }
            }

            if(BuildConfig.DEBUG) Logger.debug(TAG, "Not downloading");
            return false;
        }

        public static DownloadEntry addDownloadQueue(ImageData imageData, boolean atTheBeginning) {
            if (sDownloadQueue == null) {
                sDownloadQueue = new ArrayList<DownloadEntry>();
            }

            DownloadEntry downloadEntry = findDownloadEntry(imageData);
            if(downloadEntry == null) {
                downloadEntry = new DownloadEntry(imageData);
            } else if(atTheBeginning) {
                sDownloadQueue.remove(downloadEntry);
            } else {
                downloadEntry = null;
            }

            if (downloadEntry != null) {
                imageData.setIsInDownloadQueue(true);
                if(atTheBeginning) {
                  sDownloadQueue.add(0, downloadEntry);
                } else {
                  sDownloadQueue.add(downloadEntry);
                }
                processDownloadQueue();
                return downloadEntry;
            }
            return null;
        }

        public static int processAllDownloadQueue() {
            int count = 0;
            if (!isDownloading()) {
                DownloadEntry downloadEntry = null;
                 while((downloadEntry = getNextDownload()) != null) {
                    download(downloadEntry);
                    count++;
                }
                if(BuildConfig.DEBUG) {
                    Logger.debug(TAG, "Downloads added: " + count);
                }
            }
            return count;
        }

        public static ImageList getImageList() {
            return sIageList;
        }
    }
    
    public static class DownloadEntry {

        public static final String DOWNLOAD_ID = "downloadId";
        public static final String UNIQUE_FILE_NAME = "uniqueFileName";

        public final ImageData mImageData;
        public int mDownloadId;
        private int mProgress = -1;

        public DownloadEntry(ImageData imageData)  {
            mImageData = imageData;
        }

        public ImageData getImageData() {
        return mImageData;
      }

        public int getDownloadId() {
          return mDownloadId;
        }

        public void setDownloadId(int downloadId) {
            mDownloadId = downloadId;
        }

        public JSONObject toJSONbject() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(DOWNLOAD_ID, mDownloadId);
                jsonObject.put(UNIQUE_FILE_NAME, mImageData.uniqueFileName);
                return jsonObject;
            } catch (JSONException e) {
                e.printStackTrace();
                return null;
            }
        }

        public int getProgress() {
            return mProgress;
        }

        public void setProgress(int progress) {
            mProgress = progress;
        }
    }
}
