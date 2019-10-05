/*
 * Copyright (C) 2018 Mauricio Rodriguez (ranametal@users.sf.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hmsoft.pentaxgallery.util.cache;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public class CacheUtils {

    private static final int MISC_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    //private static final String MISC_CACHE_DIR = "misc";

    private static final String TAG = "CacheUtils";

    private CacheUtils() { }

    private static DiskLruCache sMiscDiskCache = null;

    private static File sMiscCacheDir;
    private static boolean sMiscDiskCacheStarting = true;
    private static final Object sMiscDiskCacheLock = new Object();
    private static final int DISK_CACHE_INDEX = 0;


    /**
     * Check how much usable space is available at a given path.
     *
     * @param path The path to check
     * @return The space available in bytes
     */
    public static long getUsableSpace(File path) {
        return path.getUsableSpace();
    }

/*
    public static void init() {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                synchronized (sMiscDiskCacheLock) {
                    if(sMiscDiskCache != null) {
                        return;
                    }

                    if(sMiscCacheDir == null) {
                        sMiscCacheDir = CacheUtils.getDiskCacheDir(MyApplication.ApplicationContext, MISC_CACHE_DIR);
                    }
                    if (!sMiscCacheDir.exists()) {
                        sMiscCacheDir.mkdirs();
                    }

                    if (getUsableSpace(sMiscCacheDir) > MISC_CACHE_SIZE) {
                        try {
                            sMiscDiskCache = DiskLruCache.open(sMiscCacheDir, 1, 1, MISC_CACHE_SIZE);
                            //CacheUtils.setDiskCache(sMiscDiskCache);
                            if (BuildConfig.DEBUG) {
                                Logger.debug(TAG, "Misc cache initialized");
                            }
                        } catch (IOException e) {
                            sMiscDiskCache = null;
                        }
                    }
                    sMiscDiskCacheStarting = false;
                    sMiscDiskCacheLock.notifyAll();
                }
            }
        });
    }

    public static void flush() {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                synchronized (sMiscDiskCacheLock) {
                    if (sMiscDiskCache != null) {
                        try {
                            sMiscDiskCache.flush();
                            if (BuildConfig.DEBUG) {
                                Logger.debug(TAG, "Misc cache flushed");
                            }
                        } catch (IOException e) {
                            Logger.error(TAG, "flush - " + e);
                        }
                    }
                }
            }
        });
    }

    public static void close() {
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                synchronized (sMiscDiskCacheLock) {
                    if (sMiscDiskCache != null) {
                        try {
                            if (!sMiscDiskCache.isClosed()) {
                                sMiscDiskCache.close();
                                sMiscDiskCache = null;
                                if (BuildConfig.DEBUG) {
                                    Logger.debug(TAG, "Misc cache closed");
                                }
                            }
                        } catch (IOException e) {
                            Logger.error(TAG, "closeCacheInternal - " + e);
                        }
                    }
                }
            }
        });
    }


    private static void waitUntilCacheReady() {
        synchronized (sMiscDiskCacheLock) {
            // Wait for disk cache to initialize
            while (sMiscDiskCacheStarting) {
                try {
                    sMiscDiskCacheLock.wait();
                } catch (InterruptedException e) {
                }
            }
        }
    }
*/

    /**
     * Check if external storage is built-in or removable.
     *
     * @return True if external storage is removable (like an SD card), false
     *         otherwise.
     */
    public static boolean isExternalStorageRemovable() {
        return Environment.isExternalStorageRemovable();
    }

    /**
     * Get the external app cache directory.
     *
     * @param context The context to use
     * @return The external cache dir
     */
    public static File getExternalCacheDir(Context context) {
        return context.getExternalCacheDir();
    }

    /**
     * Get a usable cache directory (external if available, internal otherwise).
     *
     * @param context The context to use
     * @param uniqueName A unique directory name to append to the cache dir
     * @return The cache dir
     */
    public static File getDiskCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? getExternalCacheDir(context).getPath() :
                                context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    /*
    public static void saveString(String keyName, String data) {
        if(sMiscDiskCache != null) {
            waitUntilCacheReady();
            saveString(sMiscDiskCache, keyName, data);
        }
    }

    private static void saveString(DiskLruCache lruCache, String keyName, String data) {
        try {
            DiskLruCache.Editor editor = lruCache.edit(keyName);
            editor.set(0, data);
            editor.commit();
            if(BuildConfig.DEBUG) Logger.debug(TAG, "SaveString: " + keyName + "=" +data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getString(String keyName) {
        if(sMiscDiskCache != null) {
            waitUntilCacheReady();
            return getString(sMiscDiskCache, keyName);
        }
        return null;
    }

    private static String getString(DiskLruCache lruCache, String keyName) {
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = lruCache.get(keyName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(snapshot != null) {
            try {
                String data = snapshot.getString(0);
                if(BuildConfig.DEBUG) Logger.debug(TAG, "GetString: " + keyName + "=" +data);
                return data;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }


    private static boolean keyExists(DiskLruCache lruCache, String keyName) {
        DiskLruCache.Snapshot snapshot = null;
        try {
            snapshot = lruCache.get(keyName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return snapshot != null;
    }

    public static boolean keyExists(String keyName) {
        return keyExists(sMiscDiskCache, keyName);
    }

    public static void remove(String keyName) {
        if(sMiscDiskCache != null) {
            waitUntilCacheReady();
            try {
                sMiscDiskCache.remove(keyName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    */
}
