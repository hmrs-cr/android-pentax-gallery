package com.hmsoft.pentaxgallery.camera.implementation.pentax.model;

import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;

import org.json.JSONArray;
import org.json.JSONException;

public class PentaxImageList extends ImageList {

    PentaxImageList(JSONArray jsonArray) throws JSONException {
        super(jsonArray);
    }

    @Override
    protected ImageData createImageData(String dirName, String fileName) {
        return  new PentaxImageData(dirName, fileName);
    }
}
