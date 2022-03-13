package com.hmsoft.pentaxgallery.ui.camera;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.AttributeSet;
import android.widget.ImageView;

import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.util.TaskExecutor;

@SuppressLint("AppCompatCustomView")
public class LiveViewImageView extends ImageView implements CameraController.OnLiveViewFrameReceivedListener {
    private static final float FOCUS_BOX_BORDER_STROKE_WIDTH = 3;
    private static final float FOCUS_BOX_SIZE_RELATION = 0.048148F;
    private static final float FOCUS_BOX_BORDER_SIZE_RELATION = 0.096153F;

    private Float mLiveViewAspectRatio;
    private int mLiveViewOriginalLongSide;
    private int mLiveViewOriginalShortSide;


    private int mFocusSquareX = -1;
    private int mFocusSquareY = -1;
    private int mFocusSquareStrokeColor;

    private int mFocusSquareDimen = 52;
    private int mFocusSquareStrokeWidth = 5;

    private final Paint mFocusSquareStrokePaint = new Paint();
    private final Paint mFocusSquareBorderStrokePaint = new Paint();

    private final int mFocusFocusedColor = Color.rgb(114,250,76);
    private final int mFocusFocusingColor = Color.rgb(255,255,255);
    private final int mFocusNotFocusedColor = Color.rgb(237,72,63);
    private final int mFocusSquareBorderStrokeColor = Color.rgb(22,22,22);
    private final int mFocusAreaColor = Color.rgb(255,255,255);

    private Bundle mSavedState;

    private RectF mFocusArea;
    private Rect mFocusAreaInPixels;

    public LiveViewImageView(Context context) {
        super(context);
    }

    public LiveViewImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LiveViewImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public LiveViewImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public int getLiveViewWidth() {
        int iw = this.getWidth();
        int ih = this.getHeight();

        if (iw < ih) {
            return iw;
        }


        float aspectRatio = mLiveViewAspectRatio != null ? mLiveViewAspectRatio : 1;
        return Math.round(ih * aspectRatio);
    }

    public int getLiveViewHeight() {
        int iw = this.getWidth();
        int ih = this.getHeight();

        if (ih < iw) {
            return ih;
        }

        float aspectRatio = mLiveViewAspectRatio != null ? mLiveViewAspectRatio : 1;
        return Math.round(iw / aspectRatio);
    }

    @Override
    public void setImageBitmap(Bitmap bitmap) {
        if (mLiveViewAspectRatio == null) {
            mLiveViewOriginalLongSide = Math.max(bitmap.getHeight(), bitmap.getWidth());
            mLiveViewOriginalShortSide = Math.min(bitmap.getHeight(), bitmap.getWidth());
            mLiveViewAspectRatio = (float)mLiveViewOriginalLongSide / (float)mLiveViewOriginalShortSide;
        }
        if (mSavedState != null) {
            this.restoreState(mSavedState);
            mSavedState = null;
        }
        setFocusAreaPoints();
        super.setImageBitmap(bitmap);
    }

    @Override
    public void onLiveViewFrameReceived(byte[] frameData) {
        if (frameData != null) {
            final Bitmap bitmap = BitmapFactory.decodeByteArray(frameData, 0, frameData.length);
            TaskExecutor.executeOnUIThread(() -> this.setImageBitmap(bitmap));
        } else {
            mLiveViewAspectRatio = null;
        }
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        drawFocusSquare(canvas);
        drawFocusArea(canvas);
        super.onDrawForeground(canvas);
    }

    private void drawFocusArea(Canvas canvas) {
        if (mFocusAreaInPixels != null) {
            mFocusSquareStrokePaint.setStyle(Paint.Style.STROKE);
            mFocusSquareStrokePaint.setStrokeWidth(1);
            mFocusSquareStrokePaint.setColor(mFocusAreaColor);

            canvas.drawRect(mFocusAreaInPixels, mFocusSquareStrokePaint);
        }
    }


    private void drawFocusSquare(Canvas canvas) {
        if (mFocusSquareX >= 0 &&  mFocusSquareY >= 0) {
            mFocusSquareStrokePaint.setStyle(Paint.Style.STROKE);
            mFocusSquareStrokePaint.setStrokeWidth(mFocusSquareStrokeWidth);
            mFocusSquareStrokePaint.setColor(mFocusSquareStrokeColor);

            mFocusSquareBorderStrokePaint.setStyle(Paint.Style.STROKE);
            mFocusSquareBorderStrokePaint.setStrokeWidth(FOCUS_BOX_BORDER_STROKE_WIDTH);
            mFocusSquareBorderStrokePaint.setColor(mFocusSquareBorderStrokeColor);

            int right = mFocusSquareX + mFocusSquareDimen;
            int bottom = mFocusSquareY + mFocusSquareDimen;

            canvas.drawRect(mFocusSquareX - FOCUS_BOX_BORDER_STROKE_WIDTH - 1, mFocusSquareY - FOCUS_BOX_BORDER_STROKE_WIDTH - 1,
                    right + FOCUS_BOX_BORDER_STROKE_WIDTH + 1, bottom + FOCUS_BOX_BORDER_STROKE_WIDTH + 1, mFocusSquareBorderStrokePaint);

            canvas.drawRect(mFocusSquareX, mFocusSquareY, right, bottom, mFocusSquareStrokePaint);

            canvas.drawRect(mFocusSquareX + mFocusSquareStrokeWidth, mFocusSquareY + mFocusSquareStrokeWidth,
                    right - mFocusSquareStrokeWidth, bottom - mFocusSquareStrokeWidth, mFocusSquareBorderStrokePaint);
        }
    }

    private void setFocusSquareStartPoint(int fpX, int fpY) {
        mFocusSquareDimen = Math.round(getLiveViewWidth() * FOCUS_BOX_SIZE_RELATION);
        mFocusSquareX = fpX - (mFocusSquareDimen / 2);
        mFocusSquareY = fpY - (mFocusSquareDimen / 2);
        mFocusSquareStrokeWidth = Math.round(mFocusSquareDimen * FOCUS_BOX_BORDER_SIZE_RELATION);
    }

    private void setFocusAreaPoints() {
        if (mFocusArea != null && mFocusAreaInPixels == null) {
            float width = (float)getLiveViewWidth();
            float height = (float)getLiveViewHeight();

            int l = Math.round(mFocusArea.left * width);
            int t = Math.round(mFocusArea.top * height);
            int r = Math.round(mFocusArea.right * width);
            int b = Math.round(mFocusArea.bottom * height);
            mFocusAreaInPixels = new Rect(l, t, r, b);
        }
    }

    public void setFocusArea(RectF focusArea) {
        mFocusArea = focusArea;
        mFocusAreaInPixels = null;
    }

    public void setFocusing(int fpX, int fpY) {
        setFocusSquareStartPoint(fpX, fpY);
        mFocusSquareStrokeColor = mFocusFocusingColor;
        invalidate();
    }

    public void setFocused() {
        mFocusSquareStrokeColor = mFocusFocusedColor;
        invalidate();
    }

    public void setFocused(int fpX, int fpY) {
        setFocusSquareStartPoint(fpX, fpY);
        setFocused();
    }

    public void setNotFocused() {
        mFocusSquareStrokeColor = mFocusNotFocusedColor;
        invalidate();
    }

    public void setNotFocused(int fpX, int fpY) {
        setFocusSquareStartPoint(fpX, fpY);
        setNotFocused();
    }

    public void removeFocusSquare() {
        setFocusSquareStartPoint(-1, -1);
        invalidate();
    }


    public void saveState(Bundle state) {
        state.putFloat("X", (float)(mFocusSquareX + mFocusSquareDimen / 2) / getLiveViewWidth());
        state.putFloat("Y", (float)(mFocusSquareY + mFocusSquareDimen / 2) / getLiveViewHeight());
        state.putInt("C", mFocusSquareStrokeColor);
    }

    public void restoreState(Bundle state) {
        if (mSavedState == null) {
            mSavedState = state;
        } else {
            state = mSavedState;
            int x = Math.round(getLiveViewWidth() * state.getFloat("X", -1));
            int y =  Math.round(getLiveViewHeight() * state.getFloat("Y", -1));
            setFocusSquareStartPoint(x, y);
            mFocusSquareStrokeColor = state.getInt("C", mFocusFocusingColor);
        }
    }
}
