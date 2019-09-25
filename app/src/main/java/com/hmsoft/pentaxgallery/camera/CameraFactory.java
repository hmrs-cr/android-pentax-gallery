package com.hmsoft.pentaxgallery.camera;

import com.hmsoft.pentaxgallery.camera.implementation.pentax.PentaxController;

public final class CameraFactory {

    private CameraFactory() { }    
  
    public static final Camera DefaultCamera = new Camera(new PentaxController());
}
