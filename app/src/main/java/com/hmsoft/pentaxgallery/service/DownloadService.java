package com.hmsoft.pentaxgallery.service;

import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.FilteredImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.data.LocationTable;
import com.hmsoft.pentaxgallery.ui.ImageGridActivity;
import com.hmsoft.pentaxgallery.ui.camera.CameraFragment;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
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

public class DownloadService extends IntentService {

    private static final String TAG = "DownloadService";

    private static int shouldCancelId = -1;

    public static final int ALLOWED_CONSECUTIVE_ERRORS = 3;
    public static final int DOWNLOAD_FINISHED = 1000;
    public static final int UPDATE_PROGRESS = 1001;

    public static final int DOWNLOAD_STATUS_SUCCESS = 0;
    public static final int DOWNLOAD_STATUS_CANCELED = 1;
    public static final int DOWNLOAD_STATUS_ERROR = 2;
    public static final int DOWNLOAD_STATUS_TOO_MANY_ERRORS = 3;

    private static final String ACTION_DOWNLOAD = "action.DOWNLOAD";
    public static final String EXTRA_PROGRESS = "extra.PROGRESS";
    public static final String EXTRA_DOWNLOAD_ID = "extra.DOWNLOAD_ID";
    public static final String EXTRA_DOWNLOAD_STATUS = "extra.DOWNLOAD_STATUS";
    public static final String EXTRA_DOWNLOAD_STATUS_MESSAGE = "extra.DOWNLOAD_STATUS_MESSAGE";
    public static final String EXTRA_LOCAL_URI = "extra.LOCAL_URI";
    private static final String EXTRA_RECEIVER = "extra.RECEIVER";

    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");

    private static int downloadId = 0;
    private static int downloadErrorCount = 0;
    private static boolean sShutCameraDownWhenDone;

    private static boolean displayNotification;

    public static boolean isDisplayingNotification() {
        return displayNotification;
    }

    public static void setDisplayNotification(boolean displayNotification) {
        DownloadService.displayNotification = displayNotification;
    }

    public interface OnDownloadFinishedListener {
        void onDownloadProgress(ImageData imageData, long donloadId, int progress);
        void onDownloadFinished(ImageData imageData, long donloadId, int remainingDownloads,
                                int downloadCount, int errorCount, boolean wasCanceled);
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
                final int downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1);
                ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra(EXTRA_RECEIVER);

                if(BuildConfig.DEBUG) {
                    Logger.debug(TAG, "Download start: " + downloadId);
                }

                handleActionDownload(downloadId, receiver);

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
        this.startForeground(Queue.PROGRESS_NOTIFICATION_ID,
                Queue.createDownloadNotificationBuilder(MyApplication.ApplicationContext).build());

        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onStart");
    }


    @Override
    public void onDestroy() {
        this.stopForeground(true);
        super.onDestroy();
        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onDestroy");
    }
  
    private void handleActionDownload(int downloadId, ResultReceiver receiver) {

        Bundle resultData = new Bundle();

        if (downloadErrorCount >= ALLOWED_CONSECUTIVE_ERRORS) {
            resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
            resultData.putInt(EXTRA_DOWNLOAD_STATUS, DOWNLOAD_STATUS_TOO_MANY_ERRORS);
            receiver.send(DOWNLOAD_FINISHED, resultData);
            downloadErrorCount = 0;
            return;
        }

        DownloadEntry downloadEntry = Queue.findDownloadEntry(downloadId);
        if (downloadEntry == null) {
            if (BuildConfig.DEBUG) {
                Logger.debug(TAG, "No download with id " + downloadId + " found");
            }
            return;
        }

        boolean canceled = false;
        int status;
        String statusMessage = "";
        int fileLength = 0;

        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
        resultData.putInt(EXTRA_PROGRESS, 0);
        receiver.send(UPDATE_PROGRESS, resultData);

        Uri uri = null;

        Context context = MyApplication.ApplicationContext;
        ContentResolver cr = context.getContentResolver();
        try {
            cancelDownload(-1);
            ImageData imageData = downloadEntry.getImageData();

            //create url and connect
            URL url = new URL(imageData.getDownloadUrl());
            URLConnection connection = url.openConnection();

            Camera camera = Camera.instance;

            connection.setConnectTimeout(camera.getPreferences().getConnectTimeout() / 2);
            connection.connect();

            // this will be useful so that you can show a typical 0-100% progress bar
            fileLength = connection.getContentLength();

            ImageMetaData imageMetaData = camera.getImageInfo(imageData);
            uri = imageData.getLocalStorageUri();

            if (uri == null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.TITLE, imageData.uniqueFileName);
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageData.uniqueFileName);
                values.put(MediaStore.Images.Media.DESCRIPTION, imageData.uniqueFileName);
                values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
                values.put(MediaStore.Images.Media.MIME_TYPE, imageData.isRaw ? "image/x-adobe-dng" : "image/jpeg");
                values.put(MediaStore.Images.Media.SIZE, fileLength);

                if (imageMetaData != null) {
                    values.put(MediaStore.Images.Media.ORIENTATION, imageMetaData.orientationDegrees);
                    try {
                        Date date = dateFormat.parse(imageMetaData.dateTime);
                        values.put(MediaStore.Images.Media.DATE_TAKEN, date.getTime());

                        LocationTable.LatLong latLong = LocationService.getLocationAtTime(date);
                        if (latLong != null) {
                            if (Logger.DEBUG) Logger.debug(TAG, "Found location info for image: " + latLong.latitude + "," + latLong.longitude);
                            // TODO: Do no use Images.Media.LATITUDE/Images.Media.LONGITUDE fields, save location data in exif.
                            values.put(MediaStore.Images.Media.LATITUDE, latLong.latitude);
                            values.put(MediaStore.Images.Media.LONGITUDE, latLong.longitude);
                        }
                    } catch (ParseException e) {
                        Logger.warning(TAG, "Error parsing date: " + imageMetaData.dateTime, e);
                    }
                }

                //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    File localPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                            camera.getImageLocalPath(imageData).getPath());
                    values.put(MediaStore.MediaColumns.DATA, localPath.getAbsolutePath());
                    localPath.getParentFile().mkdirs();
                //} else {
                    //MediaStore.MediaColumns.RELATIVE_PATH
                    //values.put(MediaStore.Images.Media.IS_PENDING, 1);
                //}

                uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    Logger.warning(TAG, "Failed to insert image in gallery " + imageData.fileName);
                }
            }

            // downloadDown the file
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
                    output.write(data, 0, count);

                    int progress = (int) (total * 100 / fileLength);
                    if (progress > lastProgress) {
                        // publishing the progress....
                        resultData = new Bundle();
                        resultData.putInt(EXTRA_PROGRESS, progress);
                        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
                        receiver.send(UPDATE_PROGRESS, resultData);
                        lastProgress = progress;
                    }
                }
                status = DOWNLOAD_STATUS_SUCCESS;
                downloadErrorCount = 0;
            } finally {
                // close streams
                output.flush();
                output.close();
                input.close();

                if (canceled) {
                    status = DOWNLOAD_STATUS_CANCELED;
                }
            }
        } catch (Exception e) {
            Logger.warning(TAG, "Error downloading file", e);
            status = DOWNLOAD_STATUS_ERROR;
            statusMessage = e.getLocalizedMessage();
            downloadErrorCount++;
        }

        if (status != DOWNLOAD_STATUS_SUCCESS && uri != null) {
            try {
                cr.delete(uri, null, null);
            } catch (Exception ignored) {

            }
            uri = null;
        }

        if (Build.VERSION.SDK_INT >= /*Build.VERSION_CODES.Q*/ 29 && uri != null) {
            //values.put(MediaStore.Images.Media.IS_PENDING, 0);
        }

        resultData = new Bundle();
        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
        resultData.putInt(EXTRA_DOWNLOAD_STATUS, status);
        resultData.putString(EXTRA_DOWNLOAD_STATUS_MESSAGE, statusMessage);
        if (uri != null) {
            resultData.putString(EXTRA_LOCAL_URI, uri.toString());
        }
        receiver.send(DOWNLOAD_FINISHED, resultData);
    }

    public static void toggleShutCameraDownWhenDone() {
        sShutCameraDownWhenDone = !sShutCameraDownWhenDone;
    }

    public static void setShutCameraDownWhenDone(boolean setShutCameradownWhenDone) {
        sShutCameraDownWhenDone = setShutCameradownWhenDone;
    }

    public static boolean shutCameraDownWhenDone() {
        return sShutCameraDownWhenDone;
    }

    /*private*/ static int downloadDown(Context context, DownloadEntry downloadEntry) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);        
        intent.putExtra(EXTRA_RECEIVER, Queue.DownloadResultReceiver);
        intent.putExtra(EXTRA_DOWNLOAD_ID, ++downloadId);
        downloadEntry.setDownloadId(downloadId);
        context.startForegroundService(intent);
        if(BuildConfig.DEBUG) Logger.debug(TAG, "Download added to service: " + downloadId);
        return downloadId;
    }

    public static void cancelAllDownloads() {
        Queue.cancelAll();
    }

    public static void cancelCurrentDownload() {
        cancelDownload(0);
    }

    public synchronized static void cancelDownload(int downloadId) {
        shouldCancelId = downloadId;
    }
    
    public static void setOnDownloadFinishedListener(OnDownloadFinishedListener onDownloadFinishedListener) {
        Queue.onDownloadFinishedListener = onDownloadFinishedListener;
    }
    
    public static DownloadEntry findDownloadEntry(ImageData imageData) {
        return Queue.findDownloadEntry(imageData);
    }
    
    public static void downloadDown(ImageData imageData) {
        // TODO: Implement
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
    
    public static void saveQueueToFile(CameraData cameraData) {
        Queue.saveToFile(cameraData);
    }
    
    public static void processDownloadQueue() {
        int added = Queue.processDownloadQueue(true);
    }
    
    public static void setInBatchDownload(boolean inBatchDownload) {
        Queue.inBatchDownload = inBatchDownload;
    }

    public static void loadQueueFromFile(ImageList sourceImageList, CameraData cameraData) {
        Queue.loadFromFile(sourceImageList, cameraData);
    }

    public static boolean isDownloading() {
        return Queue.isDownloading();
    }

    public static String getQueueFinishETA() {
        return Queue.getETAString();
    }

    private static class Queue {

        private static final String CACHE_KEY = "downloadQueue.cache";

        private static final String TAG = "Queue";

        private static final int PROGRESS_NOTIFICATION_ID = 5;
        private static final int DONE_NOTIFICATION_ID = 6;
        private static final String FILE_NAME_DOWNLOAD_QUEUE = "download.queue";

        private static Hashtable<Integer, DownloadEntry> sDownloadQueueDict = null;
        private static List<DownloadEntry> sDownloadQueue;
        private final static ImageList sIageList = new DownloadQueueImageList();
        private static int downloadCount = 0;
        private static int errorCount = 0;
        private static long sStarDownloadTime = -1;
        private static long sLastEtaUpdate = -1;
        private static String sLastEtatext;

        private static class DownloadReceiver extends ResultReceiver {

            public DownloadReceiver(Handler handler) {
                super(handler);
            }

            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {

                super.onReceiveResult(resultCode, resultData);
              
                int id = resultData.getInt(EXTRA_DOWNLOAD_ID);
                DownloadEntry downloadEntry = Queue.findDownloadEntry(id);
                if(downloadEntry == null) {
                   if(BuildConfig.DEBUG) {
                       Logger.warning(TAG, "No downloadDown found with id: " + id);
                    }
                    return;
                }

                Context context = MyApplication.ApplicationContext;
                final ImageData imageData = downloadEntry.getImageData();
              
                if (resultCode == UPDATE_PROGRESS) {
                    int progress = resultData.getInt(EXTRA_PROGRESS);                    
                    
                    downloadEntry.setProgress(progress);
                    downloadNotification(imageData, progress);                    
                    doDownloadProgress(imageData, id, progress);                    
                  
                    if(BuildConfig.DEBUG) {
                       Logger.debug(TAG, "Update Progress: " + id + " - " + progress + "%");
                    }
                } else if (resultCode == DOWNLOAD_FINISHED) {
                    if(BuildConfig.DEBUG) { 
                          Logger.debug(TAG, "Finished: " + id);
                    }
                  
                    int status = resultData.getInt(EXTRA_DOWNLOAD_STATUS);

                    if(status == DOWNLOAD_STATUS_SUCCESS) {
                        Queue.downloadCount++;
                        String localUri = resultData.getString(EXTRA_LOCAL_URI);
                        if(localUri != null && localUri.length() > 0) {
                            Uri uri = Uri.parse(localUri);
                            imageData.setGalleryId(Integer.parseInt(uri.getLastPathSegment()));
                            imageData.setLocalStorageUri(uri);
                            TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
                                @Override
                                public void run() {
                                    imageData.saveData();
                                }
                            });
                        }
                    } else if(status == DOWNLOAD_STATUS_ERROR) {
                        Queue.errorCount++;
                        String message = resultData.getString(EXTRA_DOWNLOAD_STATUS_MESSAGE);                        
                        if(message != null && message.length() > 0) {
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        }                        
                    } else if(status == DOWNLOAD_STATUS_CANCELED) {
                        Toast.makeText(context, context.getString(R.string.download_canceled) + imageData.fileName, Toast.LENGTH_LONG).show();
                    } else if(status == DOWNLOAD_STATUS_TOO_MANY_ERRORS) {
                        Toast.makeText(context, R.string.too_many_errors, Toast.LENGTH_LONG).show();
                        Queue.errorCount += sDownloadQueue.size();
                        cancelAll();
                        return;
                    }

                    downloadNotification(downloadEntry.getImageData(), 100);
                    Queue.remove(downloadEntry, false);
                    Queue.processDownloadQueue(false);

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
        
        private static OnDownloadFinishedListener onDownloadFinishedListener;

        private static void doDownloadProgress(ImageData imageData, long donloadId, int progress) {
            if(onDownloadFinishedListener != null) {
                onDownloadFinishedListener.onDownloadProgress(imageData, donloadId, progress);
            }
        }

        private static void doDownloadFinished(ImageData imageData, long donloadId, boolean wasCanceled) {

            if(onDownloadFinishedListener != null) {
                onDownloadFinishedListener.onDownloadFinished(imageData, donloadId, sDownloadQueue.size(),
                        Queue.downloadCount, Queue.errorCount, wasCanceled);
            }

            if(sDownloadQueue.size() == 0) {
                inBatchDownload = false;
                if(sWackeLock != null) {
                    sWackeLock.release();
                    sWackeLock = null;
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "WakeLock released");
                }
                if(sShutCameraDownWhenDone && !wasCanceled && !CameraFragment.isInLiveView()) {
                    Camera.instance.powerOff();
                }
                downloadNotification(null, donloadId > 0 ? 0 : -1);
                sStarDownloadTime = -1;
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
            doDownloadFinished(null, -1, true);
        }

        /*private*/ static void remove(DownloadEntry downloadEntry, boolean canceled) {
            sDownloadQueue.remove(downloadEntry);
            if(sDownloadQueueDict != null && downloadEntry.getDownloadId() > 0) {
                sDownloadQueueDict.remove(downloadEntry.getDownloadId());
            }
            downloadEntry.getImageData().setIsInDownloadQueue(false);
            doDownloadFinished(downloadEntry.mImageData, downloadEntry.getDownloadId(), canceled);
        }
              
        /*public*/ static boolean inBatchDownload;

        private static NotificationManagerCompat notificationManager;
        
        public static ResultReceiver DownloadResultReceiver = new DownloadReceiver(new Handler());

        public static void downloadNotification(ImageData imageData, int progress) {
            downloadNotification(imageData, progress, null);
        }

        public static NotificationCompat.Builder createDownloadNotificationBuilder(Context context) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MyApplication.NOTIFICATION_CHANNEL_ID);

            PendingIntent pi = ImageGridActivity.getPendingIntent();
            builder.setSmallIcon(R.drawable.ic_cloud_download_white_24dp)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pi);

            return builder;
        }
        public static void downloadNotification(ImageData imageData, int progress, Service foregroundService) {

            if (progress % 5 != 0) {
                return;
            }

            Context context = MyApplication.ApplicationContext;
            if(notificationManager == null) {
                notificationManager = NotificationManagerCompat.from(context);
            }

            if(!displayNotification) {
                notificationManager.cancel(PROGRESS_NOTIFICATION_ID);
                return;
            }

            NotificationCompat.Builder builder = createDownloadNotificationBuilder(context);
            if(imageData != null) {
                //i.putExtra(ImageGridActivity.EXTRA_START_DOWNLOADS, true);

                int remainingDownloads = sDownloadQueue.size();
                builder.setContentTitle(context.getString(R.string.download_notification_title))
                       .setContentText(String.format("%s (%d)", imageData.fileName, remainingDownloads))
                       .setOngoing(true)
                       .setLargeIcon(imageData.getThumbBitmap())
                       .setShowWhen(false)
                       .setProgress(100, progress, progress == 0);

                String eta = getETAString((float) remainingDownloads);
                if (!TextUtils.isEmpty(eta)) {
                    builder.setSubText(eta);
                }


                if (foregroundService != null) {
                    foregroundService.startForeground(PROGRESS_NOTIFICATION_ID, builder.build());
                } else {
                    notificationManager.notify(PROGRESS_NOTIFICATION_ID, builder.build());
                }

            } else {
                if (foregroundService != null) {
                    foregroundService.stopForeground(true);
                } else {
                    notificationManager.cancel(PROGRESS_NOTIFICATION_ID);
                }

                String contentText = null;
                if(Queue.downloadCount > 0 && Queue.errorCount > 0) {
                    contentText = String.format(context.getString(R.string.download_done_with_fails_notification_text), Queue.downloadCount,
                                               Queue.errorCount);
                } else if (Queue.downloadCount > 0) {
                    contentText = String.format(context.getString(R.string.download_done_notification_text), Queue.downloadCount);
                } else if (Queue.errorCount > 0) {
                  contentText = String.format(context.getString(R.string.download_done_failed_notification_text), Queue.errorCount);
                }

                if(contentText != null) {
                    int totalTime = Math.round((SystemClock.elapsedRealtime() - sStarDownloadTime) / 1000);
                    if (totalTime > 0) {
                        String ataText = "Total: ";
                        if (totalTime < 60) {
                            ataText += totalTime + "s";
                        } else {
                            if (totalTime % 60 > 6) {
                                ataText += "< ";
                            }
                            ataText += Math.round(Math.ceil(totalTime / (float)60)) +  "m";
                        }

                        if (Logger.DEBUG) {
                            Logger.debug(TAG, "ATA: " + totalTime / (float)60);
                        }
                        builder.setSubText(ataText);
                    }

                    builder.setContentText(contentText)
                            .setAutoCancel(true)
                            .setContentTitle(context.getString(R.string.download_done_notification_title));
                    notificationManager.notify(DONE_NOTIFICATION_ID, builder.build());
                }

                Queue.errorCount = 0;
                Queue.downloadCount = 0;

                if(progress > -1) {
                   Toast.makeText(context, R.string.donwload_done_notification_title, Toast.LENGTH_LONG).show();
                }
            }
        }

        public static String getETAString() {
            return  getETAString((float)sDownloadQueue.size());
        }

        private static String getETAString(float remainingDownloads) {
            String etaText = null;
            long elapsedRealTime = SystemClock.elapsedRealtime();
            if (sStarDownloadTime > 0 && elapsedRealTime - sLastEtaUpdate > 40000) {
                int downloaded = Queue.downloadCount;
                long elapsedDownloadTime = (elapsedRealTime - sStarDownloadTime) / 1000;
                float downloadsPerSecond = (float)downloaded / (float)elapsedDownloadTime;
                if (downloadsPerSecond > 0) {
                    sLastEtaUpdate = elapsedRealTime;
                    int remainingMinutes = Math.round((remainingDownloads / (float)downloadsPerSecond) / 60);
                    etaText =  "ETA: ";
                    switch (remainingMinutes) {
                        case 0:
                            etaText += " < 1m";
                            break;
                        default:
                            etaText += remainingMinutes + "m";
                    }

                    sLastEtatext = etaText;
                    if (Logger.DEBUG) Logger.debug(TAG, etaText);
                }
            }

            return sLastEtatext;
        }

        /*public*/ static void loadFromFile(ImageList sourceImageList, CameraData cameraData) {
            if (sourceImageList == null || sourceImageList.length() == 0) {
                return;
            }

            if (sDownloadQueue == null) {
                sDownloadQueue = new ArrayList<>();
            } else {
                sDownloadQueue.clear();
            }

            try {
                String json = Utils.readTextFile(new File(cameraData.getStorageDirectory(), FILE_NAME_DOWNLOAD_QUEUE));
                JSONArray jsonArray = new JSONArray(json);
                for (int c = 0; c < jsonArray.length(); c++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(c);
                    String fileName = jsonObject.getString(DownloadEntry.UNIQUE_FILE_NAME);
                    ImageData imageData = sourceImageList.findByUniqueFileName(fileName);
                    if (imageData != null && !imageData.existsOnLocalStorage()) {
                        DownloadEntry downloadEntry = new DownloadEntry(imageData);
                        sDownloadQueue.add(downloadEntry);
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }

        /*public*/ static void saveToFile(CameraData cameraData) {
            JSONArray jsonArray = new JSONArray();
            if(sDownloadQueue != null) {
                for (DownloadEntry downloadEntry : sDownloadQueue) {
                    JSONObject jsonObject = downloadEntry.toJSONbject();
                    if(jsonObject != null) {
                        jsonArray.put(jsonObject);
                    }
                }
            }
            try {
                Utils.saveTextFile(new File(cameraData.getStorageDirectory(), FILE_NAME_DOWNLOAD_QUEUE), jsonArray.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static DownloadEntry getNextDownload() {
            return findDownloadEntry(0);
        }

        /*private*/ static void download(DownloadEntry downloadEntry) {
            if(sWackeLock == null) {
                sWackeLock = MyApplication.acquireWakeLock();
            }            

            Context context = MyApplication.ApplicationContext;
            DownloadService.downloadDown(context, downloadEntry);
        }
        
        /*public*/ static DownloadEntry findDownloadEntry(ImageData imageData) {
            if (sDownloadQueue == null) {
                return null;
            }

            for (DownloadEntry downloadEntry : sDownloadQueue) {
                if (downloadEntry.getImageData().equals(imageData)) {
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
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "Found downloadDown entry in list: " + downloadId);
                    if(downloadId > 0) {
                        sDownloadQueueDict.put(downloadId, downloadEntry);
                    }
                    return downloadEntry;
                }
            }

            if(BuildConfig.DEBUG) Logger.warning(TAG, "No downloadDown entry found: " + downloadId);

            return null;
        }

        public static synchronized boolean isDownloading() {
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
                download(downloadEntry);
                return downloadEntry;
            }
            return null;
        }

        public static int processDownloadQueue(boolean all) {
            int count = 0;
            if (all || !isDownloading()) {
                DownloadEntry downloadEntry = null;
                sStarDownloadTime = SystemClock.elapsedRealtime();
                sLastEtatext = "";
                sLastEtaUpdate = sStarDownloadTime;
                while((downloadEntry = getNextDownload()) != null) {
                    download(downloadEntry);
                    count++;
                }
                if(BuildConfig.DEBUG && count > 0) {
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
