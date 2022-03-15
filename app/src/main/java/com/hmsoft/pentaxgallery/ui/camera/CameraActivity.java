package com.hmsoft.pentaxgallery.ui.camera;

import android.content.Intent;
import android.os.Bundle;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";

    private CameraFragment  fragment;

    public static void start(FragmentActivity activity) {
        if (Camera.instance.isConnected() || BuildConfig.DEBUG) {
            Intent i = new Intent(activity, CameraActivity.class);
            activity.startActivity(i);
        } else {
            Toast.makeText(activity, R.string.camera_not_connected_label, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        fragment = (CameraFragment) getSupportFragmentManager().findFragmentByTag(TAG);
        if (fragment == null) {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            fragment = new CameraFragment();
            ft.add(android.R.id.content, fragment, TAG);
            ft.commit();
        }

        setTitle(Camera.instance.getCameraData().getDisplayName());

        Display display = ((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
        int rotation = display.getRotation();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            final ActionBar actionBar = getSupportActionBar();
            if(actionBar != null) {
                actionBar.hide();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.camera_menu, menu);
        MenuItem drawFocusAreMenuItem = menu.findItem(R.id.drawFocusArea);
        if (drawFocusAreMenuItem != null && fragment != null) {
            drawFocusAreMenuItem.setChecked(CameraFragment.sDrawFocusArea);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(super.onOptionsItemSelected(item)) {
            return true;
        }
        return fragment != null && fragment.onOptionsItemSelected(item);
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
            case KeyEvent.KEYCODE_VOLUME_UP:
                fragment.shoot();
                return true;
        }

        return false;
    }
}
