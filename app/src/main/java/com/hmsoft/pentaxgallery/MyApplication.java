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

package com.hmsoft.pentaxgallery;

import android.app.Application;
import android.os.Environment;

import com.hmsoft.pentaxgallery.util.TaskExecutor;

import java.io.File;

public class MyApplication extends Application {

    public static MyApplication ApplicationContext = null;

    private File mLocalDownloadsPath;

    public File getLocalDownloadsPath() {
        if(mLocalDownloadsPath == null) {
            mLocalDownloadsPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), this.getString(R.string.app_name));
            if (!mLocalDownloadsPath.exists()) {
                mLocalDownloadsPath.mkdirs();
            }
        }
        return mLocalDownloadsPath;
    }


    @Override
    public void onCreate() {
        ApplicationContext = this;
        TaskExecutor.init();
        super.onCreate();
    }   
}
