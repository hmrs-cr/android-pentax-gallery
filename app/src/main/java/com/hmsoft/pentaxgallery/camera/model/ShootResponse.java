package com.hmsoft.pentaxgallery.camera.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class ShootResponse extends BaseResponse {

    public final int captureId;

    public ShootResponse(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public ShootResponse(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public ShootResponse(JSONObject jsonObject) {
        super(jsonObject);
        captureId = jsonObject.optInt("captureId");
    }
}
