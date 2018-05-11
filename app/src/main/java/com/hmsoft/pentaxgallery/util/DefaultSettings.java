package com.hmsoft.pentaxgallery.util;

import android.os.Environment;

import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.util.cache.CacheUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class DefaultSettings {


    public static String DOWNLOAD_LOCATION = "download_location";
    public static String THUMB_THREAD_NUMBER = "thumb_thread_number";
    public static String DEFAULT_CONNECT_TIME_OUT = "default_connect_time_out";
    public static String DEFAULT_READ_TIME_OUT = "default_read_time_out";

    private static DefaultSettings sInstance;

    private Properties mProperties;

    private DefaultSettings() {

    }

    public synchronized static DefaultSettings getsInstance() {
        if(sInstance == null) {
            sInstance = new DefaultSettings();
            sInstance.load();
        }
        return sInstance;
    }


    public File getSettingFile() {
        final File filesPath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !CacheUtils.isExternalStorageRemovable() ? MyApplication.ApplicationContext.getExternalFilesDir(null) :
                        MyApplication.ApplicationContext.getFilesDir();

        if(!filesPath.exists()) {
            filesPath.mkdirs();
        }

        return new File(filesPath, "params.properties");
    }

    public void load() {
        if(mProperties == null) {
            mProperties = new Properties();
            File file = getSettingFile();
            if (!file.exists()) {
                saveDefault();
            }
            try {
                FileInputStream inputStream = new FileInputStream(file);
                mProperties.load(inputStream);
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveDefault() {
        if(mProperties != null) {
            mProperties.setProperty(DOWNLOAD_LOCATION, MyApplication.ApplicationContext.getLocalDownloadsPath().getAbsolutePath());
            mProperties.setProperty(THUMB_THREAD_NUMBER, "4");
            mProperties.setProperty(DEFAULT_CONNECT_TIME_OUT, "1");
            mProperties.setProperty(DEFAULT_READ_TIME_OUT, "10");

            try {
                FileOutputStream outputStream = new FileOutputStream(getSettingFile());
                mProperties.store(outputStream, "");
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    public String getStringValue(String key) {
        load();
        return mProperties.getProperty(key);
    }

    public int getIntValue(String key) {
        String value = getStringValue(key);
        return Integer.valueOf(value);
    }

    public long getLogValue(String key) {
        String value = getStringValue(key);
        return Long.valueOf(value);
    }

    public boolean getBoolValue(String key) {
        return getIntValue(key) > 0;
    }


}
