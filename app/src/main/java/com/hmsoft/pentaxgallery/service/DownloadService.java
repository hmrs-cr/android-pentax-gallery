package com.hmsoft.pentaxgallery.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.data.provider.DownloadQueue;
import com.hmsoft.pentaxgallery.util.DefaultSettings;
import com.hmsoft.pentaxgallery.util.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

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
    public static final String EXTRA_PROGRESS = "com.hmsoft.pentaxgallery.service.extra.PROGRESS";;
    public static final String EXTRA_DOWNLOAD_ID = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_ID";
    public static final String EXTRA_DOWNLOAD_STATUS = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_STATUS";
    public static final String EXTRA_DOWNLOAD_STATUS_MESSAGE = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_STATUS_MESSAGE";

    private static int downloadId = 0;

    private static int errorCount = 0;

    private static final String EXTRA_URL = "com.hmsoft.pentaxgallery.service.extra.URL";
    private static final String EXTRA_DESTINATION_PATH = "com.hmsoft.pentaxgallery.service.extra.DESTINATION_PATH";

    public DownloadService() {
        super("DownloadService");
    }

    public static int download(Context context, String url, String destinationPath) {
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_DOWNLOAD);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_DESTINATION_PATH, destinationPath);
        intent.putExtra("receiver", DownloadQueue.DownloadResultReceiver);
        intent.putExtra(EXTRA_DOWNLOAD_ID, ++downloadId);
        context.startService(intent);
        if(BuildConfig.DEBUG) Logger.debug(TAG, "Download started: " + downloadId);
        return downloadId;
    }

    public static void cancelCurrentDownload() {
        cancelDownload(0);
    }

    public synchronized static void cancelDownload(int downloadId) {
        shouldCancelId = downloadId;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_DOWNLOAD.equals(action)) {
                final String url = intent.getStringExtra(EXTRA_URL);
                final String destination = intent.getStringExtra(EXTRA_DESTINATION_PATH);
                final int downloadId = intent.getIntExtra(EXTRA_DOWNLOAD_ID, -1);
                ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");
                handleActionDownload(url, destination, downloadId, receiver);
            }
        }
    }

    private void handleActionDownload(String downloadUrl, String destination, int downloadId, ResultReceiver receiver) {
        boolean canceled = false;
        int status;
        String statusMessage = "";
        int fileLength = 0;

        Bundle resultData = new Bundle();
        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
      
        if(errorCount >= 3) {            
            resultData.putInt(EXTRA_DOWNLOAD_STATUS, DOWNLOAD_STATUS_TOO_MANY_ERRORS);
            receiver.send(DOWNLOAD_FINISHED, resultData);
            errorCount = 0;
            return;
        }

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

            // download the file
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(destination);

            byte data[] = new byte[1024];
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

                if(canceled) {
                    status = DOWNLOAD_STATUS_CANCELED;
                }
            }

        } catch (IOException e) {
           Logger.error(TAG,"Error downloading file", e);
           status = DOWNLOAD_STATUS_ERROR;
           statusMessage = e.getLocalizedMessage();
           errorCount++;
        }

        File downloadFile = new File(destination);
        if(BuildConfig.DEBUG) Logger.debug(TAG, "Camera Size:" + fileLength + " - Downloaded size: " + downloadFile.length());

        if(status == DOWNLOAD_STATUS_SUCCESS && fileLength != downloadFile.length()) {
            status = DOWNLOAD_STATUS_ERROR;
            statusMessage = "Corrupted file.";
        }

        if (status != DOWNLOAD_STATUS_SUCCESS) {
            downloadFile.delete();
        }
        
        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
        resultData.putInt(EXTRA_DOWNLOAD_STATUS, status);
        resultData.putString(EXTRA_DOWNLOAD_STATUS_MESSAGE, statusMessage);
        receiver.send(DOWNLOAD_FINISHED, resultData);
    }
}
