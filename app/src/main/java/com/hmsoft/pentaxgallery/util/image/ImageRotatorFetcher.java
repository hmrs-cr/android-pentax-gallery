package com.hmsoft.pentaxgallery.util.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

import com.hmsoft.pentaxgallery.camera.ControllerFactory;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;

public class ImageRotatorFetcher extends ImageFetcher {

    public ImageRotatorFetcher(Context context, int imageSize) {
        super(context, imageSize);
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int orientation) {

        float degrees = 0;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
        }

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
            ImageMetaData metaData = ControllerFactory.DefaultController.getImageInfo(imageData);
            if(metaData != null) {
                bitmap = rotateBitmap(bitmap, metaData.orientation);
            }
        }

        return bitmap;
    }
}
