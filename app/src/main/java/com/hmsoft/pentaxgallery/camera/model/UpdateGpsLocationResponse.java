package com.hmsoft.pentaxgallery.camera.model;

import org.json.JSONException;

public class UpdateGpsLocationResponse  extends BaseResponse {

    public final String gpsInfo;

    public UpdateGpsLocationResponse(String response, String gpsInfo) throws JSONException {
        super(response);
        this.gpsInfo = gpsInfo;
    }
}
