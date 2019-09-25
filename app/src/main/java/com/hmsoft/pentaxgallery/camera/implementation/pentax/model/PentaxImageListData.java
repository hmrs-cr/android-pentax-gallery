package com.hmsoft.pentaxgallery.camera.implementation.pentax.model;

import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;

import org.json.JSONArray;
import org.json.JSONException;

public class PentaxImageListData extends ImageListData {

    public PentaxImageListData(String response) throws JSONException {
        super(response);
    }

    @Override
    public ImageList createImageList(JSONArray jsonArray) throws JSONException {
        return new PentaxImageList(jsonArray);
    }
}
