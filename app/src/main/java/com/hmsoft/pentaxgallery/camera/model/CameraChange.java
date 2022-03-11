package com.hmsoft.pentaxgallery.camera.model;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import androidx.annotation.NonNull;

public class CameraChange extends BaseResponse {

    public static final String ACTION_ADD = "add";
    public static final String CHANGED_STORAGE = "storage";
    public static final String CHANGED_CAMERA = "camera";
    public static final String CHANGED_LENS = "lens";

    public final String changed;
    public final String action;
    public final String storage;
    public final String filepath;
    public final int captureId;
    public final boolean focused;

    public CameraChange(String response) throws JSONException {
        this(new JSONTokener(response));
    }

    public CameraChange(JSONTokener jsonTokener) throws JSONException {
        this(new JSONObject(jsonTokener));
    }

    public CameraChange(JSONObject jsonObject) {
        super(jsonObject);
        changed = jsonObject.optString("changed");
        action = jsonObject.optString("action");
        storage = jsonObject.optString("storage");
        focused = jsonObject.optBoolean("focused");
        filepath = jsonObject.optString("filepath");
        captureId = jsonObject.optInt ("captureId", 0);
    }

    @NonNull
    @Override
    public String toString() {
        return changed + ", " + action + ", " + filepath;
    }

    public boolean isAction(String action) {
        return this.action != null && this.action.equals(action);
    }

    public boolean isChanged(String changed) {
        return  this.changed != null && this.changed.equals(changed);
    }
}
