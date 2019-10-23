package com.hmsoft.pentaxgallery.ui.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;

import androidx.fragment.app.Fragment;


public class CameraFragment extends Fragment implements CameraController.OnLiveViewFrameReceivedListener {

    private ImageView mImageLiveView;
    private FloatingActionButton shutterActionButton;
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
        shutterActionButton = v.findViewById(R.id.shutterActionButton);

        shutterActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraController.shootAsync(new CameraController.OnAsyncCommandExecutedListener() {
                    @Override
                    public void onAsyncCommandExecuted(BaseResponse response) {
                        if(response == null) {
                            cameraNotConnected();
                        }
                    }
                });
            }
        });

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
        if(frameData != null) {
            final Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mImageLiveView.setImageBitmap(bitmap);
                }
            });
        } else {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    cameraNotConnected();
                }
            });

        }
    }

    private void cameraNotConnected() {
        Toast.makeText(getContext(), "Camera not connected", Toast.LENGTH_LONG).show();
        getActivity().finish();
    }
}
