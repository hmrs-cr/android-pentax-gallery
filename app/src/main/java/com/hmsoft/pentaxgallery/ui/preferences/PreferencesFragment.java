package com.hmsoft.pentaxgallery.ui.preferences;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.util.Utils;

import java.util.List;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

public class PreferencesFragment extends PreferenceFragmentCompat {

    private Camera camera = Camera.instance;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
        addCameraList();

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

        String message = String.format("<center><br/><p><b>%s</b> by hmrs.cr.</p><p>%s</p><p><i>%s</i></p></center>",
                getString(R.string.app_name), getString(R.string.intro_message), Utils.VERSION_STRING);

        if(Camera.instance.isConnected()) {
            CameraData cameraData = Camera.instance.getCameraData();
            message += String.format("<p><b>Connected to %s (%s).</b> Battery: %d%s.</p>",
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
            preference.setSummary("Serial #" + camera.serialNo);
            preference.setFragment(CameraPreferenceFragment.class.getName());
            preferenceCategory.addPreference(preference);
        }
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
