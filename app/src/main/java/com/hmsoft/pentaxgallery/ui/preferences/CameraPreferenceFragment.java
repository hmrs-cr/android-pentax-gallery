package com.hmsoft.pentaxgallery.ui.preferences;


import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.CameraPreferences;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * A simple {@link Fragment} subclass.
 */
public class CameraPreferenceFragment extends PreferenceFragmentCompat {

    EditTextPreference.OnBindEditTextListener numberEditTextListener =  new EditTextPreference.OnBindEditTextListener() {
        @Override
        public void onBindEditText(@NonNull EditText editText) {
            editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        }
    };


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Bundle args = getArguments();
        String cameraKey = args.getString("key");
        List<CameraData> cameras =  Camera.instance.getRegisteredCameras();

        CameraData cameraData = null;
        for (CameraData camera : cameras) {
            if(camera.key.equals(cameraKey)) {
                cameraData = camera;
                getPreferenceManager().setPreferenceDataStore(camera.preferences);
                break;
            }
        }
        setPreferencesFromResource(R.xml.camera_preferences, rootKey);

        ((EditTextPreference)findPreference(getString(R.string.key_connect_timeout))).setOnBindEditTextListener(numberEditTextListener);
        ((EditTextPreference)findPreference(getString(R.string.key_read_timeout))).setOnBindEditTextListener(numberEditTextListener);
        ((EditTextPreference)findPreference(getString(R.string.key_camera_thread_number))).setOnBindEditTextListener(numberEditTextListener);

        final CameraData camera = cameraData;
        findPreference(getString(R.string.key_remove_camera)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.remove_camera_label)
                        .setMessage(String.format(getString(R.string.remove_camera_confirmation), camera.getDisplayName()))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog, int whichButton) {

                            }})
                        .setNegativeButton(android.R.string.no, null).show();
                return false;
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        final CameraPreferences preferenceDataStore = (CameraPreferences)getPreferenceManager().getPreferenceDataStore();
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                preferenceDataStore.save();
            }
        });
    }
}
