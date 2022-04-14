package com.hmsoft.pentaxgallery.service;

import android.Manifest;
import android.app.Activity;
import android.app.IntentService;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
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
import com.hmsoft.pentaxgallery.camera.model.CameraPreferences;
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
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadService extends IntentService {

    private static ExecutorService downloadExecutor;
    private static final Object downloadSynchronizer = new Object();
    private static final String TAG = "DownloadService";

    private final Camera camera = Camera.instance;

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

    private static int downloadId = 0;
    private static int downloadErrorCount = 0;
    private static boolean sShutCameraDownWhenDone;

    private static boolean displayNotification;
    private int mNotifyDownloadId = -1;

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
        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onHandleIntent");
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_DOWNLOAD.equals(action)) {

                if (!camera.isConnected()) {
                    Logger.warning(TAG, "Camera disconnected. Canceling all downloads.");
                    cancelAllDownloads();
                    return;
                }


                final ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RECEIVER);
                final int downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1);
                initNotifyDownloadId(downloadId);

                Runnable downloadAction = () -> {
                    if(BuildConfig.DEBUG) {
                        Logger.debug(TAG, "Download start: " + downloadId);
                    }

                    handleActionDownload(downloadId, receiver);

                    if(BuildConfig.DEBUG) {
                        Logger.debug(TAG, "Download end: " + downloadId);
                    }
                };

                createDownloadExecutor();

                if (downloadExecutor != null) {

                    if (Logger.DEBUG) Logger.debug(TAG, "Executing parallel download: " + downloadId);

                    downloadExecutor.execute(downloadAction);
                    synchronized(downloadSynchronizer) {
                        if (Logger.DEBUG) Logger.debug(TAG, "Waiting for next parallel download");
                        try { downloadSynchronizer.wait(); } catch (InterruptedException ignored) {}
                        if (Logger.DEBUG) Logger.debug(TAG, "Done waiting for next parallel download");
                    }
                } else {
                    if (Logger.DEBUG) Logger.debug(TAG, "Executing serial download: " + downloadId);
                    downloadAction.run();
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
        destroyDownloadExecutor();
        this.stopForeground(true);
        super.onDestroy();
        if(BuildConfig.DEBUG) Logger.debug(TAG,  "onDestroy");
    }

    private synchronized void createDownloadExecutor() {
        int parallelDownloadPercentage = camera.getPreferences().getParallelDownloadPercentage();
        if (downloadExecutor == null && parallelDownloadPercentage > 0) {
            downloadExecutor = Executors.newFixedThreadPool(parallelDownloadPercentage <= 10 ? 4 : 2);
            if (Logger.DEBUG) Logger.debug(TAG, "DownloadExecutor created");
        } else if (downloadExecutor != null && parallelDownloadPercentage <= 0) {
            destroyDownloadExecutor();
        }
    }

    private synchronized void destroyDownloadExecutor() {
        if (downloadExecutor != null) {
            downloadExecutor.shutdown();
            downloadExecutor = null;
            if (Logger.DEBUG) Logger.debug(TAG, "DownloadExecutor destroyed");
        }
    }

    private void handleActionDownload(int downloadId, ResultReceiver receiver) {

        Bundle resultData = new Bundle();

        if (downloadErrorCount >= ALLOWED_CONSECUTIVE_ERRORS) {
            resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
            resultData.putInt(EXTRA_DOWNLOAD_STATUS, DOWNLOAD_STATUS_TOO_MANY_ERRORS);
            receiver.send(DOWNLOAD_FINISHED, resultData);
            downloadErrorCount = 0;
            notifyDownloadSynchronizer();
            return;
        }

        DownloadEntry downloadEntry = Queue.findDownloadEntry(downloadId);
        if (downloadEntry == null) {
            if (BuildConfig.DEBUG) {
                Logger.debug(TAG, "No download with id " + downloadId + " found");
            }
            notifyDownloadSynchronizer();
            return;
        }

        boolean canceled = false;
        int status;
        int fileLength = 0;
        boolean isNewMedia = true;
        String statusMessage = "";

        if (getNotifyDownloadId() == downloadId) {
            resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
            resultData.putInt(EXTRA_PROGRESS, 0);
            receiver.send(UPDATE_PROGRESS, resultData);
        }

        Uri mediaStoreUri = null;

        Context context = MyApplication.ApplicationContext;
        ContentResolver cr = context.getContentResolver();
        try {
            cancelDownload(-1);

            ImageData imageData = downloadEntry.getImageData();

            isNewMedia = imageData.getLocalStorageUri() == null;
            mediaStoreUri = insertOrUpdateMediaStoreImage(cr, imageData);
            if (mediaStoreUri == null) {
                status = DOWNLOAD_STATUS_ERROR;
                Logger.warning(TAG, "Failed to insert image in gallery " + imageData.fileName);
                notifyDownloadSynchronizer();
            } else {
                //create downloadUrl and connect
                URL downloadUrl = new URL(imageData.getDownloadUrl());
                URLConnection connection = downloadUrl.openConnection();

                connection.setConnectTimeout(camera.getPreferences().getConnectTimeout() / 2);
                connection.connect();

                // this will be useful so that you can show a typical 0-100% progress bar
                fileLength = connection.getContentLength();

                // downloadDown the file
                InputStream input = new BufferedInputStream(connection.getInputStream());
                OutputStream output = cr.openOutputStream(mediaStoreUri);

                byte[] data = new byte[1024];
                long total = 0;
                int lastProgress = 0;
                int count;
                try {
                    int parallelDownloadPercentage = camera.getPreferences().getParallelDownloadPercentage();
                    while ((count = input.read(data)) != -1) {

                        if (shouldCancelId == downloadId) {
                            canceled = true;
                            notifyDownloadSynchronizer();
                            break;
                        }

                        total += count;
                        output.write(data, 0, count);

                        int progress = (int) (total * 100 / fileLength);
                        if (progress > lastProgress) {
                            initNotifyDownloadId(downloadId);

                            if (getNotifyDownloadId() == downloadId) {
                                // publishing the progress....
                                resultData = new Bundle();
                                resultData.putInt(EXTRA_PROGRESS, progress);
                                resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
                                receiver.send(UPDATE_PROGRESS, resultData);
                            }

                            lastProgress = progress;
                            if (parallelDownloadPercentage > 0 && progress == parallelDownloadPercentage) {
                                notifyDownloadSynchronizer();
                            }
                        }
                    }

                    ContentValues values = new ContentValues();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    }
                    values.put(MediaStore.Images.Media.SIZE, fileLength);
                    cr.update(mediaStoreUri, values, null, null);

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

                if (camera.getPreferences().isUpdatePictureLocationEnabled()) {
                    updateLocationInfo(cr, mediaStoreUri, imageData);
                }
            }
        } catch (Exception e) {
            Logger.warning(TAG, "Error downloading file", e);
            status = DOWNLOAD_STATUS_ERROR;
            statusMessage = e.getLocalizedMessage();
            downloadErrorCount++;
            notifyDownloadSynchronizer();
        }

        if (isNewMedia && status != DOWNLOAD_STATUS_SUCCESS && mediaStoreUri != null) {
            try {
                cr.delete(mediaStoreUri, null, null);
            } catch (Exception ignored) {

            }
            mediaStoreUri = null;
        }

        resultData = new Bundle();
        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
        resultData.putInt(EXTRA_DOWNLOAD_STATUS, status);
        resultData.putString(EXTRA_DOWNLOAD_STATUS_MESSAGE, statusMessage);
        if (mediaStoreUri != null) {
            resultData.putString(EXTRA_LOCAL_URI, mediaStoreUri.toString());
        }
        receiver.send(DOWNLOAD_FINISHED, resultData);
        setNotifyDownloadId(-1);
    }

    private void notifyDownloadSynchronizer() {
        if (downloadExecutor != null) {
            synchronized (downloadSynchronizer) {
                downloadSynchronizer.notifyAll();
            }
        }
    }

    private synchronized int getNotifyDownloadId() {
        return mNotifyDownloadId;
    }

    private synchronized void setNotifyDownloadId(int notifyDownloadId) {
        mNotifyDownloadId = notifyDownloadId;
    }

    private synchronized void initNotifyDownloadId(int notifyDownloadId) {
        if (mNotifyDownloadId < 0) {
            mNotifyDownloadId = notifyDownloadId;
        }
    }

    private static final float[] ll = new float[2];
    private void updateLocationInfo(ContentResolver cr, Uri mediaStoreUri, ImageData imageData) {
        long time = camera.getImageInfo(imageData).getTime();
        LocationTable.LatLong latLong = LocationService.getLocationAtTime(time);
        if (latLong != null) {
            if (Logger.DEBUG) Logger.debug(TAG, "Found location info for image: " + latLong.latitude + "," + latLong.longitude);
            try {
                try (ParcelFileDescriptor fd = cr.openFileDescriptor(mediaStoreUri, "rw")) {
                    ExifInterface ei = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                                         ? new ExifInterface(fd.getFileDescriptor())
                                         : new ExifInterface(imageData.getLocalFilePath());

                    if (!ei.getLatLong(ll)) {
                        if (Logger.DEBUG) Logger.debug(TAG, "Updating exif location info");
                        ei.setAttribute(ExifInterface.TAG_GPS_LATITUDE, latLong.getLatGeoCoordinates());
                        ei.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latLong.getLatRef());
                        ei.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, latLong.getLongGeoCoordinates());
                        ei.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, latLong.getLongRef());
                        ei.saveAttributes();
                        if (Logger.DEBUG) Logger.debug(TAG, "Exif location info updated");
                    } else {
                        if (Logger.DEBUG) Logger.debug(TAG, "Exif location info already present in image");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Nullable
    private Uri insertOrUpdateMediaStoreImage(ContentResolver cr, ImageData imageData) {
        StorageManager storageManager = (StorageManager) getSystemService(Context.STORAGE_SERVICE);

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, imageData.uniqueFileName);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, imageData.uniqueFileName);
        values.put(MediaStore.Images.Media.DESCRIPTION, imageData.uniqueFileName);
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, imageData.isRaw ? "image/x-adobe-dng" : "image/jpeg");

        ImageMetaData imageMetaData = camera.getImageInfo(imageData);
        if (imageMetaData != null) {
            values.put(MediaStore.Images.Media.ORIENTATION, imageMetaData.orientationDegrees);
            long imageTime = imageMetaData.getTime();
            if (imageTime > 0) {
                values.put(MediaStore.Images.Media.DATE_TAKEN, imageTime);
            }
        }

        Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            File localPath = camera.getImageLocalPath(imageData);
            values.put(MediaStore.MediaColumns.DATA, localPath.getAbsolutePath());
            localPath.getParentFile().mkdirs();
            imageData.setLocalFilePath(localPath.getAbsolutePath());
        } else {
            CameraPreferences camPrefs = camera.getPreferences();
            String downloadVolume = camPrefs.getDownloadVolume();
            if (downloadVolume != null && !downloadVolume.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY)) {
                try {
                    Uri uri = MediaStore.Images.Media.getContentUri(downloadVolume);
                    StorageVolume sv = storageManager.getStorageVolume(uri);
                    if (Environment.MEDIA_MOUNTED.equals(sv.getState())) {
                        contentUri = uri;
                    }
                } catch (Exception e) {
                    camPrefs.setDownloadVolume(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                    camPrefs.save();
                    Logger.warning(TAG, e.getLocalizedMessage());
                }
            }
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, camera.getImageRelativePath(imageData));
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        Uri localStorageUri = imageData.getLocalStorageUri();
        if (localStorageUri != null) {
            cr.update(localStorageUri, values, null, null);
            if (Logger.DEBUG) Logger.debug(TAG, "Updated existing content uri: " + localStorageUri);
        } else {
            localStorageUri = cr.insert(contentUri, values);
            if (Logger.DEBUG) Logger.debug(TAG, "Create new content uri: " + localStorageUri);
        }

        return localStorageUri;
    }

    private static final String[] sWriteExternalStoragePermissions =  new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };


    private static long lastNoPermissionToast = 0;
    public static boolean hasWriteExternalStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        Context ctx = MyApplication.ApplicationContext;
        for (String permission : sWriteExternalStoragePermissions) {
                if (ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                    if (Logger.DEBUG) Logger.debug(TAG, "Missing write external storage permission permission.");
                    if (SystemClock.elapsedRealtime() - lastNoPermissionToast > 5000) {
                        TaskExecutor.executeOnUIThread(() -> Toast.makeText(ctx, R.string.grand_external_storage_permission_label, Toast.LENGTH_LONG).show());
                        lastNoPermissionToast = SystemClock.elapsedRealtime();
                    }
                    return false;
                }
        }

        return true;
    }

    public static void requestWriteExternalStoragePermissions(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            activity.requestPermissions(sWriteExternalStoragePermissions, requestCode);
        }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
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
    
    public static DownloadEntry addDownloadQueue(ImageData imageData, boolean canPowerOff) {
       return Queue.addDownloadQueue(imageData, Queue.inBatchDownload, canPowerOff);
    }
    
    public static void saveQueueToFile(CameraData cameraData) {
        Queue.saveToFile(cameraData);
    }
    
    public static int processDownloadQueue() {
        return Queue.processDownloadQueue(true);
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

    public static int getQueueSize() {
        return Queue.size();
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
        private static int sLastRemainingSeconds;

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

        private static void doDownloadFinished(ImageData imageData, DownloadEntry downloadEntry, boolean wasCanceled) {

            int downloadEntryId = downloadEntry != null ? downloadEntry.getDownloadId() : -1;
            if(onDownloadFinishedListener != null) {
                onDownloadFinishedListener.onDownloadFinished(imageData, downloadEntryId, sDownloadQueue.size(),
                        Queue.downloadCount, Queue.errorCount, wasCanceled);
            }

            if(sDownloadQueue.size() == 0) {
                inBatchDownload = false;
                if(sWackeLock != null) {
                    sWackeLock.release();
                    sWackeLock = null;
                    if(BuildConfig.DEBUG) Logger.debug(TAG, "WakeLock released");
                }

                if (downloadEntry != null && downloadEntry.canPowerOff && !wasCanceled && !CameraFragment.isOpened()) {
                    Camera.instance.disconnectIfInPowerOfTransfer();
                    if (sShutCameraDownWhenDone) {
                        Camera.instance.powerOff();
                    }
                }

                downloadNotification(null, downloadEntryId > 0 ? 0 : -1);
                sStarDownloadTime = -1;
            }
        }

        private static synchronized void cancelAll() {
            cancelCurrentDownload();

            DownloadEntry downloadEntry = null;
            for(DownloadEntry de : sDownloadQueue) {
                downloadEntry = de;
                downloadEntry.getImageData().setIsInDownloadQueue(false);
            }
            sDownloadQueue.clear();
            if(sDownloadQueueDict != null) {
                sDownloadQueueDict.clear();
            }

            final DownloadEntry de = downloadEntry;
            TaskExecutor.executeOnUIThread(() -> doDownloadFinished(null, de, true));
        }

        /*private*/ static void remove(DownloadEntry downloadEntry, boolean canceled) {
            sDownloadQueue.remove(downloadEntry);
            if(sDownloadQueueDict != null && downloadEntry.getDownloadId() > 0) {
                sDownloadQueueDict.remove(downloadEntry.getDownloadId());
            }
            downloadEntry.getImageData().setIsInDownloadQueue(false);
            doDownloadFinished(downloadEntry.mImageData, downloadEntry, canceled);
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
                    int totalTime = Math.round((SystemClock.elapsedRealtime() - sStarDownloadTime) / 1000F);
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

        public static int size() {
            return sDownloadQueue.size();
        }

        private static String getETAString(float remainingDownloads) {
            String etaText = null;
            long elapsedRealTime = SystemClock.elapsedRealtime();
            if (sStarDownloadTime > 0 && elapsedRealTime - sLastEtaUpdate > (sLastRemainingSeconds <= 30 ? 1000 : 27500)) {
                int downloaded = Queue.downloadCount;
                long elapsedDownloadTime = (elapsedRealTime - sStarDownloadTime) / 1000;
                float downloadsPerSecond = (float)downloaded / (float)elapsedDownloadTime;
                if (downloadsPerSecond > 0) {
                    sLastEtaUpdate = elapsedRealTime;
                    int remainingSeconds = Math.round(remainingDownloads / downloadsPerSecond);
                    if (remainingSeconds < sLastRemainingSeconds) {
                        sLastRemainingSeconds = remainingSeconds;
                    }
                    int remainingMinutes = Math.round(sLastRemainingSeconds / 60F);
                    etaText =  "ETA: ";
                    if (remainingMinutes == 0 || sLastRemainingSeconds <= 30) {
                        etaText += sLastRemainingSeconds <= 30 ? sLastRemainingSeconds + "s" : " < 1m";
                    } else {
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
                String json = Utils.readTextFile(new File(cameraData.getCameraFilesDirectory(), FILE_NAME_DOWNLOAD_QUEUE));
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
                Utils.saveTextFile(new File(cameraData.getCameraFilesDirectory(), FILE_NAME_DOWNLOAD_QUEUE), jsonArray.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public static DownloadEntry getNextDownload() {
            return findDownloadEntry(0);
        }

        /*private*/ static void download(DownloadEntry downloadEntry) {
            if (!hasWriteExternalStoragePermission()) {
                return;
            }

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

        public static DownloadEntry addDownloadQueue(ImageData imageData, boolean atTheBeginning, boolean canPowerOff) {
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

                downloadEntry.canPowerOff = canPowerOff;
                download(downloadEntry);
                return downloadEntry;
            }
            return null;
        }

        public static int processDownloadQueue(boolean all) {
            if (!hasWriteExternalStoragePermission()) {
                return -1;
            }

            int count = 0;
            if (all || !isDownloading()) {
                DownloadEntry downloadEntry = null;
                sStarDownloadTime = SystemClock.elapsedRealtime();
                sLastEtatext = "";
                sLastEtaUpdate = sStarDownloadTime;
                sLastRemainingSeconds = Integer.MAX_VALUE;
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
        public boolean canPowerOff;
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
