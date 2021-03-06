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

package com.hmsoft.pentaxgallery;

import android.app.Application;
import android.os.PowerManager;

import com.hmsoft.pentaxgallery.service.DownloadService;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";

    public static MyApplication ApplicationContext = null;

    public static PowerManager.WakeLock acquireWakeLock() {
        PowerManager powerManager = (PowerManager)ApplicationContext.getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire(15 * 60000);

        if(BuildConfig.DEBUG) Logger.debug(TAG, "WakeLock acquired");

        return wakeLock;
    }

    @Override
    public void onCreate() {
        ApplicationContext = this;
        CrashCatcher.init();
        TaskExecutor.init();
        DownloadService.createNotificationChannel();
        super.onCreate();
    }   
}
