package com.hmsoft.pentaxgallery.ui.camera;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.CameraChange;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.CameraParams;
import com.hmsoft.pentaxgallery.camera.model.PowerOffResponse;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;


public class CameraFragment extends Fragment implements CameraController.OnLiveViewFrameReceivedListener,
        CameraController.OnCameraChangeListener,
        CameraController.OnAsyncCommandExecutedListener,
        GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener {

    private static final String TAG = "CameraFragment";

    private ImageView mImageLiveView;
    private FloatingActionButton shutterActionButton;
    private SeekBar mXvSeekBar;
    private TextView mExposureCompensationBtn;
    private TextView mExposureModeBtn;

    private CameraController cameraController = Camera.instance.getController();

    private static boolean sInLiveView;
    private GestureDetector mDetector;

    private int mLiveViewOriginalLongSide;
    private int mLiveViewOriginalShortSide;
    private Float mLiveViewAspectRatio;

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
        mImageLiveView.setOnClickListener(v1 -> {
            //focus();
        });

        mDetector = new GestureDetector(getContext(), this);
        mDetector.setOnDoubleTapListener(this);

        mImageLiveView.setOnTouchListener((view, motionEvent) -> {
            if (this.mDetector.onTouchEvent(motionEvent)) {
                return true;
            }
            return false;
        });


        CameraData cameraData = Camera.instance.getCameraData();

        mXvSeekBar = v.findViewById(R.id.xvSeekBar);
        mExposureCompensationBtn = v.findViewById(R.id.exposureCompensationBtn);
        if(cameraData != null) {
            final String[] xvList = cameraData.getParamList("xv");
            if(xvList != null) {

                mExposureCompensationBtn.setOnClickListener(view -> {
                    updateXvUI("0.0");
                    updateExposureCompensation();
                });


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
                        if (Logger.DEBUG) Logger.debug(TAG, "onStartTrackingTouch");
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (Logger.DEBUG) Logger.debug(TAG, "onStopTrackingTouch");
                        updateExposureCompensation();
                    }
                });
            }
        }

        mExposureModeBtn = v.findViewById(R.id.exposureModeBtn);

        cameraController.addCameraChangeListener(this);

        return v;
    }

    private void updateExposureCompensation() {
        cameraController.updateExposureCompensation(mExposureCompensationBtn.getText().toString(), response -> {
            if (Logger.DEBUG) Logger.debug(TAG, "updateExposureCompensation:" + response.errMsg);
        });
    }

    /*private*/ void shoot() {
        cameraController.shoot(this);
    }

    /*private*/ void focus(int afpX, int afpY) {
        cameraController.focus(afpX, afpY, this);
    }

    boolean focus(MotionEvent motionEvent) {
        int x = Math.round(motionEvent.getAxisValue(MotionEvent.AXIS_X));
        int y = Math.round(motionEvent.getAxisValue(MotionEvent.AXIS_Y));
        if (x <= getLiveViewWidth() && y <= getLiveViewHeight()) {
            int afpX = Math.round((float)x / (float)getLiveViewWidth() * 100F);
            int afpY =  Math.round((float)y / (float)getLiveViewHeight() * 100F);
            if (Logger.DEBUG) Logger.debug(TAG, "Autofocus points:" + afpX + "/" + afpY);
            focus(afpX, afpY);
            return true;
        }

        return false;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.powerOff:
                new AlertDialog.Builder(getContext())
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes, (dialogInterface, i) -> cameraController.powerOff(CameraFragment.this))
                        .setTitle(R.string.power_of_camera)
                        .setMessage(R.string.are_you_sure)
                        .show();
                return true;
            case R.id.liveView:
                toggleLiveView();
                item.setChecked(sInLiveView);
                return true;
        }
        return false;
    }

    private void toggleLiveView() {
        if (sInLiveView) {
            cameraController.stopLiveView();
            sInLiveView = false;
        } else {
            cameraController.startLiveView(this);
            sInLiveView = true;
        }
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
                        if (mLiveViewAspectRatio == null) {
                            mLiveViewOriginalLongSide = Math.max(bitmap.getHeight(), bitmap.getWidth());
                            mLiveViewOriginalShortSide = Math.min(bitmap.getHeight(), bitmap.getWidth());
                            mLiveViewAspectRatio = (float)mLiveViewOriginalLongSide / (float)mLiveViewOriginalShortSide;
                        }
                        mImageLiveView.setImageBitmap(bitmap);
                    }
                }
            });
        }
    }

    private int getLiveViewWidth() {
        int iw = this.mImageLiveView.getWidth();
        int ih = this.mImageLiveView.getHeight();

        if (iw < ih) {
            return iw;
        }


        float aspectRatio = mLiveViewAspectRatio != null ? mLiveViewAspectRatio : 1;
        return Math.round(ih * aspectRatio);
    }

    private int getLiveViewHeight() {
        int iw = this.mImageLiveView.getWidth();
        int ih = this.mImageLiveView.getHeight();

        if (ih < iw) {
            return ih;
        }

        float aspectRatio = mLiveViewAspectRatio != null ? mLiveViewAspectRatio : 1;
        return Math.round(iw / aspectRatio);
    }

    private void cameraNotConnected() {
        Context context = getContext();
        if (context != null) {
            Toast.makeText(getContext(), R.string.camera_not_connected_label, Toast.LENGTH_LONG).show();
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
                updateXvUI(params.xv);
                mExposureModeBtn.setText(params.exposureMode);
            }
        });
    }

    private int findXvPosition(String xv) {
        String[] xvList = (String[])mXvSeekBar.getTag();
        for(int c = 0; c < xvList.length; c++) {
            if (xvList[c].equals(xv)) {
                mXvSeekBar.setProgress(c);
                break;
            }
        }

        return -1;
    }

    private void updateXvUI(String xv) {
        int pos = findXvPosition(xv);
        if (pos > -1) {
            mXvSeekBar.setProgress(pos);
            mExposureCompensationBtn.setText(xv);
        }
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

        if (Logger.DEBUG && response != null) Logger.debug(TAG, response.getClass().getName() + ": " + response.errMsg);
    }

    public static boolean isInLiveView() {
        return sInLiveView;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent motionEvent) {
        focus(motionEvent);
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent motionEvent) {
        if (Logger.DEBUG) Logger.debug(TAG, "onDoubleTap");
        shoot();
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent motionEvent) {
        if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
            if (Logger.DEBUG) Logger.debug(TAG, "onDoubleTapEvent:" + motionEvent);
        }
        return false;
    }

    @Override
    public boolean onDown(MotionEvent motionEvent) {
        if (Logger.DEBUG) Logger.debug(TAG, "onDown");
        return false;
    }

    @Override
    public void onShowPress(MotionEvent motionEvent) {
        if (Logger.DEBUG) Logger.debug(TAG, "onShowPress");

    }

    @Override
    public boolean onSingleTapUp(MotionEvent motionEvent) {
        if (Logger.DEBUG) Logger.debug(TAG, "onSingleTapUp");
        return false;
    }

    long lastXvProgressIncrement;
    @Override
    public boolean onScroll(MotionEvent motionEvent, MotionEvent motionEvent1, float distanceX,
                            float distanceY) {
        if (Math.abs(distanceY) > 1 && Math.abs(distanceX) < 8 && SystemClock.elapsedRealtime() - lastXvProgressIncrement > 75) {
            //mXvSeekBar.incrementProgressBy(distanceY > 0 ? 1 : -1);
            if (Logger.DEBUG) Logger.debug(TAG, "onScroll:" + distanceX + "/" + distanceY);
            //updateExposureCompensation();
            lastXvProgressIncrement = SystemClock.elapsedRealtime();
            return true;
        }

        if (Logger.DEBUG) Logger.debug(TAG, "onScroll:" + motionEvent);
        return false;
    }

    @Override
    public void onLongPress(MotionEvent motionEvent) {
        if (Logger.DEBUG) Logger.debug(TAG, "onLongPress");
    }

    @Override
    public boolean onFling(MotionEvent motionEvent, MotionEvent motionEvent1, float v, float v1) {
        if (Logger.DEBUG) Logger.debug(TAG, "onFling");
        return false;
    }
}
