package com.hmsoft.pentaxgallery.ui.camera;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";

    private CameraFragment  fragment;

    public static void start(FragmentActivity activity) {
        Intent i = new Intent(activity, CameraActivity.class);
        activity.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getSupportFragmentManager().findFragmentByTag(TAG) == null) {
            final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            fragment = new CameraFragment();
            ft.add(android.R.id.content, fragment, TAG);
            ft.commit();
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }
}
