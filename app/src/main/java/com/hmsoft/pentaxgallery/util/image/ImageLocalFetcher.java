package com.hmsoft.pentaxgallery.util.image;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.ParcelFileDescriptor;
import android.widget.ImageView;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.camera.CameraFactory;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.util.Logger;

import java.io.IOException;

public class ImageLocalFetcher extends ImageRotatorFetcher {
    private static final String TAG = "ImageLocalFetcher";
    private static final String KEY_EXT =  ".fullsize_bitmap";


    private ImageData mImageData;

    public ImageLocalFetcher(Context context, int imageSize) {
        super(context, imageSize);
    }

    @Override
    public void loadImage(String url, Object param, ImageView imageView, OnImageLoadedListener listener) {
        if(param instanceof  ImageData){
            mImageData = (ImageData)param;
        }
        super.loadImage(url, param, imageView, listener);
    }

    @Override
    protected BitmapDrawable getBitmapFromMemCache(String key) {
        if(mImageData != null && !mImageData.isRaw && mImageData.existsOnLocalStorage()) {
            key = key + KEY_EXT;
        }
        BitmapDrawable value = super.getBitmapFromMemCache(key);
        if(BuildConfig.DEBUG && value != null) Logger.debug(TAG, "getBitmapFromMemCache: " + key);
        return value;
    }

    @Override
    protected Bitmap getBitmapFromDiskCache(String key) {
        if(mImageData != null && !mImageData.isRaw && mImageData.existsOnLocalStorage()) {
            key = key + KEY_EXT;
        }
        Bitmap value =  super.getBitmapFromDiskCache(key);
        if(BuildConfig.DEBUG && value != null) Logger.debug(TAG, "getBitmapFromDiskCache: " + key);
        return value;
    }

    @Override
    protected void addBitmapToCache(String key, BitmapDrawable value) {
        if(mImageData != null && !mImageData.isRaw && mImageData.existsOnLocalStorage()) {
            key = key + KEY_EXT;
        }
        super.addBitmapToCache(key, value);
    }

    protected Bitmap loadFromLocalFile(ImageData imageData) {
        ParcelFileDescriptor fileDescriptor = null;
        try {
                if(BuildConfig.DEBUG) Logger.debug(TAG, "Loading picture from " + imageData.getLocalStorageUri());
                fileDescriptor = mContentResolver.openFileDescriptor(imageData.getLocalStorageUri(), "r");
            } catch (IOException e) {
                if(BuildConfig.DEBUG) Logger.warning(TAG, "ERROR: Loading picture from " + imageData.getLocalStorageUri(), e);
                e.printStackTrace();
            }
        
          Bitmap bitmap = null;
          if (fileDescriptor != null) {
              bitmap = decodeSampledBitmapFromDescriptor(fileDescriptor.getFileDescriptor(), mImageWidth,
                      mImageHeight, getImageCache());
            
              if(bitmap != null && imageData != null) {
                  ImageMetaData metaData =  CameraFactory.DefaultCamera.getImageInfo(imageData);
                  if(metaData != null) {
                      bitmap = rotateBitmap(bitmap, metaData.orientation);
                  }
              }
          }
          if (fileDescriptor != null) {
              try {
                  fileDescriptor.close();
              } catch (IOException ignored) {}
            }
      
        return bitmap;
    }
  
    @Override
    protected Bitmap processBitmap(String url, ImageData imageData) {

        Bitmap bitmap;
        if (!imageData.isRaw && imageData.existsOnLocalStorage()) {
            bitmap = this.loadFromLocalFile(imageData);
        } else {
            bitmap = super.processBitmap(url, imageData);
        }

        return bitmap;
    }
}
