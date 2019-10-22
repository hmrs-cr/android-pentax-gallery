package com.hmsoft.pentaxgallery.ui.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;

import androidx.fragment.app.Fragment;


public class CameraFragment extends Fragment implements CameraController.OnLiveViewFrameReceivedListener {

    private ImageView mImageLiveView;
    private CameraController cameraController = Camera.instance.getController();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_camera, container, false);

        mImageLiveView = v.findViewById(R.id.liveImageView);
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        cameraController.startLiveView(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        cameraController.stopLiveView();
    }

    @Override
    public void onLiveViewFrameReceived(byte[] frameData) {
        final Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImageLiveView.setImageBitmap(bitmap);
            }
        });
    }
}
