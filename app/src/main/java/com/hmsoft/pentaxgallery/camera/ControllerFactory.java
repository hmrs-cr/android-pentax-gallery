package com.hmsoft.pentaxgallery.camera;

import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.implementation.pentax.PentaxController;

public final class ControllerFactory {

    private ControllerFactory() { }

    public static final CameraController DefaultController = new PentaxController();
}
