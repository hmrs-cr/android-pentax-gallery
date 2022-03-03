package com.hmsoft.pentaxgallery.camera.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class CameraParams extends BaseResponse {

    public final String av;
    public final String tv;
    public final String sv;
    public final String xv;
    public final String WBMode;
    public final String shootMode;
    public final String exposureMode;
    public final String operationMode;

    public CameraParams(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public CameraParams(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public CameraParams(JSONObject jsonObject) {
        super(jsonObject);

        this.av = jsonObject.optString("av"); // "1.4",
        this.tv  = jsonObject.optString("tv"); // "15.10",
        this.sv = jsonObject.optString("sv"); // "3200",
        this.xv = jsonObject.optString("xv"); // "-0.5",
        this.WBMode = jsonObject.optString("WBMode"); // "auto",
        this.shootMode = jsonObject.optString("shootMode"); // "single",
        this.exposureMode = jsonObject.optString("exposureMode"); // "U1",
        this.operationMode = jsonObject.optString("operationMode");
                /*"stillSize": "S1",
                "movieSize": "FHD24p",
                "effect": "cim_auto",
                "filter": "off"*/
    }
}
