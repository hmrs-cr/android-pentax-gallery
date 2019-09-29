package com.hmsoft.pentaxgallery.util.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;

import com.hmsoft.pentaxgallery.camera.CameraFactory;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;

public class ImageRotatorFetcher extends ImageFetcher {

    public ImageRotatorFetcher(Context context, int imageSize) {
        super(context, imageSize);
    }

    protected Bitmap rotateBitmap(Bitmap bitmap, float degrees) {

        if(degrees > 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        }

        return bitmap;
    }
    
    @Override
    protected Bitmap processBitmap(String url, ImageData imageData) {
        Bitmap bitmap = super.processBitmap(url, imageData);

        if(bitmap != null && imageData != null) {
            ImageMetaData metaData = CameraFactory.DefaultCamera.getImageInfo(imageData);
            if(metaData != null) {
                bitmap = rotateBitmap(bitmap, metaData.orientationDegrees);
            }
        }

        return bitmap;
    }
}
