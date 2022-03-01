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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

import androidx.preference.PreferenceManager;

import com.hmsoft.pentaxgallery.service.DownloadService;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

public class MyApplication extends Application {

    private static final String TAG = "MyApplication";
    public static final String NOTIFICATION_CHANNEL_ID = "PentaxGalleryChannel";

    public static MyApplication ApplicationContext = null;

    public static PowerManager.WakeLock acquireWakeLock() {
        PowerManager powerManager = (PowerManager)ApplicationContext.getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "MyApp::MyWakelockTag");
        wakeLock.acquire(15 * 60000);

        if(BuildConfig.DEBUG) Logger.debug(TAG, "WakeLock acquired");

        return wakeLock;
    }

    private static void createNotificationChannel() {
        Context context = MyApplication.ApplicationContext;
        String name = context.getString(R.string.download_notification_channel_name);
        String description = context.getString(R.string.download_notification_channel_desc);
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        channel.setSound(null,null);
        channel.enableLights(false);
        channel.enableVibration(false);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = MyApplication.ApplicationContext.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    public static boolean getBooleanPref(int key, int defValueKey) {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationContext).getBoolean(ApplicationContext.getString(key), Boolean.toString(true).equals(ApplicationContext.getString(defValueKey)));
    }

    public static int getIntPref(int key, int defValueKey) {
        return Integer.parseInt(getStringPref(key, defValueKey));
    }

    public static String getStringPref(int key, int defValueKey) {
        return PreferenceManager.getDefaultSharedPreferences(ApplicationContext).getString(ApplicationContext.getString(key), ApplicationContext.getString(defValueKey));
    }

    @Override
    public void onCreate() {
        ApplicationContext = this;
        CrashCatcher.init();
        TaskExecutor.init();
        createNotificationChannel();
        super.onCreate();
    }   
}
