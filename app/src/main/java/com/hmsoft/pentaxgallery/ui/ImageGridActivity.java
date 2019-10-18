/*
 * Copyright (C) 2012 The Android Open Source Project
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
 *
 * Modified by hmrs.cr
 *
 */

package com.hmsoft.pentaxgallery.ui;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.util.Logger;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

/**
 * Simple FragmentActivity to hold the main {@link ImageGridFragment} and not much else.
 */
public class ImageGridActivity extends FragmentActivity {
    private static final String TAG = "ImageGridActivity";

    public static final String EXTRA_START_DOWNLOADS = "EXTRA_START_DOWNLOADS";
    public static final String EXTRA_SHOW_VIEW = "EXTRA_SHOW_VIEW";
    private static PendingIntent sPendingIntent = null;

    private ImageGridFragment fragment;

    public static void start(Context context, boolean inDownloadView) {
        final Intent i = new Intent(context, ImageGridActivity.class);
        i.putExtra(EXTRA_START_DOWNLOADS, inDownloadView);
        context.startActivity(i);

    }

    public static PendingIntent getPendingIntent() {
        if(sPendingIntent == null) {
            Context context = MyApplication.ApplicationContext;
            final Intent i = new Intent(context, ImageGridActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            sPendingIntent = PendingIntent.getActivity(context, 0, i, 0);
        }
        return sPendingIntent;
    }

    private void handleStartDownloadIntent(Intent intent) {
        if(BuildConfig.DEBUG)  Logger.debug(TAG, "EXTRA_START_DOWNLOADS:" + intent.getBooleanExtra(EXTRA_START_DOWNLOADS, false));
        if(intent.getBooleanExtra(EXTRA_START_DOWNLOADS, false)) {
            fragment.downloadJpgs(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if(BuildConfig.DEBUG) Logger.debug(TAG, "onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        if(BuildConfig.DEBUG) Logger.debug(TAG, "onResume");
        super.onResume();
        handleStartDownloadIntent(getIntent());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getSupportFragmentManager().findFragmentByTag(TAG) == null) {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            fragment = new ImageGridFragment();
            ft.add(android.R.id.content, fragment, TAG);
            //ft.add(android.R.id.content, new ImageGalleryFragment(), TAG);
            ft.commit();
        }


    }
}
