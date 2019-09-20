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

public class ImageMetaData extends BaseResponse {

    public final String directory;
    public final String fileName;

    public final boolean captured;
    public final int orientation;
    public final String cameraModel;
    public final String dateTime;
  
    public final String aperture;
    public final String iso;
    public final String xv;
    public final String shutterSpeed;
    public final String latlng;
      
    
    public ImageMetaData(String directory, String fileName, boolean captured, int orientation, 
                         String cameraModel, String dateTime, String aperture, String iso, String xv,
                        String shutterSpeed, String latlng) {
        super(200, null);
        this.directory = directory;
        this.fileName = fileName;
        this.captured = captured;
        this.orientation = orientation;
        this.cameraModel = cameraModel;
        this.dateTime = dateTime;
        this.aperture = aperture;
        this.iso = iso;
        this.xv = xv;
        this.shutterSpeed = shutterSpeed;
        this.latlng = latlng;
    }
  
    public ImageMetaData(String jsonData) throws JSONException {
        this(new JSONObject(new JSONTokener(jsonData)));
    }


    @Override
    public String toString() {
        return String.format("f%s %s ISO%s", aperture, shutterSpeed.replace(".","/"), iso);
    }

    public ImageMetaData(JSONObject jsonObject) throws JSONException {
        super(jsonObject);
        directory = jsonObject.optString("dir");
        fileName = jsonObject.optString("file");
        captured = jsonObject.optBoolean("captured");
        orientation = jsonObject.optInt("orientation");
        cameraModel = jsonObject.optString("cameraModel");
        dateTime = jsonObject.optString("datetime");
        latlng = jsonObject.optString("latlng");
      
        aperture = jsonObject.optString("av");
        iso = jsonObject.optString("sv");
        xv = jsonObject.optString("xv");
        shutterSpeed = jsonObject.optString("tv");
    }          
}
