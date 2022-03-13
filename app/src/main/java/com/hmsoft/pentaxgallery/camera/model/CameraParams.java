package com.hmsoft.pentaxgallery.camera.model;

import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;

import org.json.JSONArray;
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

    public final RectF focusEffectiveArea;

    public CameraParams(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public CameraParams(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public CameraParams(JSONObject jsonObject) throws JSONException {
        super(jsonObject);

        this.av = jsonObject.optString("av"); // "1.4",
        this.tv  = jsonObject.optString("tv"); // "15.10",
        this.sv = jsonObject.optString("sv"); // "3200",
        this.xv = jsonObject.optString("xv"); // "-0.5",
        this.WBMode = jsonObject.optString("WBMode"); // "auto",
        this.shootMode = jsonObject.optString("shootMode"); // "single",
        this.exposureMode = jsonObject.optString("exposureMode"); // "U1",
        this.operationMode = jsonObject.optString("operationMode");

        JSONArray jsonArray  = jsonObject.optJSONArray("focusEffectiveArea");
        if (jsonArray != null && jsonArray.length() == 2) {

            float feap1 = (((float)jsonArray.getInt(0)) / 2.0F) / 100.0F;
            float feap2 = (((float)jsonArray.getInt(0)) / 2.0F) / 100.0F;

            float x1 = 0.5F - feap1;
            float y1 = 0.5F - feap2;

            float x2 = feap1 + 0.5F;
            float y2 = feap2 + 0.5F;

            focusEffectiveArea = new RectF(x1, y1, x2, y2);
        } else {
            focusEffectiveArea = new RectF(0.111F, 0.125F, 0.889F, 0.875F);
        }
    }
}
