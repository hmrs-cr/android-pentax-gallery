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

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class BaseResponse {

    public final int errCode;
    public final String errMsg;
    public final boolean success;
  
    private JSONObject mJSONObject;

    public BaseResponse(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public BaseResponse(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public BaseResponse(JSONObject jsonObject) {
        this(jsonObject.optInt("errCode"), jsonObject.optString("errMsg"));
        mJSONObject = jsonObject;
    }

    public BaseResponse(int errCode, String errMsg) {
        this.errCode = errCode;
        this.errMsg = errMsg;
        success = errCode == 200;
        mJSONObject = null;
    }
  
    public JSONObject getJSONObject() {
        return mJSONObject;
    }
}
