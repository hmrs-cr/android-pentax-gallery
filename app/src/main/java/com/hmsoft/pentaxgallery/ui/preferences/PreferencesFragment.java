package com.hmsoft.pentaxgallery.ui.preferences;

import android.content.Context;
import android.os.Bundle;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.model.CameraData;

import java.util.List;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

public class PreferencesFragment extends PreferenceFragmentCompat {

    private Camera camera = Camera.instance;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Context context = getPreferenceManager().getContext();

        PreferenceCategory preferenceCategory = findPreference("cameras_category");
        List<CameraData> cameras = camera.getRegisteredCameras();
        for (CameraData camera : cameras) {
            Preference preference = new Preference(context);
            preference.setTitle(camera.getDisplayName());
            preference.setKey(camera.key);
            preference.setSummary(camera.serialNo);
            preference.setFragment(CameraPreferenceFragment.class.getName());
            preferenceCategory.addPreference(preference);
        }

    }
}
