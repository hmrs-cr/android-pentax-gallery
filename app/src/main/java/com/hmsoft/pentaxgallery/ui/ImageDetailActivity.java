/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified by hmrs.cr
 *
 */

package com.hmsoft.pentaxgallery.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.BaseResponse;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageMetaData;
import com.hmsoft.pentaxgallery.service.DownloadService;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.image.ImageCache;
import com.hmsoft.pentaxgallery.util.image.ImageFetcher;
import com.hmsoft.pentaxgallery.util.image.ImageLocalFetcher;
import com.hmsoft.pentaxgallery.util.image.ImageRotatorFetcher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;

public class ImageDetailActivity extends AppCompatActivity implements OnClickListener,
        GestureDetector.OnGestureListener,
        GestureDetector.OnDoubleTapListener, ViewPager.OnPageChangeListener,
        DownloadService.OnDownloadFinishedListener,
        CameraController.OnAsyncCommandExecutedListener {

    private static final String IMAGE_CACHE_DIR = "images";
    public static final String EXTRA_IMAGE = "extra_image";

    private Camera mCamera = Camera.instance;
    private ImagePagerAdapter mAdapter;
    private ImageFetcher mImageFetcher;
    private ViewPager mPager;
    private Menu mMenu;
    private ImageData imageData;
    private DownloadService.DownloadEntry downloadEntry;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_detail_pager);


        // Fetch screen height and width, to use as our max size when loading images as this
        // activity runs full screen
        final DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        final int height = displayMetrics.heightPixels;
        final int width = displayMetrics.widthPixels;

        final int longest = (height > width ? height : width) / 2;

        ImageCache.ImageCacheParams cacheParams =
                new ImageCache.ImageCacheParams(this, IMAGE_CACHE_DIR);
        cacheParams.setMemCacheSizePercent(0.40f); // Set memory cache to 35% of app memory

        // The ImageFetcher takes care of loading images into our ImageView children asynchronously
        boolean loadLocalImageData = Camera.instance.getPreferences().loadLocalImageData();

        mImageFetcher = loadLocalImageData ? new ImageLocalFetcher(this, longest) : new ImageRotatorFetcher(this, longest);
        mImageFetcher.addImageCache(getSupportFragmentManager(), cacheParams);
        mImageFetcher.setImageFadeIn(false);

        // Set up ViewPager and backing adapter
        mAdapter = new ImagePagerAdapter(getSupportFragmentManager());
        mPager = (ViewPager) findViewById(R.id.pager);
        mPager.setAdapter(mAdapter);
        mPager.setPageMargin((int) getResources().getDimension(R.dimen.horizontal_page_margin));
        mPager.setOffscreenPageLimit(3);

        mPager.addOnPageChangeListener(this);

        // Set up activity to go full screen
        getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN);

        // Enable some additional newer visibility and ActionBar features to create a more
        // immersive photo viewing experience

        final ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            // Hide title text and set home as up
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);

            // Hide and show the ActionBar as the visibility changes
            mPager.setOnSystemUiVisibilityChangeListener(
                    new View.OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int vis) {
                            if ((vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
                                actionBar.hide();
                            } else {
                                actionBar.show();
                            }
                        }
                    });

        }

        // Set the current item based on the extra passed in to this activity
        final int extraCurrentItem = getIntent().getIntExtra(EXTRA_IMAGE, -1);
        if (extraCurrentItem != -1) {
            mPager.setCurrentItem(extraCurrentItem);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mImageFetcher.setExitTasksEarly(false);
        updateCurrentImageData();
        updateUiElements();
        DownloadService.setOnDownloadFinishedListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mImageFetcher.setExitTasksEarly(true);
        mImageFetcher.flushCache();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mImageFetcher.closeCache();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        mMenu = menu;
        updateOptionsMenu();
        return super.onPrepareOptionsMenu(menu);
    }

    private void updateOptionsMenu() {
        if(mMenu != null) {
            
            if(imageData == null) {
                return;
            }            

            boolean isInDownloadQueue = downloadEntry != null;
            boolean isDownloading = isInDownloadQueue && downloadEntry.getDownloadId() > 0;

            MenuItem downloadItem = mMenu.findItem(R.id.download);
            MenuItem downloadAgainItem = mMenu.findItem(R.id.downloadAgain);
            MenuItem cancelDownloadItem = mMenu.findItem(R.id.cancelDownload);
            MenuItem downloadNowItem = mMenu.findItem(R.id.downloadNow);
            MenuItem shareItem = mMenu.findItem(R.id.share);
            MenuItem flagItem = mMenu.findItem(R.id.flag);

            cancelDownloadItem.setVisible(isInDownloadQueue);
            downloadNowItem.setVisible(isInDownloadQueue && !isDownloading);

            boolean isDownloaded = imageData.existsOnLocalStorage();
            shareItem.setVisible(isDownloaded);
            downloadItem.setVisible(!isDownloaded && !isInDownloadQueue);
            downloadAgainItem.setVisible(isDownloaded && !isInDownloadQueue);

            flagItem.setChecked(imageData.isFlagged());
            updateFlaggedBtnState(flagItem);
        }
    }

    private void updateFlaggedBtnState(MenuItem flagItem) {
        flagItem.setIcon(flagItem.isChecked() ? R.drawable.ic_done_all_white_24dp : R.drawable.ic_done_white_24dp);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.downloadAgain:
            case R.id.download:
                download();
                return true;
            case R.id.cancelDownload:
                cancelDownload();
                return true;
            case R.id.downloadNow:
                downloadNow();
                return true;
            case R.id.gallery_find:
                findInGallery();
                return true;
            case R.id.share:
                share();
                return true;
            case R.id.pic_info:
                showInfoDialog();
                return true;
            case R.id.flag:
                toggleFlag(item);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleFlag(MenuItem item) {
        item.setChecked(!item.isChecked());
        setFlagged(item.isChecked());
        updateFlaggedBtnState(item);
        updateUiElements();
    }

    private void setFlagged(boolean flagged) {
        imageData.setIsFlagged(flagged);
        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                imageData.saveData();
            }
        });
    }

    private void openUrl() {
        String downloadUrl = imageData.getDownloadUrl();
        Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(downloadUrl));
        startActivity(intent);
    }

    private void showInfoDialog() {        
        ImageMetaData imageMetaData = imageData.getMetaData();
        if(imageMetaData != null) {
            Toast.makeText(this, imageMetaData.toString(), Toast.LENGTH_LONG).show();
        } else {
            mCamera.getController().getImageInfo(imageData, this);
        }

    }

    private void share() {        
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, imageData.getLocalStorageUri());
        shareIntent.setType("image/*");
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_in)));
    }

    private void downloadNow() {
        if (!mCamera.isConnected()) {
            Toast.makeText(this, R.string.camera_not_connected_label, Toast.LENGTH_LONG).show();
            return;
        }
        DownloadService.downloadDown(imageData);
        updateCurrentImageData();
        updateUiElements();
    }

    private void cancelDownload() {        
        DownloadService.removeFromDownloadQueue(imageData);
        mPager.getAdapter().notifyDataSetChanged();
        updateUiElements();
    }

    private void findInGallery() {
        Uri uri = imageData.getLocalStorageUri();
        if(uri != null) {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "image/*");
            startActivity(intent);
        } else {
            Toast.makeText(getApplicationContext(), String.format(getString(R.string.not_found_in_gallery), imageData.fileName), Toast.LENGTH_LONG).show();
        }
    }

    private void download() {
        if (!mCamera.isConnected()) {
            Toast.makeText(this, R.string.camera_not_connected_label, Toast.LENGTH_LONG).show();
            return;
        }
        DownloadService.addDownloadQueue(imageData, false);
        updateCurrentImageData();
        updateUiElements();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.detail_menu, menu);
        MenuItem downloadItem = menu.findItem(R.id.download);
        downloadItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem shareItem = menu.findItem(R.id.share);
        shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem infoItem = menu.findItem(R.id.pic_info);
        infoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        MenuItem flagItem = menu.findItem(R.id.flag);
        flagItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        flagItem.setCheckable(true);
        
        mMenu = menu;
        
        return true;
    }

    /**
     * Called by the ViewPager child fragments to load images via the one ImageFetcher
     */
    public ImageFetcher getImageFetcher() {
        return mImageFetcher;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        MenuItem flagItem = mMenu.findItem(R.id.flag);
        toggleFlag(flagItem);
        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onPageScrolled(int i, float v, int i1) {

    }

    @Override
    public void onPageSelected(int i) {        
        updateCurrentImageData();
        updateUiElements();
    }
          
    private void updateCurrentImageData() {
        imageData = mCamera.getImageList().getImage(mPager.getCurrentItem());
        downloadEntry = null;
        if(imageData != null) {
           downloadEntry = DownloadService.findDownloadEntry(imageData);
        }
    }

    private void updateUiElements() {
        updateOptionsMenu();
        updateActionBarTitle();
    }

    private void updateActionBarTitle() {        
        if(imageData != null) {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(imageData.fileName);                
                String subtitle = null;
                if (downloadEntry != null) {
                    subtitle = getString(R.string.in_download_queue);
                    if (downloadEntry.getProgress() >= 0) {
                        subtitle = getString(R.string.downloading) + " (" + downloadEntry.getProgress() + "%)";
                    }
                } else {
                    if (imageData.existsOnLocalStorage()) {
                        subtitle = getString(R.string.downloaded);
                    }
                }
                actionBar.setSubtitle(subtitle);
            }
        } else {
            if (mCamera.hasFilter(DownloadService.DownloadQueueFilter) && mCamera.imageCount() == 0) {
                finish();
            }
        }
    }

    @Override
    public void onPageScrollStateChanged(int i) {

    }

    @Override
    public void onDownloadFinished(ImageData imageData, long donloadId, int remainingDownloads,
                                   int downloadCount, int errorCount, boolean wasCanceled) {
        updateCurrentImageData();
        if(mCamera.isFiltered()) {
            mAdapter.notifyDataSetChanged();
        }
        updateUiElements();

        if(remainingDownloads == 0) {
            //ControllerFactory.DefaultController.powerOff(null);
        }
    }

    @Override
    public void onDownloadProgress(ImageData imageData, long donloadId, int progress) {
        updateActionBarTitle();
    }

    @Override
    public void onAsyncCommandExecuted(BaseResponse response) {
        if(response instanceof ImageMetaData) {
            Toast.makeText(this, response.toString(), Toast.LENGTH_LONG).show();
        }
    }


    /**
     * The main adapter that backs the ViewPager. A subclass of FragmentStatePagerAdapter as there
     * could be a large number of items in the ViewPager and we don't want to retain them all in
     * memory at once but create/destroy them on the fly.
     */
    private class ImagePagerAdapter extends FragmentStatePagerAdapter {

        public ImagePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return mCamera.imageCount();
        }

        @Override
        public Fragment getItem(int position) {
            if(mCamera.getImageList() == null) {
                return null;
            }
            return ImageDetailFragment.newInstance(position);
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }

    /**
     * Set on the ImageView in the ViewPager children fragments, to enable/disable low profile mode
     * when the ImageView is touched.
     */
    @Override
    public void onClick(View v) {
        final int vis = mPager.getSystemUiVisibility();
        if ((vis & View.SYSTEM_UI_FLAG_LOW_PROFILE) != 0) {
            mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } else {
            mPager.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }
}
