package com.hmsoft.pentaxgallery.camera.model;

import android.content.Context;
import android.os.Build;
import android.provider.MediaStore;

import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceDataStore;

public class CameraPreferences extends PreferenceDataStore {

    public enum MainAction {
        NONE,
        CAMERA,
        DOWNLOAD
    }

    public static final CameraPreferences Default = new CameraPreferences(null);

    private final File settingsFile;
    private final Properties properties;
    private final Context context;

    /*public*/ CameraPreferences(CameraData cameraData) {
        settingsFile = cameraData != null ? new File(cameraData.getStorageDirectory(), "camera.settings") : null;
        properties = new Properties();
        context = MyApplication.ApplicationContext;
    }

    public void save() {
        if (settingsFile != null) {
            try {
                FileOutputStream outputStream = new FileOutputStream(settingsFile);
                properties.store(outputStream, "");
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void load() {
        if (settingsFile != null && settingsFile.exists()) {
            try {
                FileInputStream inputStream = new FileInputStream(settingsFile);
                properties.load(inputStream);
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void putBoolean(String key, boolean value) {
        properties.put(key, Boolean.toString(value));
    }

    public void putBoolean(int key, boolean value) {
        putBoolean(context.getString(key), value);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        return  Boolean.valueOf(properties.getProperty(key, Boolean.toString(defValue)));
    }

    public boolean getBoolean(int key, int defValueKey) {
        return getBoolean(context.getString(key), Boolean.parseBoolean(context.getString(defValueKey)));
    }

    @Override
    public void putString(String key, @Nullable String value) {
        properties.put(key, value);
    }

    public void putString(int key, String value) {
        putString(context.getString(key), value);
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        return properties.getProperty(key, defValue);
    }

    public String getString(int key, int defValueKey) {
        return getString(context.getString(key), context.getString(defValueKey));
    }

    @Override
    public void putInt(String key, int value) {
        properties.put(key, value);
    }

    public void putInt(int key, int value) {
        putInt(context.getString(key), value);
    }

    @Override
    public int getInt(String key, int defValue) {
        return Integer.valueOf(properties.getProperty(key, Integer.toString(defValue)));
    }

    public int getInt(int key, int defValueKey) {
        return getInt(context.getString(key), Integer.parseInt(context.getString(defValueKey)));
    }

    public String getAlbumName() {
        return getString(R.string.key_album_name, R.string.default_album_name);
    }

    public String getRawAlbumName() {
        return getString(R.string.key_raw_album_name, R.string.default_raw_album_name);
    }

    public boolean autoDownloadJpg() {
        return getBoolean(R.string.key_auto_download_jpg, R.string.default_auto_download_jpg);
    }

    public boolean autoDownloadRaw() {
        return getBoolean(R.string.key_auto_download_raw, R.string.default_auto_download_raw);
    }

    public boolean shutdownAfterTransfer() {
        return getBoolean(R.string.key_shutdown_camera_after_transfer, R.string.default_auto_download_raw);
    }

    public boolean loadLocalImageData() {
        return getBoolean(R.string.key_load_local_image_data, R.string.default_load_local_image_data);
    }

    public int getThreadNumber() {
        return getInt(R.string.key_camera_thread_number, R.string.default_camera_thread_number);
    }

    public int getConnectTimeout() {
        return getInt(R.string.key_connect_timeout, R.string.default_connect_timeout);
    }

    public int getReadTimeout() {
        return getInt(R.string.key_read_timeout, R.string.default_read_timeout);
    }

    public MainAction getMainAction() {
        return MainAction.valueOf(getString(R.string.key_main_action, R.string.default_main_action));
    }

    public boolean isStoreLocationInCameraEnabled() {
        return getBoolean(R.string.key_store_location_in_camera, R.string.default_store_location_in_camera);
    }

    public boolean isAutoSyncTime() {
        return getBoolean(R.string.key_auto_sync_camera_time, R.string.default_auto_sync_camera_time);
    }

    public boolean isUpdatePictureLocationEnabled() {
        return getBoolean(R.string.key_update_picture_location_information, R.string.default_update_picture_location_information);
    }

    public String getDownloadVolume() {
        String volume = getString(context.getString(R.string.key_downloaded_images_location), null);
        if (volume == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                volume = MediaStore.VOLUME_EXTERNAL_PRIMARY;
            }
        }
        return volume;
    }
}
