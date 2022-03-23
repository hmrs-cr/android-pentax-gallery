package com.hmsoft.pentaxgallery.ui.preferences;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.service.LocationService;
import com.hmsoft.pentaxgallery.service.StartLocationServiceReceiver;
import com.hmsoft.pentaxgallery.util.Utils;

import java.util.List;

public class PreferencesFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 6;
    private final Camera camera = Camera.instance;
    private PreferenceCategory mPrefCategoryLocationService;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPrefCategoryLocationService = findPreference(getString(R.string.key_location_service_category));
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        addCameraList();

        SwitchPreferenceCompat enableLocationPreference = findPreference(getString(R.string.key_enable_location_service));
        enableLocationPreference.setChecked(enableLocationPreference.isChecked() && LocationService.hasLocationPermission());

        Preference aboutPreference = findPreference(getString(R.string.key_about));
        aboutPreference.setSummary(Utils.VERSION_STRING);
        aboutPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showAboutDialog();
                return true;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        SwitchPreferenceCompat p = mPrefCategoryLocationService.findPreference(getString(R.string.key_enable_location_service));
        Preference intervalPref = mPrefCategoryLocationService.findPreference(getString(R.string.key_location_update_interval));
        Preference startAtBootPref = mPrefCategoryLocationService.findPreference(getString(R.string.key_enable_location_service_at_boot));
        intervalPref.setEnabled(p.isChecked());
        startAtBootPref.setEnabled(p.isChecked());
    }

    @Override
    public void onPause() {
        LocationService.updateConfig(getContext());
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    /*private*/ void addCameraList() {
        List<CameraData> cameras = camera.getRegisteredCameras();
        if(cameras != null && cameras.size() > 0) {
            addCameraList(cameras);
        } else {
            new LoadCameraListTask().execute();
        }
    }

    @SuppressLint("DefaultLocale")
    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert);

        String message = String.format(getString(R.string.about_line),
                getString(R.string.app_name), getString(R.string.intro_message), Utils.VERSION_STRING);

        if(Camera.instance.isConnected()) {
            CameraData cameraData = Camera.instance.getCameraData();
            message += String.format(getString(R.string.about_connected_to),
                    cameraData.model, cameraData.serialNo, cameraData.battery, "%");
        }

        builder.setTitle(R.string.settings)
                .setTitle(R.string.about_label)
                .setMessage(Html.fromHtml(message))
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(R.mipmap.ic_launcher)
                .show();
    }

    private void addCameraList(List<CameraData> cameras) {
        Context context = getPreferenceManager().getContext();
        PreferenceCategory preferenceCategory = findPreference(getString(R.string.key_cameras_category));
        preferenceCategory.removeAll();
        for (CameraData camera : cameras) {
            Preference preference = new Preference(context);
            preference.setTitle(camera.getDisplayName());
            preference.setKey(camera.key);
            preference.setSummary(getString(R.string.serial) + camera.serialNo);
            preference.setFragment(CameraPreferenceFragment.class.getName());
            preferenceCategory.addPreference(preference);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getString(R.string.key_enable_location_service).equals(key)) {
            SwitchPreferenceCompat p = mPrefCategoryLocationService.findPreference(key);
            boolean hasLocationPermission = LocationService.hasLocationPermission();
            if (p.isChecked() && hasLocationPermission) {
                StartLocationServiceReceiver.enable(getContext());
            } else {
                StartLocationServiceReceiver.disable(getContext());
                if (!hasLocationPermission) {
                    LocationService.requestLocationPermissions(getActivity(), LOCATION_PERMISSION_REQUEST_CODE);
                    setEnableLocation(p, false);
                }
            }
            Preference intervalPref = mPrefCategoryLocationService.findPreference(getString(R.string.key_location_update_interval));
            Preference startAtBootPref = mPrefCategoryLocationService.findPreference(getString(R.string.key_enable_location_service_at_boot));
            intervalPref.setEnabled(p.isChecked());
            startAtBootPref.setEnabled(p.isChecked());
        }
    }

    void requestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (LOCATION_PERMISSION_REQUEST_CODE == requestCode && grantResults.length == 1) {
            SwitchPreferenceCompat p = mPrefCategoryLocationService.findPreference(getString(R.string.key_enable_location_service));
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setEnableLocation(p, true);
                StartLocationServiceReceiver.enable(getContext());
            } else {
                StartLocationServiceReceiver.disable(getContext());
                setEnableLocation(p, false);
            }
        }
    }

    private void setEnableLocation(SwitchPreferenceCompat p, boolean enabled) {
        p.setChecked(enabled);
        getPreferenceScreen().getSharedPreferences().edit().putBoolean(p.getKey(), enabled).apply();
    }

    private class LoadCameraListTask extends AsyncTask<Object, Object, List<CameraData>> {

        @Override
        protected List<CameraData> doInBackground(Object... objects) {
            camera.loadCameraList();
            return camera.getRegisteredCameras();
        }

        @Override
        protected void onPostExecute(List<CameraData> cameraData) {
            addCameraList(cameraData);
        }
    }
}
