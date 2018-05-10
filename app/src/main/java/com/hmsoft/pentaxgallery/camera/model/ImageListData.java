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

package com.hmsoft.pentaxgallery.camera.model;

import com.hmsoft.pentaxgallery.camera.implementation.pentax.model.PentaxImageList;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ImageListData extends BaseResponse {

    public final ImageList dirList;

    public ImageListData(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public ImageListData(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public ImageListData(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
        dirList = new PentaxImageList(jsonObject.optJSONArray("dirs"));
    }
}
