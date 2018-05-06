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

package com.hmsoft.pentaxgallery.data.model;


import com.hmsoft.pentaxgallery.camera.model.ImageData;

import org.json.JSONException;
import org.json.JSONObject;

public class DownloadEntry {

    public static final String DOWNLOAD_ID = "downloadId";
    public static final String UNIQUE_FILE_NAME = "uniqueFileName";

    public final ImageData mImageData;
    public long mDownloadId;

    public DownloadEntry(ImageData imageData)  {
        mImageData = imageData;
    }
  
    public ImageData getImageData() {
    return mImageData;
  }

    public long getDownloadId() {
      return mDownloadId;
    }
  
    public void setDownloadId(long downloadId) {
        mDownloadId = downloadId;
    }

    public JSONObject toJSONbject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(DOWNLOAD_ID, mDownloadId);
            jsonObject.put(UNIQUE_FILE_NAME, mImageData.uniqueFileName);
            return jsonObject;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }
}
