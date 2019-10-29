package com.hmsoft.pentaxgallery.ui.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.hmsoft.pentaxgallery.R;

public class ImageThumbWidget extends RelativeLayout {

    private ImageView mImageView;
    private ImageView mBatchImageView;
    private Context mContext;

    public ImageThumbWidget(Context context) {
        super(context);
        mContext = context;
    }

    public void init(ViewGroup.LayoutParams viewLayoutParams) {
        mImageView = new RecyclingImageView(mContext);
        mImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        mImageView.setLayoutParams(viewLayoutParams);
        setLayoutParams(new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        addView(mImageView);
    }

    public void showBatch(int id) {
        if(mBatchImageView == null) {
            mBatchImageView = new ImageView(mContext);

            Resources r = getResources();
            int px = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, r.getDisplayMetrics());

            mBatchImageView.setLayoutParams(new ViewGroup.LayoutParams(px, px));

            addView(mBatchImageView);
        }

        Drawable drawable = mContext.getResources().getDrawable(id);
        mBatchImageView.setImageDrawable(drawable);
        mBatchImageView.setVisibility(VISIBLE);
    }

    public void showAsSelected() {
        showBatch(R.drawable.ic_check_circle_black_24dp);
    }

    public void hideBatch() {
        if(mBatchImageView != null) {
            mBatchImageView.setImageDrawable(null);
            mBatchImageView.setVisibility(GONE);
        }
    }

    public ImageView getmImageView() {
        return mImageView;
    }
}
