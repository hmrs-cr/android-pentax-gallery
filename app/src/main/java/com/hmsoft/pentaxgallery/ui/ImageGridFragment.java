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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.Camera;
import com.hmsoft.pentaxgallery.camera.controller.CameraController;
import com.hmsoft.pentaxgallery.camera.model.CameraChange;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.FilteredImageList;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.service.DownloadService;
import com.hmsoft.pentaxgallery.ui.camera.CameraActivity;
import com.hmsoft.pentaxgallery.ui.preferences.PreferencesActivity;
import com.hmsoft.pentaxgallery.ui.widgets.ImageThumbWidget;
import com.hmsoft.pentaxgallery.util.Logger;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.Utils;
import com.hmsoft.pentaxgallery.util.image.ImageCache;
import com.hmsoft.pentaxgallery.util.image.ImageFetcher;
import com.hmsoft.pentaxgallery.util.image.ImageRotatorFetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

/**
 * The main fragment that powers the ImageGridActivity screen. Fairly straight forward GridView
 * implementation with the key addition being the UrlImageWorker class w/ImageCache to load children
 * asynchronously, keeping the UI nice and smooth and caching thumbnails for quick retrieval. The
 * cache is retained over configuration changes like orientation change so the images are populated
 * quickly if, for example, the user rotates the device.
 */
public class ImageGridFragment extends Fragment implements AdapterView.OnItemClickListener,
        AdapterView.OnItemLongClickListener,
        DownloadService.OnDownloadFinishedListener,
        SearchView.OnQueryTextListener,
        ActionBar.OnNavigationListener,
        SwipeRefreshLayout.OnRefreshListener,
        CameraController.OnCameraChangeListener,
        SearchView.OnCloseListener, View.OnApplyWindowInsetsListener {
    private static final String TAG = "ImageGridFragment";
    private static final String IMAGE_CACHE_DIR = "thumbs";

    private static ImageListTask mImageListTask = null;

    private int mRequestCount = 5;
    private int mImageThumbSize;
    private int mImageThumbSpacing;
    private ImageAdapter mAdapter;
    private ImageFetcher mImageFetcher;
    private ProgressBar mProgressBar;
    private TextView mEmptyViewLabel;
    private TextView mProgressLabel;
    private GridView mGridView;
    private FloatingActionButton mCameraActionButton;
    private Menu mMenu;
    private SearchView mSearchView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Camera mCamera = Camera.instance;
    private boolean mNeedUpdateImageList;
    private volatile boolean mDontShowProgressBar;

    private final int DEFAULT_MULTIFORMAT_FILTER =  R.id.view_raw_only;

    /**
     * Empty constructor as per the Fragment documentation
     */
    public ImageGridFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        mImageThumbSize = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_size);
        mImageThumbSpacing = getResources().getDimensionPixelSize(R.dimen.image_thumbnail_spacing);

        ImageCache.ImageCacheParams cacheParams =
                new ImageCache.ImageCacheParams(getActivity(), IMAGE_CACHE_DIR);

        cacheParams.setMemCacheSizePercent(0.25f); // Set memory cache to 25% of app memory

        // The ImageFetcher takes care of loading images into our ImageView children asynchronously
        mImageFetcher = new ImageRotatorFetcher(getActivity(), mImageThumbSize);
        mImageFetcher.setLoadingImage(R.drawable.empty_photo);
        mImageFetcher.addImageCache(getActivity().getSupportFragmentManager(), cacheParams);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        final View v = inflater.inflate(R.layout.image_grid_fragment, container, false);
        mGridView = v.findViewById(R.id.gridView);
        mProgressBar = v.findViewById(R.id.progressbarGrid);
        mEmptyViewLabel = v.findViewById(R.id.emptyViewLabel);
        mProgressLabel = v.findViewById(R.id.progressLabel);
        mEmptyViewLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showView(false, -1);
            }
        });

        v.setOnApplyWindowInsetsListener(this);


        mGridView.setOnTouchListener(new View.OnTouchListener(){

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Disable touch while loading.
                return mImageListTask != null;
            }

        });

        mSwipeRefreshLayout = v.findViewById(R.id.swiperefresh);
        mSwipeRefreshLayout.setOnRefreshListener(this);

        mAdapter = new ImageAdapter(getActivity());

        mGridView.setAdapter(mAdapter);
        mGridView.setOnItemClickListener(this);
        mGridView.setOnItemLongClickListener(this);
        mGridView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                // Pause fetcher to ensure smoother scrolling when flinging
                if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    mImageFetcher.setPauseWork(false);
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
            }
        });

        // This listener is used to get the final width of the GridView and then calculate the
        // number of columns and the width of each column. The width of each column is variable
        // as the GridView has stretchMode=columnWidth. The column width is used to set the height
        // of each view so we get nice square thumbnails.
        mGridView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (mAdapter.getNumColumns() == 0) {
                            int width = mGridView.getWidth();
                            int numColumns = mGridView.getNumColumns();
                            if (numColumns <= 0) {
                                numColumns = (int) Math.floor(width / (mImageThumbSize + mImageThumbSpacing));
                            }
                            if (numColumns > 0) {
                                final int columnWidth = (width / numColumns) - mImageThumbSpacing;
                                mAdapter.setNumColumns(numColumns);
                                mAdapter.setItemHeight(columnWidth);
                                if (BuildConfig.DEBUG) {
                                    Logger.debug(TAG, "onCreateView - numColumns set to " + numColumns);
                                }
                                mGridView.getViewTreeObserver()
                                        .removeOnGlobalLayoutListener(this);
                            }
                        }
                    }
                });

        mCameraActionButton = v.findViewById(R.id.cameraActionButton);
        mCameraActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CameraActivity.start(getActivity());
            }
        });

        mCameraActionButton.setVisibility(mCamera.isConnected() ? View.VISIBLE : View.GONE);

        updateEmptyViewText("");

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();

        if(!Utils.hasAllPermissions()) {
            if(mRequestCount-- < 0) {
                Utils.showSettingActivity(getActivity());
            } else {
                Utils.requestAllPermissions(getActivity());
            }
        }

        mImageFetcher.setCancel(false);
        mImageFetcher.setExitTasksEarly(false);

        ImageList imageList = mCamera.getImageList();

        mCamera.rebuildFilter();

        mAdapter.notifyDataSetChanged();

        if(imageList == null) {
            //syncPictureList(false);
            loadPictureList();

        } else {
            mProgressBar.setVisibility(View.GONE);
        }
        DownloadService.setOnDownloadFinishedListener(this);
        updateActionBarTitle();

        DownloadService.setDisplayNotification(false);

        if(mCamera.isConnected()) {
            mCamera.getController().addCameraChangeListener(ImageGridFragment.this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if(mCamera.isConnected()) {
            TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
                @Override
                public void run() {
                    DownloadService.saveQueueToFile(mCamera.getCameraData());
                }
            });
        }

        mImageFetcher.setPauseWork(false);
        mImageFetcher.setExitTasksEarly(true);
        mImageFetcher.flushCache();
        cancelCacheThread = true;

        DownloadService.setOnDownloadFinishedListener(null);
        DownloadService.setDisplayNotification(true);
    }

    @Override
    public void onDestroy() {
        mCamera.getController().removeCameraChangeListener(this);
        if(getActivity() == null || !getActivity().isChangingConfigurations()) {
            mCamera.getController().addCameraChangeListener(null);
        }
        super.onDestroy();
        mImageFetcher.closeCache();
        //CacheUtils.close();
        if(mImageListTask != null) {
            mImageListTask.cancel(true);
            mImageListTask = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        if(mAdapter.isInSelectMode()) {
            mAdapter.toggleItemSelection(position);
        } else {
            startDetailActivity(v, (int) id);
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if(mCamera.isFiltered()) {          
            return true;
        }

        mAdapter.toggleItemSelection(position);
        return true;
    }

    private void startDetailActivity(View v, int id) {
        final Intent i = new Intent(getActivity(), ImageDetailActivity.class);
        i.putExtra(ImageDetailActivity.EXTRA_IMAGE, id);

        // makeThumbnailScaleUpAnimation() looks kind of ugly here as the loading spinner may
        // show plus the thumbnail image in GridView is cropped. so using
        // makeScaleUpAnimation() instead.
        if(v != null) {
            ActivityOptions options =
                    ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight());
            getActivity().startActivity(i, options.toBundle());
        } else {
            getActivity().startActivity(i);
        }

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        inflater.inflate(R.menu.main_menu, menu);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            menu.setGroupDividerEnabled(true);
        } else {
            MenuCompat.setGroupDividerEnabled(menu, true);
        }

        MenuItem downloadFilter = menu.findItem(R.id.downloadFilter);
        downloadFilter.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem syncItem = menu.findItem(R.id.sync_images_1);
        syncItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        syncItem = menu.findItem(R.id.sync_images_2);
        syncItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem searchItem = menu.findItem(R.id.search);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        searchItem.setActionView(R.layout.search_view);
        MenuItem proccessDownloadQueueItem = menu.findItem(R.id.proccess_download_queue);
        proccessDownloadQueueItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem cancelDownloadQueueItem = menu.findItem(R.id.cancel_download_queue);
        cancelDownloadQueueItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        MenuItem shutdownWhenDoneItem = menu.findItem(R.id.shutdown_when_download_done_queue);
        shutdownWhenDoneItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        MenuItem downloadJpgs = menu.findItem(R.id.download_jpgs);
        downloadJpgs.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem downloadItem = menu.findItem(R.id.download_selected);
        downloadItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem clearSelectionItem = menu.findItem(R.id.clear_selection);
        clearSelectionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem selectionItem = menu.findItem(R.id.select_all);
        selectionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        selectionItem = menu.findItem(R.id.select_no_downloaded);
        selectionItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem shareItem = menu.findItem(R.id.share);
        shareItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);


        SearchManager searchManager =
                (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        mSearchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        mSearchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));

        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);

        mMenu = menu;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mMenu = menu;
        updateMenuItems();
    }

    private void updateMenuItems() {
        if(mMenu != null) {
            if(mAdapter.isInSelectMode()) {
                int s = mMenu.size();
                for(int i = 0; i < s; i++) {
                   MenuItem item = mMenu.getItem(i);
                   int grpId = item.getGroupId();
                   boolean visibleOnSelectMode = grpId == R.id.selection_menu_group || grpId == R.id.misc_menu_group;
                   item.setVisible(visibleOnSelectMode);
                }
                return;
            }

            List<CameraData> cameras = mCamera.getRegisteredCameras();
            if(cameras != null && cameras.size() > 1) {
                for(CameraData cameraData : cameras) {
                    MenuItem menuItem = mMenu.findItem(cameraData.hashCode);
                    if(menuItem == null) {
                        menuItem = mMenu.add(R.id.camera_menu_list, cameraData.hashCode, 1, cameraData.getDisplayName());
                        menuItem.setCheckable(true);
                    }
                    CameraData currentCamera = mCamera.getCameraData();
                    menuItem.setChecked(currentCamera != null && currentCamera.hashCode == cameraData.hashCode);
                }
            }

            MenuItem downloadItem = mMenu.findItem(R.id.download_selected);
            downloadItem.setVisible(false);
            MenuItem clearSelectionItem = mMenu.findItem(R.id.clear_selection);
            clearSelectionItem.setVisible(false);
            MenuItem selectAllItem = mMenu.findItem(R.id.select_all);
            selectAllItem.setVisible(false);
            MenuItem selectNonDownloadedItem = mMenu.findItem(R.id.select_no_downloaded);
            selectNonDownloadedItem.setVisible(false);


            boolean isShowDownloadQueueOnly = mCamera.hasFilter(DownloadService.DownloadQueueFilter);
            boolean isShowDownloadedOnly = mCamera.hasFilter(FilteredImageList.DownloadedFilter);
            boolean isFlaggedOnly = mCamera.hasFilter(FilteredImageList.FlaggedFilter);
            boolean isShowRawOnly = mCamera.hasFilter(FilteredImageList.RawFilter);
            boolean isShowJpgOnly = mCamera.hasFilter(FilteredImageList.JpgFilter);

            MenuItem downloadFilterItem = mMenu.findItem(R.id.downloadFilter);
            downloadFilterItem.setVisible(true);

            MenuItem downloadJpgs = mMenu.findItem(R.id.download_jpgs);
            downloadJpgs.setVisible(!isShowDownloadQueueOnly);

            MenuItem flaggedOnlyItem = mMenu.findItem(R.id.view_flagged_only);
            flaggedOnlyItem.setChecked(isFlaggedOnly);

            MenuItem downloadsOnlyItem = mMenu.findItem(R.id.view_downloads_only);
            downloadsOnlyItem.setChecked(isShowDownloadQueueOnly);

            MenuItem jpgsOnlyItem = mMenu.findItem(R.id.view_jpg_only);
            jpgsOnlyItem.setChecked(isShowJpgOnly);
            jpgsOnlyItem.setVisible(mCamera.imageListHasMixedFormats());

            MenuItem rawOnlyItem = mMenu.findItem(R.id.view_raw_only);
            rawOnlyItem.setChecked(isShowRawOnly);
            rawOnlyItem.setVisible(mCamera.imageListHasMixedFormats());

            MenuItem dowloadedOnlyItem = mMenu.findItem(R.id.view_downloaded_only);
            dowloadedOnlyItem.setChecked(isShowDownloadedOnly);

            MenuItem shareItem = mMenu.findItem(R.id.share);
            shareItem.setVisible(isFlaggedOnly);

            MenuItem searchItem = mMenu.findItem(R.id.search);
            searchItem.setVisible(!mCamera.hasFilter(DownloadService.DownloadQueueFilter) &&
                                  !mCamera.hasFilter(FilteredImageList.FlaggedFilter));


            CameraData cameraData = mCamera.getCameraData();
            boolean multyStorage =  cameraData != null && cameraData.storages.size() > 1;


            int currentStorageIndex = mCamera.getCurrentStorageIndex();
            String syncText = getString(R.string.sync_images);
            MenuItem syncItem = mMenu.findItem(R.id.sync_images_1);
            syncItem.setTitle(multyStorage ? cameraData.storages.get(0).displayName :  syncText);
            syncItem.setVisible(!mCamera.hasFilter(DownloadService.DownloadQueueFilter));
            syncItem.setCheckable(multyStorage);
            syncItem.setChecked(multyStorage && currentStorageIndex == 0);
            if(multyStorage) {
                syncItem.setIcon(null);
            }

            syncItem = mMenu.findItem(R.id.sync_images_2);
            syncItem.setVisible(multyStorage && !mCamera.hasFilter(DownloadService.DownloadQueueFilter));
            syncItem.setTitle(multyStorage ? cameraData.storages.get(1).displayName :  syncText);
            syncItem.setCheckable(multyStorage);
            syncItem.setChecked(multyStorage && currentStorageIndex == 1);
            if(multyStorage) {
                syncItem.setIcon(null);
            }

            MenuItem proccessDownloadQueueItem = mMenu.findItem(R.id.proccess_download_queue);
            proccessDownloadQueueItem.setVisible(isShowDownloadQueueOnly);
            MenuItem cancelDownloadQueueItem = mMenu.findItem(R.id.cancel_download_queue);
            cancelDownloadQueueItem.setVisible(isShowDownloadQueueOnly);
            MenuItem shutdownWhenDoneItem = mMenu.findItem(R.id.shutdown_when_download_done_queue);
            shutdownWhenDoneItem.setVisible(isShowDownloadQueueOnly);
            shutdownWhenDoneItem.setChecked(DownloadService.shutCameraDownWhenDone());
        }
    }

    private void removeFilters() {
        mSearchView.setQuery("", false);
        mSearchView.setIconified(true);
        mCamera.setImageFilter(null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int itemId = item.getItemId();

        if(mCamera.getImageList() == null) {
            if(BuildConfig.DEBUG) Logger.debug(TAG, "No images loaded yet.");
            return false;
        }

        if(item.getGroupId() == R.id.camera_menu_list) {
            for (CameraData cameraData :  mCamera.getRegisteredCameras()) {
                if(cameraData.hashCode == itemId) {
                    setCurrentCamera(cameraData.cameraId);
                    return true;
                }
            }
            return true;
        }

        switch (itemId) {
            case R.id.view_downloaded_only:
            case R.id.view_downloads_only:
            case R.id.view_flagged_only:
            case R.id.view_jpg_only:
            case R.id.view_raw_only:
                item.setChecked(!item.isChecked());
                showView(item.isChecked(), itemId);
                return true;
            case R.id.sync_images_1:
            case R.id.sync_images_2:
                removeFilters();
                int currentStorageIndex = mCamera.getCurrentStorageIndex();
                int newStorageIndex = itemId == R.id.sync_images_1 ? 0 : 1;
                syncPictureList(newStorageIndex, currentStorageIndex != newStorageIndex,
                        true, currentStorageIndex == newStorageIndex, null);
                return true;
            case R.id.shutdown_when_download_done_queue:
                DownloadService.toggleShutCameraDownWhenDone();
                return true;
            case R.id.proccess_download_queue:
                DownloadService.processDownloadQueue();
                return true;
            case R.id.cancel_download_queue:
                DownloadService.cancelAllDownloads();
                showView(false, -1);
                return true;
            case R.id.download_selected:
                downloadSelected();
                return true;
            case R.id.clear_selection:
                mAdapter.clearSelection();
                return true;
            case R.id.select_all:
            case R.id.select_no_downloaded:
                selectAllImages(itemId == R.id.select_no_downloaded);
                return true;
            case R.id.share:
                shareFlaggedList();
                return true;
            case R.id.download_jpgs:
                downloadJpgs();
                return true;
            case R.id.settings:
                PreferencesActivity.start(getActivity());
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showView(boolean show, int itemId) {
        if(mSearchView != null) {
            mSearchView.setQuery("", false);
            mSearchView.setIconified(true);
        }

        mCamera.setImageFilter(null);
        removeScreenOnFlag();

        int emptyViewText = R.string.no_pictures_in_camera;
        if(show) {
            switch (itemId) {
                case R.id.view_downloads_only:
                    mCamera.setImageFilter(DownloadService.DownloadQueueFilter);
                    addScreenOnFlag();
                    emptyViewText = R.string.all_pictures_transferred;
                    break;
                case R.id.view_downloaded_only:
                    mCamera.setImageFilter(FilteredImageList.DownloadedFilter);
                    emptyViewText = R.string.no_pictures_transferred;
                    break;
                case R.id.view_flagged_only:
                    mCamera.setImageFilter(FilteredImageList.FlaggedFilter);
                    emptyViewText = R.string.no_flagged_pictures;
                    break;
                case R.id.view_raw_only:
                    mCamera.setImageFilter(FilteredImageList.RawFilter);
                    emptyViewText = R.string.no_raw_pictures;
                    break;
                case R.id.view_jpg_only:
                    mCamera.setImageFilter(FilteredImageList.JpgFilter);
                    emptyViewText = R.string.no_jpg_pictures;
                    break;
            }
        } else if (itemId != DEFAULT_MULTIFORMAT_FILTER && mCamera.imageListHasMixedFormats()) {
            showView(true, DEFAULT_MULTIFORMAT_FILTER);
        }

        updateEmptyViewText(emptyViewText);

        mAdapter.notifyDataSetChanged();
        updateMenuItems();
        updateActionBarTitle();
    }

    private void updateProgressText(String text) {
        if(text != null) {
            mProgressLabel.setText(text);
            mProgressLabel.setVisibility(View.VISIBLE);
        } else {
            mProgressLabel.setVisibility(View.GONE);
        }
        mEmptyViewLabel.setVisibility(View.GONE);
    }

    private void updateProgressText(int textId) {
        if(textId != 0) {
            updateProgressText(getString(textId));
        } else {
            updateProgressText(null);
        }
    }

    private void updateEmptyViewText(String text) {
        ImageList imageList = mCamera.getImageList();
        if(imageList != null) {
            if (imageList.length() == 0) {
                if(text != null && text.length() > 0) {
                    mEmptyViewLabel.setText(text);
                } else {
                    mEmptyViewLabel.setText("");
                }
                mEmptyViewLabel.setVisibility(View.VISIBLE);
                mSwipeRefreshLayout.setVisibility(View.GONE);
            } else {
                mEmptyViewLabel.setVisibility(View.GONE);
                mSwipeRefreshLayout.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateEmptyViewText(int stringResourceId) {
        updateEmptyViewText(getString(stringResourceId));
    }

    private void downloadJpgs() {
        downloadJpgs(false);
    }

    /*package*/ void downloadJpgs(boolean forceRefresh) {


        List<ImageData> enqueue = getDownloadList();
        if ((mNeedUpdateImageList && (enqueue == null || enqueue.size() == 0)) || forceRefresh) {
            syncPictureList(mCamera.getCurrentStorageIndex(), true, false, true,
                    new OnRefreshDoneListener() {
                        @Override
                        public void onRefreshDone() {
                            showView(true, -1);
                            addToDownloadQueue(getDownloadList());
                        }
                    });
        } else {
            addToDownloadQueue(enqueue);
        }
    }

    private void addToDownloadQueue(List<ImageData> enqueue) {
        if (enqueue != null && enqueue.size() > 0) {
            Toast.makeText(this.getActivity(), "Transferring " + enqueue.size() + " pictures", Toast.LENGTH_LONG).show();
            DownloadService.setInBatchDownload(false);
            for (ImageData imageData : enqueue) {
                DownloadService.addDownloadQueue(imageData);
            }
            DownloadService.setInBatchDownload(true);
            showView(true, R.id.view_downloads_only);
        } else {
            Toast.makeText(this.getActivity(), R.string.no_new_images_to_transfer, Toast.LENGTH_LONG).show();
        }
        DownloadService.processDownloadQueue();
    }

    private List<ImageData> getDownloadList() {
        if(mCamera.isFiltered() && !mCamera.hasFilter(FilteredImageList.JpgFilter) &&
                !mCamera.hasFilter(FilteredImageList.FlaggedFilter)) {
            showView(false, -1);
        }

        boolean includeRaw = false;
        ImageList imageList = mCamera.getCurrentStorage().getImageList();
        if(mCamera.hasFilter(FilteredImageList.FlaggedFilter)) {
            // If downloaded flagged images use only the flagged ones include raw.
            imageList = mCamera.getImageList();
            includeRaw = true;
        }

        List<ImageData> enqueue = new ArrayList<>();
        if(imageList != null) {
            for (int c = imageList.length() - 1; c >= 0; c--) {
                ImageData imageData = imageList.getImage(c);
                if ((includeRaw || !imageData.isRaw) && imageData.getGalleryId() == 0) {
                    DownloadService.DownloadEntry downloadEntry = DownloadService.findDownloadEntry(imageData);
                    if (downloadEntry == null) {
                        enqueue.add(imageData);
                    }
                }
            }
        }
        return  enqueue;
    }

    private void shareFlaggedList() {
        ImageList imageList = mCamera.getImageList();
        String text = imageList.getFlaggedList();

        Intent sharingIntent = new Intent(android.content.Intent.ACTION_SEND);
        sharingIntent.setType("text/plain");
        sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, R.string.flagged_images);
        sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(sharingIntent, getResources().getString(R.string.share_in)));
    }

    private void selectAllImages(boolean ignoreAlreadyDownloaded) {
        ImageList imageList = mCamera.getImageList();
        mAdapter.startBulkOperatio();
        mAdapter.clearSelection();
        for(int c = 0; c < imageList.length(); c++) {
            ImageData imageData = imageList.getImage(c);
            if(!ignoreAlreadyDownloaded || !imageData.existsOnLocalStorage()) {
                mAdapter.selectItem(imageData);
            }
        }
        mAdapter.finishBulkOperation();
    }

    private void downloadSelected() {
        List<ImageData> selectedImages = mAdapter.getSelectedItems();
        for(ImageData imageData : selectedImages) {
            DownloadService.addDownloadQueue(imageData);
        }

        Toast.makeText (getActivity(), String.format(getString(R.string.added_to_download_queue), selectedImages.size()), Toast.LENGTH_LONG).show();
        mAdapter.clearSelection();
    }

    private void showNoConnectedDialog(final String cameraID) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert);

        builder.setTitle(getString(R.string.connection_error))
                .setMessage(Html.fromHtml(getString(R.string.camera_not_connected)))
                .setPositiveButton(getString(R.string.wifi_settings), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        getActivity().finish();
                    }
                })
                .setCancelable(true)
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        getActivity().finish();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert);

                if(mCamera.getRegisteredCameras().size() > 0) {
                    builder.setNeutralButton(R.string.load_cache, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (cameraID != null) {
                                setCurrentCamera(cameraID);
                            } else {
                                syncPictureList(true);
                            }
                        }
                    });
                }

                builder.show();
    }

    private void syncPictureList(boolean loadImageListOnly) {
        syncPictureList(-1, loadImageListOnly, true);
    }

    private void loadPictureList() {
        syncPictureList(-1, false, false, true, null);
    }

    private void syncPictureList(int storageIndex, boolean loadImageListOnly, boolean showProgressBar) {
        syncPictureList(storageIndex, loadImageListOnly, showProgressBar, false, null);
    }

    private void syncPictureList(int storageIndex, boolean loadImageListOnly, boolean showProgressBar,
                                boolean connectionNeeded, OnRefreshDoneListener refreshDoneListener) {
        if (mImageListTask == null) {
            if (showProgressBar) {
                mDontShowProgressBar = false;
                TaskExecutor.executeOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!mDontShowProgressBar) {
                            mProgressBar.setVisibility(View.VISIBLE);
                            mSwipeRefreshLayout.setVisibility(View.GONE);
                        }
                    }
                }, 300);
            }
            mImageListTask = new ImageListTask(refreshDoneListener);
            mImageListTask.execute(loadImageListOnly, storageIndex, null, connectionNeeded);
        }
    }

    private void setCurrentCamera(String cameraId) {
        if(mImageListTask == null) {
            mProgressBar.setVisibility(View.VISIBLE);
            mSwipeRefreshLayout.setVisibility(View.GONE);

            mImageListTask = new ImageListTask(null);
            mImageListTask.execute(false, -1, cameraId);
        }
    }

    @Override
    public void onDownloadFinished(ImageData imageData, long donloadId, int remainingDownloads,
                                   int downloadCount, int errorCount, boolean wasCanceled) {
        Logger.debug(TAG, "onDownloadFinished: " + donloadId + " Remaining: " + remainingDownloads);
        if(mCamera.isFiltered()) {
            if(mCamera.hasFilter(FilteredImageList.DownloadedFilter) && mCamera.getImageList() instanceof FilteredImageList) {
                    ((FilteredImageList)mCamera.getImageList()).rebuildFilter();
            }
            mAdapter.notifyDataSetChanged();
        }
        updateActionBarTitle();
        if(remainingDownloads == 0) {
            if(mCamera.hasFilter(DownloadService.DownloadQueueFilter)) {
                String viewText;
                if(downloadCount > 0 && errorCount > 0) {
                    viewText = String.format(getString(R.string.download_done_with_fails_notification_text),
                            downloadCount, errorCount);
                } else if (downloadCount > 0) {
                    viewText = String.format(getString(R.string.download_done_notification_text), downloadCount);
                } else if (errorCount > 0) {
                    viewText = String.format(getString(R.string.download_done_failed_notification_text), errorCount);
                } else {
                    viewText = getString(R.string.all_pictures_transferred);
                }
                updateEmptyViewText(viewText);
            }
            removeScreenOnFlag();
        }
    }

    private void removeScreenOnFlag() {
        Window window = getActivity().getWindow();
        int flags = window.getAttributes().flags;
        if((flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0) {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Logger.DEBUG) Logger.debug(TAG, "removeScreenOnFlag");
        }
    }

    private void addScreenOnFlag() {
        Window window = getActivity().getWindow();
        int flags = window.getAttributes().flags;
        if((flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) == 0) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if (Logger.DEBUG) Logger.debug(TAG, "addScreenOnFlag");
        }
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onDownloadProgress(ImageData imageData, long donloadId, int progress) {
        if (mCamera.hasFilter(DownloadService.DownloadQueueFilter)) {
            CameraData cameraData = mCamera.getCameraData();
            if (cameraData != null) {
                Activity activity = getActivity();
                if (activity != null) {
                    ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
                    actionBar.setSubtitle(String.format("%s - %s (%d)",
                            imageData.fileName, progress + "%", mCamera.imageCount()));
                }
            }
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mSearchView.setQuery("", false);
        mSearchView.setIconified(true);
        if(mCamera.getImageList() != null) {
            if(query != null && query.length() > 0) {
                mCamera.setImageFilterText(query);
                mAdapter.notifyDataSetChanged();
                updateMenuItems();
            }
        }
        return true;
    }

    private boolean isSearching;

    @Override
    public boolean onQueryTextChange(String newText) {
        if(mCamera.getImageList() != null && !TextUtils.isEmpty(newText)) {
            mCamera.setImageFilterText(newText);
            mAdapter.notifyDataSetChanged();
            updateMenuItems();
            isSearching = true;
        }
        return false;
    }

    @Override
    public boolean onClose() {
        if(isSearching) {
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    showView(false, -1);
                }
            });
            isSearching = false;
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        return false;
    }

    @Override
    public void onRefresh() {
        mCamera.setCameraData(null);
        syncPictureList(mCamera.getCurrentStorageIndex(), false, false, true, null);
    }

    @Override
    public void onCameraChange(CameraChange change) {
        if(change.isChanged(CameraChange.CHANGED_STORAGE)) {
            boolean added = false;
            if(change.filepath != null && change.filepath.length() > 0) {
                ImageData imageData = mCamera.addImageToStorage(change.storage, change.filepath);
                added = imageData != null;
                if(added) {
                    if ((imageData.isRaw && mCamera.getPreferences().autoDownloadRaw()) ||
                            (!imageData.isRaw && mCamera.getPreferences().autoDownloadJpg())) {
                            DownloadService.addDownloadQueue(imageData);
                    }
                    mAdapter.notifyDataSetChanged();
                }
            }
            mNeedUpdateImageList = !added;
        }
        if(BuildConfig.DEBUG) Logger.debug(TAG, change.toString());
    }

    /*package*/ void rebuildCameraListMenu() {
        if (mMenu != null) {
            mMenu.removeGroup(R.id.camera_menu_list);
        }
    }

    @Override
    public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)mCameraActionButton.getLayoutParams();
        layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin,layoutParams.rightMargin,
                layoutParams.bottomMargin + insets.getSystemWindowInsetBottom());
        mCameraActionButton.setLayoutParams(layoutParams);
        return insets.consumeSystemWindowInsets();
    }

    private volatile static Thread cacheThread = null;
    private volatile boolean cancelCacheThread;
    private void cacheThumbnails(final ImageList imageList) {
        if (mCamera.isConnected() && cacheThread == null) {
            cacheThread = TaskExecutor.executeOnNewThread(new Runnable() {
                    @Override
                    public void run() {
                        for (int c = 0; c < imageList.length(); c++) {
                            if(cancelCacheThread) {
                                if(BuildConfig.DEBUG) Logger.debug(TAG, "cancelCacheThread");
                                break;
                            }
                            ImageData imageData = imageList.getImage(c);
                            String url = imageData.getThumbUrl();
                            try {

                                mImageFetcher.downloadUrlToCacheIfNeeded(url);
                            } catch (Exception e) {
                                Logger.warning(TAG, "cacheThumbnails", e);
                            }
                        }
                        cacheThread = null;
                        cancelCacheThread = false;
                    }
                });
        } else if(BuildConfig.DEBUG && cacheThread != null) {
            Logger.debug(TAG, "Cache thread is running");
        }
    }

    /**
     * The main adapter that backs the GridView. This is fairly standard except the number of
     * columns in the GridView is used to create a fake top row of empty views as we use a
     * transparent ActionBar and don't want the real top row of images to start off covered by it.
     */
    private class ImageAdapter extends BaseAdapter {

        private final Context mContext;
        private int mItemHeight = 0;
        private int mNumColumns = 0;
        private int mActionBarHeight = 0;
        private GridView.LayoutParams mImageViewLayoutParams;

        private ArrayList<ImageData> mSelectionList = new ArrayList<>();

        private volatile int mBulkCount;

        public ImageAdapter(Context context) {
            super();
            mContext = context;
            mImageViewLayoutParams = new GridView.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            // Calculate ActionBar height
            TypedValue tv = new TypedValue();
            if (context.getTheme().resolveAttribute(
                    android.R.attr.actionBarSize, tv, true)) {

                int statusBarHeight = 0;
                int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
                if (resourceId > 0) {
                    statusBarHeight = getResources().getDimensionPixelSize(resourceId);
                }

                mActionBarHeight = TypedValue.complexToDimensionPixelSize(
                        tv.data, context.getResources().getDisplayMetrics()) + statusBarHeight;

               mSwipeRefreshLayout.setProgressViewEndTarget(false, mActionBarHeight + 16);
               mSwipeRefreshLayout.setProgressViewOffset(false, statusBarHeight + 8, mActionBarHeight + 8);
            }
        }

        public void startBulkOperatio() {
            mBulkCount++;
        }

        public boolean isInBulkOperation() {
            return mBulkCount > 0;
        }

        public void finishBulkOperation() {
            if(mBulkCount > 0) {
                if(--mBulkCount == 0) {
                    updateMenuItems();
                    notifyDataSetChanged();
                    updateActionBarTitle();
                }
            }
        }

        public void selectItem(ImageData imageData) {
            mSelectionList.add(imageData);
            if(!isInBulkOperation()) {
                updateMenuItems();
                notifyDataSetChanged();
                updateActionBarTitle();
            }
        }
        public void selectItem(int position) {
            selectItem(getImageDataItem(position));
        }

        public boolean isItemSelected(ImageData imageData) {
            return mSelectionList.contains(imageData);
        }

        public boolean isItemSelected(int position) {
            return isItemSelected(getImageDataItem(position));
        }

        public void toggleItemSelection(ImageData imageData) {
            if(isItemSelected(imageData)) {
                removeItemFromSelection(imageData);
            } else {
                selectItem(imageData);
            }
        }

        public void toggleItemSelection(int position) {
            if(isItemSelected(position)) {
                removeItemFromSelection(position);
            } else {
                selectItem(position);
            }
        }

        public void removeItemFromSelection(ImageData imageData) {
            mSelectionList.remove(imageData);
            if(!isInBulkOperation()) {
                updateMenuItems();
                notifyDataSetChanged();
                updateActionBarTitle();
            }
        }

        public void removeItemFromSelection(int position) {
            removeItemFromSelection(getImageDataItem(position));
        }

        public void clearSelection() {
            mSelectionList.clear();
            if(!isInBulkOperation()) {
                updateMenuItems();
                notifyDataSetChanged();
                updateActionBarTitle();
            }
        }

        public boolean isInSelectMode() {
            return  mSelectionList.size() > 0;
        }

        public List<ImageData> getSelectedItems() {
            return mSelectionList;
        }

        public ImageData getImageDataItem(int position) {
            return position < mNumColumns ?
                    null :  mCamera.getImageList().getImage(position - mNumColumns);
        }

        @Override
        public int getCount() {
            // If columns have yet to be determined, return no items
            if (getNumColumns() == 0) {
                return 0;
            }

            // Size + number of columns for top empty row
            return mCamera.imageCount() + mNumColumns;
        }

        @Override
        public Object getItem(int position) {
            ImageData imageData = getImageDataItem(position);
            return imageData != null ? imageData.getThumbUrl() : null;
        }

        @Override
        public long getItemId(int position) {
            return position < mNumColumns ? 0 : position - mNumColumns;
        }

        @Override
        public int getViewTypeCount() {
            // Two types of views, the normal ImageView and the top row of empty views
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return (position < mNumColumns) ? 1 : 0;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            //BEGIN_INCLUDE(load_gridview_item)
            // First check if this is the top row
            if (position < mNumColumns) {
                if (convertView == null) {
                    convertView = new View(mContext);
                }

                // Set empty view with height of ActionBar
                convertView.setLayoutParams(new AbsListView.LayoutParams(
                        LayoutParams.MATCH_PARENT, mActionBarHeight));
                return convertView;
            }

            // Now handle the main ImageView thumbnails
            final ImageThumbWidget imageThumb;
            if (convertView == null) { // if it's not recycled, instantiate and initialize
                imageThumb = new ImageThumbWidget(mContext);
                imageThumb.init(mImageViewLayoutParams);
            } else { // Otherwise re-use the converted view
                imageThumb = (ImageThumbWidget) convertView;
            }

            final ImageView imageView = imageThumb.getmImageView();

            // Check the height matches our calculated column width
            if (imageView.getLayoutParams().height != mItemHeight) {
                imageView.setLayoutParams(mImageViewLayoutParams);
            }

            // Finally load the image asynchronously into the ImageView, this also takes care of
            // setting a placeholder image while the background thread runs
            ImageList imageList = mCamera.getImageList();
            final ImageData imageData = imageList.getImage(position - mNumColumns);
            mImageFetcher.loadImage(imageData.getThumbUrl(), imageData, imageView, new ImageFetcher.OnImageLoadedListener() {
                public void onImageLoaded(boolean success) {
                    Drawable drawable = imageView.getDrawable();
                    if (success && drawable instanceof BitmapDrawable) {
                        imageData.setThumbBitmap(((BitmapDrawable) drawable).getBitmap());
                    }
                }
            });

            if(isItemSelected(imageData)) {
                imageThumb.showAsSelected();
            } else {
                imageThumb.hideBatch();
            }

          
            return imageThumb;
            //END_INCLUDE(load_gridview_item)
        }

        /**
         * Sets the item height. Useful for when we know the column width so the height can be set
         * to match.
         *
         * @param height
         */
        public void setItemHeight(int height) {
            if (height == mItemHeight) {
                return;
            }
            mItemHeight = height;
            mImageViewLayoutParams =
                    new GridView.LayoutParams(LayoutParams.MATCH_PARENT, mItemHeight);
            mImageFetcher.setImageSize(height);
            notifyDataSetChanged();
        }

        public int getNumColumns() {
            return mNumColumns;
        }

        public void setNumColumns(int numColumns) {
            mNumColumns = numColumns;
        }
    }

    private void updateActionBarTitle() {
        CameraData cameraData = mCamera.getCameraData();
        if(cameraData != null) {
            Activity activity = getActivity();
            if(activity != null) {
                ActionBar actionBar = ((AppCompatActivity)activity).getSupportActionBar();
                actionBar.setTitle(cameraData.getDisplayName());
                StorageData storageData = mCamera.getCurrentStorage();
                if(storageData.getImageList() != null) {
                    if (mAdapter.isInSelectMode()) {
                        actionBar.setSubtitle(String.format("%d SELECTED", mAdapter.getSelectedItems().size()));
                    } else if (mCamera.hasFilter(DownloadService.DownloadQueueFilter)) {
                        actionBar.setSubtitle(String.format("DOWNLOAD QUEUE (%d)", mCamera.imageCount()));
                    } else if (mCamera.hasFilter(FilteredImageList.DownloadedFilter)) {
                        actionBar.setSubtitle(String.format("%s (%d/%s) - Downloaded", storageData.name, mCamera.imageCount(), storageData.format).toUpperCase());
                    } else {
                        actionBar.setSubtitle(String.format("%s (%d/%s)", storageData.name, mCamera.imageCount(), storageData.format).toUpperCase());
                    }
                }
            }
        }
    }

    public interface OnRefreshDoneListener {
        void onRefreshDone();
    }

    private  class ImageListTask extends AsyncTask<Object, Object, ImageList> implements Camera.OnWifiConnectionAttemptListener {

        private static final int PROGRESS_CONNECTED = 0;
        private static final int PROGRESS_CONNECTING = 1;
        private static final int PROGRESS_LOADING_LOCAL_DATA = 2;
        private static final int PROGRESS_LOADING_PICTURE_LIST = 3;

        private OnRefreshDoneListener refreshDoneListener;

        private static final String TAG = "ImageListTask";

        public ImageListTask(OnRefreshDoneListener refreshDoneListener) {
            this.refreshDoneListener = refreshDoneListener;
            updateProgressText(R.string.connecting);
        }

        private void debug(String message) {
            if (BuildConfig.DEBUG) Logger.debug(TAG, message);
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            int progressType = (int)values[0];
            switch (progressType) {
                case PROGRESS_CONNECTED:
                    CameraData cameraData = (CameraData)values[1];
                    Toast.makeText(ImageGridFragment.this.getContext(),
                            String.format(getString(R.string.connected_to), cameraData.model, cameraData.serialNo),
                            Toast.LENGTH_SHORT).show();
                    updateProgressText(R.string.loading_picture_list);
                    updateActionBarTitle();
                    break;
                case PROGRESS_LOADING_PICTURE_LIST:
                    updateProgressText(R.string.loading_picture_list);
                    break;
                case PROGRESS_CONNECTING:
                    updateProgressText(String.format(getString(R.string.connecting_to), values[1]));
                    break;
                case PROGRESS_LOADING_LOCAL_DATA:
                    updateProgressText(R.string.loading_local_data);
                    break;

            }
        }

        @Override
        protected ImageList doInBackground(Object... params) {

            boolean loadImageListOnly = params.length > 0 ? (Boolean) params[0] : false;
            int newStorageIndex = params.length > 1 ? (int) params[1] : -1;
            String cameraId = params.length > 2 && params[2] != null ? String.valueOf(params[2]) : null;
            boolean connectionNeeded = params.length > 3 ? (Boolean) params[3] : false;

            CameraData cameraData;
            if (mCamera.getCameraData() != null && loadImageListOnly) {
                cameraData = mCamera.getCameraData();
                publishProgress(PROGRESS_LOADING_PICTURE_LIST, cameraData);
            } else {
                cameraData = mCamera.connect(cameraId, this);
                if (mCamera.isConnected()) {
                    publishProgress(PROGRESS_CONNECTED, cameraData);
                }
            }

            if(connectionNeeded && !mCamera.isConnected()) {
                return null;
            }

            ImageList imageList = null;
            if (newStorageIndex >= 0 && newStorageIndex != mCamera.getCurrentStorageIndex()) {
                mCamera.setCurrentStorageIndex(newStorageIndex);
                if(loadImageListOnly) {
                    imageList = mCamera.getCurrentStorage().getImageList();
                    mDontShowProgressBar = imageList != null;
                }                
            }

            if (BuildConfig.DEBUG) {
                Logger.debug(TAG, "Load image list START");
            }

            boolean needToLoadLocalData = !loadImageListOnly;
            if (mNeedUpdateImageList || imageList == null || imageList.length() == 0) {
                imageList = mCamera.loadImageList();
                //cacheThumbnails(imageList);
                needToLoadLocalData = true;
            }

            if (imageList != null && needToLoadLocalData) {
                publishProgress(PROGRESS_LOADING_LOCAL_DATA);

                Map<String, String> downloadedList = loadDownloadedList(imageList);
                for (int c = 0; c < imageList.length(); c++) {
                    ImageData imageData = imageList.getImage(c);
                    imageData.readData();

                    String id = downloadedList.get(imageData.uniqueFileName);
                    if (id != null) {
                        imageData.setLocalStorageUri(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id));
                    }
                }
                downloadedList.clear();
                DownloadService.loadQueueFromFile(imageList, cameraData);
            }

            if (BuildConfig.DEBUG) {
                Logger.debug(TAG, "Load image list END");
            }

            return imageList;
        }

        private Map<String, String> loadDownloadedList(ImageList imageList) {
            final String orderByMediaStoreCursor = MediaStore.Images.Media.DATE_TAKEN;
            final String[] projectionMediaStoreCursor = new String[] {
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DISPLAY_NAME
            };

            StringBuilder whereSb = new StringBuilder();
            whereSb.append(MediaStore.Images.Media.DISPLAY_NAME);
            whereSb.append(" IN (");
            for(int c = 0; c < imageList.length(); c++) {
                whereSb.append("'");
                whereSb.append(imageList.getImage(c).uniqueFileName);
                whereSb.append("'");
                if(c < imageList.length() - 1) {
                    whereSb.append(",");
                }
            }
            whereSb.append(")");

            Cursor cursor = MediaStore.Images.Media.query(
                    getContext().getContentResolver(),
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projectionMediaStoreCursor,
                    whereSb.toString(),
                    orderByMediaStoreCursor);

            whereSb.setLength(0);

            HashMap<String, String> table = new HashMap<>();

            while(cursor.moveToNext()) {
                int idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                int displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);

                String id =  cursor.getString(idIndex);
                String uniqueName =  cursor.getString(displayNameIndex);
                table.put(uniqueName, id);
            }

            cursor.close();

            return table;
        }

        @Override
        protected void onPostExecute(ImageList imageList) {

            String from = "cache";
            String cameraDisplayName = null;
            CameraData cameraData = mCamera.getCameraData();
            if(cameraData != null && cameraData.model != null && mCamera.isConnected()) {
                cameraDisplayName = cameraData.getDisplayName();
                from = cameraDisplayName + " " + mCamera.getCurrentStorage().displayName;
            }

            if(mCamera.isConnected()) {
                mCamera.getController().addCameraChangeListener(ImageGridFragment.this);
            }

            mSwipeRefreshLayout.setRefreshing(false);
            mProgressBar.setVisibility(View.GONE);
            mAdapter.notifyDataSetChanged();

            if(imageList != null) {
                mNeedUpdateImageList = false;
                String msg = String.format(getString(R.string.pictures_loaded), mCamera.imageCount(), from);
                Toast.makeText(ImageGridFragment.this.getContext(),  msg, Toast.LENGTH_LONG).show();
                mSwipeRefreshLayout.setVisibility(View.VISIBLE);
                mGridView.smoothScrollToPosition(0);
                if(refreshDoneListener != null) {
                    refreshDoneListener.onRefreshDone();
                }
                if(imageList.hasMixedFormats) {
                    showView(true, DEFAULT_MULTIFORMAT_FILTER);
                }
                DownloadService.setShutCameraDownWhenDone(mCamera.getPreferences().shutdownAfterTransfer());
            } else {
                CameraData camera = mCamera.getCameraData();
                showNoConnectedDialog(camera != null ? camera.cameraId : null);
                mCamera.setCameraData(null);
            }

            updateActionBarTitle();
            updateMenuItems();

            mCameraActionButton.setVisibility(mCamera.isConnected() ||  BuildConfig.DEBUG ? View.VISIBLE : View.GONE);

            updateProgressText(null);
            mImageListTask = null;
        }

        @Override
        public void onWifiConnectionAttempt(String ssid) {
            publishProgress(PROGRESS_CONNECTING, ssid);
        }
    }

}
