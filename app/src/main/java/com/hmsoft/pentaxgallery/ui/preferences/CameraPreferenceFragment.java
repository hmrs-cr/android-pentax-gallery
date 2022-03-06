package com.hmsoft.pentaxgallery.ui.preferences;


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Html;
import android.text.InputType;
import android.text.format.Formatter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.CameraPreferences;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * A simple {@link Fragment} subclass.
 */
public class CameraPreferenceFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener {

    private CameraData cameraData;

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
        CameraPreferences cameraPreferences = null;

        for (CameraData camera : cameras) {
            if(camera.key.equals(cameraKey)) {
                cameraData = camera;
                cameraPreferences = camera.preferences;
                getPreferenceManager().setPreferenceDataStore(cameraPreferences);
                break;
            }
        }
        setPreferencesFromResource(R.xml.camera_preferences, rootKey);

        ((EditTextPreference)findPreference(getString(R.string.key_connect_timeout))).setOnBindEditTextListener(numberEditTextListener);
        ((EditTextPreference)findPreference(getString(R.string.key_read_timeout))).setOnBindEditTextListener(numberEditTextListener);
        ((EditTextPreference)findPreference(getString(R.string.key_camera_thread_number))).setOnBindEditTextListener(numberEditTextListener);

        Preference removeOldImageDataPreference = findPreference(getString(R.string.key_remove_old_images));
        removeOldImageDataPreference.setOnPreferenceClickListener(this);

        ListPreference downloadLocationPreference = findPreference(getString(R.string.key_downloaded_images_location));
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            downloadLocationPreference.setVisible(false);
        } else {
            Set<String> externalVolumes =  MediaStore.getExternalVolumeNames(getContext());
            if (externalVolumes.size() > 1) {
                StorageManager sm = (StorageManager)getContext().getSystemService(Context.STORAGE_SERVICE);
                List<StorageVolume> sv = sm.getStorageVolumes();

                int i = 0;
                String[] volumesDesc = new String[externalVolumes.size()];
                String[] volumes = new String[externalVolumes.size()];
                for (String evn : externalVolumes) {
                    volumesDesc[i] = getVolumeDescription(evn, sv);
                    volumes[i++] = evn;
                }

                downloadLocationPreference.setValue(cameraPreferences.getDownloadVolume());
                downloadLocationPreference.setEntries(volumesDesc);
                downloadLocationPreference.setEntryValues(volumes);
            } else {
                downloadLocationPreference.setVisible(false);
            }
        }

        Preference syncTimePreference = findPreference(getString(R.string.key_sync_camera_time));
        if (cameraData.key.equals(Camera.instance.getCameraData().key) && Camera.instance.isConnected()) {
            syncTimePreference.setEnabled(true);
            syncTimePreference.setOnPreferenceClickListener(this);
        } else {
            syncTimePreference.setEnabled(false);
        }

        new OldImageDataTask(OldImageDataTask.TASK_GET_SIZE).execute(cameraData);

        Preference removeCameraPreference = findPreference(getString(R.string.key_remove_camera));
        if(cameraData.key.equals(Camera.instance.getCameraData().key)) {
            /* Can not remove current camera */
            removeCameraPreference.setVisible(false);
        } else {
            removeCameraPreference.setOnPreferenceClickListener(this);
        }
    }

    private String getVolumeDescription(String volumeName, List<StorageVolume> svs) {
        for (StorageVolume sv : svs) {
            String description = sv.getDescription(getContext());
            if (volumeName.equals(sv.getUuid()) || sv.toString().contains(volumeName) || description.contains(volumeName) ||
                    (volumeName.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY) && sv.isPrimary())) {
                return description;
            }
        }
        return  volumeName;
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            Uri uri = null;
            if (data != null) {
                uri = data.getData();
                Logger.debug("PREf", uri.toString());
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {

        DialogInterface.OnClickListener yesClickListener = null;
        String message = null;
        int title = 0;

        if (preference.getKey().equals(getString(R.string.key_sync_camera_time))) {
            Camera camera = Camera.instance;
            if (camera.isConnected()) {
                camera.getController().updateDateTime(new Date(), response -> {
                    if (response != null && response.success) {
                        new android.app.AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert)
                                .setMessage(R.string.sync_camera_time_success_message)
                                .setNegativeButton(android.R.string.ok, null)
                                .setCancelable(true)
                                .show();
                    } else if (response != null && response.errCode == 412) {
                        new android.app.AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle(R.string.camera_not_in_capture_dialog_title)
                                .setMessage(R.string.camera_not_in_capture_dialog_message)
                                .setPositiveButton(R.string.set_to_capture_mode, (dialog, which) -> {
                                    camera.getController().updateCameraSetting("operationMode", "capture", null);
                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .setCancelable(true)
                                .show();
                    } else {
                        Toast.makeText(this.getContext(), R.string.sync_camera_time_fail_message, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                Toast.makeText(this.getContext(), R.string.camera_not_connected_label, Toast.LENGTH_LONG).show();
            }
        }  else if(preference.getKey().equals(getString(R.string.key_remove_camera))) {
            message = String.format(getString(R.string.remove_camera_confirmation), cameraData.getDisplayName());
            title = R.string.remove_camera_label;
            yesClickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    new DeleteCameraTask().execute(cameraData);
                }
            };
        } else if (preference.getKey().equals(getString(R.string.key_remove_old_images))) {
            message = String.format(getString(R.string.remove_old_image_data_confirmation), cameraData.getDisplayName());
            title = R.string.remove_old_images_label;
            yesClickListener = new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    new OldImageDataTask(OldImageDataTask.TASK_DELETE_OLD).execute(cameraData);
                }
            };
        }

        if(yesClickListener != null) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setMessage(message)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, yesClickListener)
                    .setNegativeButton(android.R.string.no, null).show();

            return true;
        }

        return false;
    }

    private class OldImageDataTask extends AsyncTask<CameraData, Object, Long> {

        static final int TASK_GET_SIZE = 1;
        static final int TASK_DELETE_OLD = 2;

        private final int task;

        OldImageDataTask(int task) {
            this.task = task;
        }

        private long getDeletableSize(boolean delete, CameraData... cameras) {
            long total = 0;
            for(CameraData cameraData : cameras) {
                for(StorageData storageData : cameraData.storages) {
                    File imageDataDir = storageData.getImageDataDirectory();
                    for (File file : imageDataDir.listFiles()) {
                        String name = file.getName();
                        ImageList imageList = storageData.getImageList();
                        int i = imageList != null ? imageList.getDataKeyIndex(name) : -1;
                        if(i < 0) {
                            total += file.length();
                            if(delete) {
                                file.delete();
                            }
                        }
                    }
                }
            }
            return total;
        }

        @Override
        protected Long doInBackground(CameraData... cameras) {
            switch (task) {
                case TASK_GET_SIZE:
                    break;
                case TASK_DELETE_OLD:
                    getDeletableSize(true, cameras);
                    break;
            }
            return getDeletableSize(false, cameras);
        }

        @Override
        protected void onPostExecute(Long result) {
            switch (task) {
                case TASK_GET_SIZE:
                case TASK_DELETE_OLD:
                    Preference removeOldImageDataPreference = findPreference(getString(R.string.key_remove_old_images));
                    removeOldImageDataPreference.setSummary(Formatter.formatFileSize(getContext(), result) + " can be removed");
                    break;
            }
        }
    }

    private class DeleteCameraTask extends AsyncTask<CameraData, Object, Boolean> {

        private void deleteFolderRecursive(File root) {
            if (root.isDirectory())
                for (File child : root.listFiles())
                    deleteFolderRecursive(child);

            root.delete();
        }

        private void deleteCamera(final CameraData camera) {
            File cameraDirectory = camera.getStorageDirectory();
            deleteFolderRecursive(cameraDirectory);
            Camera.instance.loadCameraList();
        }

        @Override
        protected Boolean doInBackground(CameraData... cameraList) {
            for(CameraData cameraData : cameraList) {
                deleteCamera(cameraData);
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            PreferencesActivity preferencesActivity = (PreferencesActivity) getActivity();
            assert preferencesActivity != null;
            preferencesActivity.preferencesFragment.addCameraList();
            preferencesActivity.getSupportFragmentManager().popBackStack();
            preferencesActivity.setResult(PreferencesActivity.RESULT_UPDATE_CAMERA_LIST);
        }
    }
}
