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

package com.hmsoft.pentaxgallery.camera.implementation.pentax.model;

import com.hmsoft.pentaxgallery.MyApplication;
import com.hmsoft.pentaxgallery.camera.implementation.pentax.UrlHelper;
import com.hmsoft.pentaxgallery.camera.model.ImageData;

import java.io.File;

public class PentaxImageData extends ImageData {

    private File mLocalPath;

    public PentaxImageData(String directory, String fileName) {
        super(directory, fileName);
        updateExistsOnLocasStorage();
    }

    @Override
    public String getViewUrl() {
        String viewUrl = UrlHelper.getViewUrl(this);
        return viewUrl;
    }

    @Override
    public String getThumbUrl() {
        String thumbUrl = UrlHelper.getThumbUrl(this);
        return thumbUrl;
    }

    @Override
    public String getDownloadUrl() {
        String downloadUrl = UrlHelper.getDownloadUrl(this);
        return downloadUrl;
    }

    @Override
    public File getLocalPath() {
        if(mLocalPath == null) {
            mLocalPath = new File(MyApplication.ApplicationContext.getLocalDownloadsPath(), uniqueFileName);
        }
        return mLocalPath;
    }
}
