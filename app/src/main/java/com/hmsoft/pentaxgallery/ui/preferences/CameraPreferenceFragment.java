package com.hmsoft.pentaxgallery.ui.preferences;


import android.os.Bundle;

import com.hmsoft.pentaxgallery.R;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;

/**
 * A simple {@link Fragment} subclass.
 */
public class CameraPreferenceFragment extends PreferenceFragmentCompat {


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.camera_preferences, rootKey);
    }

}
