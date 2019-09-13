package com.hmsoft.pentaxgallery.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class BatchDownloadService extends Service {

    public BatchDownloadService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
