package com.hmsoft.pentaxgallery.ui.preferences;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.service.DownloadService;
import com.hmsoft.pentaxgallery.service.LocationService;

import java.util.List;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class PreferencesActivity extends AppCompatActivity implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        FragmentManager.OnBackStackChangedListener {

    public static final int REQUEST_CODE = 0;
    public static final int RESULT_OK = RESULT_FIRST_USER + 1;
    public static final int RESULT_UPDATE_CAMERA_LIST = RESULT_FIRST_USER + 2;

    /*package*/ final PreferencesFragment preferencesFragment = new PreferencesFragment();

    public static void start(Activity activity) {
        Intent i = new Intent(activity, PreferencesActivity.class);
        activity.startActivityForResult(i, REQUEST_CODE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, preferencesFragment)
                .commit();

        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            // Hide title text and set home as up
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
        }


        getSupportFragmentManager().addOnBackStackChangedListener(this);
        setTitle(R.string.setting_title);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());


        setTitle(pref.getTitle());

        args.putString("key", pref.getKey());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);
        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();

        return true;
    }

    @Override
    public void onBackStackChanged() {
        List<Fragment>  fragments = getSupportFragmentManager().getFragments();
        if(fragments.size() == 1 && fragments.get(0) instanceof PreferencesFragment) {
            setTitle(R.string.setting_title);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        DownloadService.setShutCameraDownWhenDone(Camera.instance.getPreferences().shutdownAfterTransfer());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                List<Fragment>  fragments = getSupportFragmentManager().getFragments();
                if(fragments.size() == 1 && fragments.get(0) instanceof PreferencesFragment) {
                    NavUtils.navigateUpFromSameTask(this);
                } else {
                    getSupportFragmentManager().popBackStack();
                }
                return true;
        }
        return false;
    }
}
