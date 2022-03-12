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
import android.net.Uri;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.service.LocationService;
import com.hmsoft.pentaxgallery.ui.preferences.PreferencesActivity;
import com.hmsoft.pentaxgallery.util.Logger;

import java.util.List;
import java.util.Set;

/**
 * Simple FragmentActivity to hold the main {@link ImageGridFragment} and not much else.
 */
public class ImageGridActivity extends AppCompatActivity {
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
        LocationService.start(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Fragment f = getSupportFragmentManager().findFragmentByTag(TAG);
        if (!(f instanceof ImageGridFragment)) {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            fragment = new ImageGridFragment();
            ft.add(android.R.id.content, fragment, TAG);
            //ft.add(android.R.id.content, new ImageGalleryFragment(), TAG);
            ft.commit();
        } else {
            fragment = (ImageGridFragment)f;
        }

        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
        }

        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        if(rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (resultCode) {
            case PreferencesActivity.RESULT_UPDATE_CAMERA_LIST:
            case PreferencesActivity.RESULT_OK:
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (fragment != null) {
            fragment.requestPermissionsResult(requestCode, permissions, grantResults);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (super.onKeyDown(keyCode, event)) {
            return true;
        }

        if(fragment == null) {
            return false;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_CAMERA:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                fragment.shoot();
                return true;
        }

        return false;
    }
}
