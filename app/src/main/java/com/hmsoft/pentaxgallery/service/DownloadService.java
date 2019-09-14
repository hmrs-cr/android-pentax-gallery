package com.hmsoft.pentaxgallery.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.data.provider.DownloadQueue;
import com.hmsoft.pentaxgallery.util.Logger;

import java.io.BufferedInputStream;
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

    public static final int DOWNLOAD_FINISHED = 1000;
    public static final int UPDATE_PROGRESS = 1001;

    private static final String ACTION_DOWNLOAD = "com.hmsoft.pentaxgallery.service.action.DOWNLOAD";
    public static final String EXTRA_PROGRESS = "com.hmsoft.pentaxgallery.service.extra.PROGRESS";;
    public static final String EXTRA_DOWNLOAD_ID = "com.hmsoft.pentaxgallery.service.extra.DOWNLOAD_ID";

    private static int downloadId = 0;

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
        try {

            //create url and connect
            URL url = new URL(downloadUrl);
            URLConnection connection = url.openConnection();
            connection.connect();

            // this will be useful so that you can show a typical 0-100% progress bar
            int fileLength = connection.getContentLength();

            // download the file
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(destination);

            byte data[] = new byte[1024];
            long total = 0;
            int lastProgress = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;

                int progress = (int) (total * 100 / fileLength);
                if(progress > lastProgress) {
                    // publishing the progress....
                    Bundle resultData = new Bundle();
                    resultData.putInt(EXTRA_PROGRESS, progress);
                    resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
                    receiver.send(UPDATE_PROGRESS, resultData);
                    lastProgress = progress;
                }

                output.write(data, 0, count);
            }

            // close streams
            output.flush();
            output.close();
            input.close();

        } catch (IOException e) {
           Logger.warning(TAG,"Error downloading file", e);
        }

        Bundle resultData = new Bundle();
        resultData.putInt(EXTRA_DOWNLOAD_ID, downloadId);
        receiver.send(DOWNLOAD_FINISHED, resultData);
    }
}
