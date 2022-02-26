package com.hmsoft.pentaxgallery.ui.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraChange;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.CameraParams;
import com.hmsoft.pentaxgallery.camera.model.PowerOffResponse;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;


public class CameraFragment extends Fragment implements CameraController.OnLiveViewFrameReceivedListener,
        CameraController.OnCameraChangeListener,
        CameraController.OnAsyncCommandExecutedListener {

    private static final String TAG = "CameraFragment";

    private ImageView mImageLiveView;
    private FloatingActionButton shutterActionButton;
    private SeekBar mXvSeekBar;
    private TextView mExposureCompensationBtn;
    private TextView mExposureModeBtn;

    private CameraController cameraController = Camera.instance.getController();

    private static boolean sInLiveView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_camera, container, false);

        shutterActionButton = v.findViewById(R.id.shutterActionButton);
        shutterActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shoot();
            }
        });

        mImageLiveView = v.findViewById(R.id.liveImageView);
        mImageLiveView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                focus();
            }
        });

        CameraData cameraData = Camera.instance.getCameraData();

        mXvSeekBar = v.findViewById(R.id.xvSeekBar);
        mXvSeekBar.setVisibility(View.GONE);
        mExposureCompensationBtn = v.findViewById(R.id.exposureCompensationBtn);
        if(cameraData != null) {
            final String[] xvList = cameraData.getParamList("xv");
            if(xvList != null) {
                mXvSeekBar.setMax(xvList.length - 1);
                mXvSeekBar.setTag(xvList);
                mXvSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        String xv = xvList[progress];
                        mExposureCompensationBtn.setText(xv);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });
            }
        }

        mExposureModeBtn = v.findViewById(R.id.exposureModeBtn);

        cameraController.addCameraChangeListener(this);

        return v;
    }

    /*private*/ void shoot() {
        cameraController.shoot(this);
    }

    /*private*/ void focus() {
        cameraController.focus(this);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.powerOff:
                cameraController.powerOff(this);
                return true;
        }
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mImageLiveView == null && getActivity() != null) {
            mImageLiveView = getActivity().findViewById(R.id.liveImageView);
        }
        updateCameraParams();
        sInLiveView = true;
    }

    @Override
    public void onStart() {
        super.onStart();
        cameraController.startLiveView(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraController.pauseLiveView();
        mImageLiveView = null;
        sInLiveView = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        if(!(getActivity() != null && getActivity().isChangingConfigurations())) {
            cameraController.stopLiveView();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraController.removeCameraChangeListener(this);
    }

    @Override
    public void onLiveViewFrameReceived(byte[] frameData) {

        if(frameData == null) {
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    cameraNotConnected();
                }
            });

            return;
        }

        if(mImageLiveView != null) {
            final Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if(mImageLiveView != null) {
                        mImageLiveView.setImageBitmap(bitmap);
                    }
                }
            });
        }
    }

    private void cameraNotConnected() {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(getContext(), "Camera not connected", Toast.LENGTH_LONG).show();
        }

        Activity activity = getActivity();
        if (activity != null) {
            //getActivity().finish();
        }
    }

    @Override
    public void onCameraChange(CameraChange change) {
        if(change.isChanged(CameraChange.CHANGED_CAMERA)) {
            updateCameraParams();
        }
    }

    private void updateCameraParams(final CameraParams params) {
        TaskExecutor.executeOnUIThread(new Runnable() {
            @Override
            public void run() {
                String[] xvList = (String[])mXvSeekBar.getTag();
                for(int c = 0; c < xvList.length; c++) {
                    if (xvList[c].equals(params.xv)) {
                        mXvSeekBar.setProgress(c);
                        break;
                    }
                }

                mExposureCompensationBtn.setText(params.xv);
                mExposureModeBtn.setText(params.exposureMode);
            }
        });
    }

    private void updateCameraParams() {
        cameraController.getCameraParams(this);
    }

    @Override
    public void onAsyncCommandExecuted(BaseResponse response) {
        if (response instanceof CameraParams) {
            updateCameraParams((CameraParams)response);
        } if (response instanceof PowerOffResponse) {
            if(response.success && getActivity() != null) {
                getActivity().finish();
            }
        } else if (response == null) {
            cameraNotConnected();
        }
    }

    public static boolean isInLiveView() {
        return sInLiveView;
    }
}
