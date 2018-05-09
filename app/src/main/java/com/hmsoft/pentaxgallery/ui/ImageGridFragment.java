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

import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Html;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.Toast;

import com.hmsoft.pentaxgallery.BuildConfig;
import com.hmsoft.pentaxgallery.R;
import com.hmsoft.pentaxgallery.camera.ControllerFactory;
import com.hmsoft.pentaxgallery.camera.model.ImageList;
import com.hmsoft.pentaxgallery.camera.model.CameraData;
import com.hmsoft.pentaxgallery.camera.model.ImageData;
import com.hmsoft.pentaxgallery.camera.model.ImageListData;
import com.hmsoft.pentaxgallery.camera.model.StorageData;
import com.hmsoft.pentaxgallery.data.provider.DownloadQueue;
import com.hmsoft.pentaxgallery.data.provider.Images;
import com.hmsoft.pentaxgallery.util.TaskExecutor;
import com.hmsoft.pentaxgallery.util.Utils;
import com.hmsoft.pentaxgallery.util.cache.CacheUtils;
import com.hmsoft.pentaxgallery.util.image.ImageFetcher;
import com.hmsoft.pentaxgallery.util.image.ImageCache;
import com.hmsoft.pentaxgallery.util.image.ImageRotatorFetcher;

/**
 * The main fragment that powers the ImageGridActivity screen. Fairly straight forward GridView
 * implementation with the key addition being the UrlImageWorker class w/ImageCache to load children
 * asynchronously, keeping the UI nice and smooth and caching thumbnails for quick retrieval. The
 * cache is retained over configuration changes like orientation change so the images are populated
 * quickly if, for example, the user rotates the device.
 */
public class ImageGridFragment extends Fragment implements AdapterView.OnItemClickListener,
        DownloadQueue.OnDowloadFinishedListener,
        SearchView.OnQueryTextListener,
        ActionBar.OnNavigationListener,
        SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "ImageGridFragment";
    private static final String IMAGE_CACHE_DIR = "thumbs";

    private static ImageListTask mImageListTask = null;


    private int mImageThumbSize;
    private int mImageThumbSpacing;
    private ImageAdapter mAdapter;
    private ImageFetcher mImageFetcher;
    private ProgressBar mProgressBar;
    private GridView mGridView;
    private Menu mMenu;
    private SearchView mSearchView;
    private SwipeRefreshLayout mSwipeRefreshLayout;

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
        CacheUtils.init();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {


        final View v = inflater.inflate(R.layout.image_grid_fragment, container, false);
        mGridView = v.findViewById(R.id.gridView);
        mProgressBar = v.findViewById(R.id.progressbarGrid);

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
                            final int numColumns = (int) Math.floor(
                                    mGridView.getWidth() / (mImageThumbSize + mImageThumbSpacing));
                            if (numColumns > 0) {
                                final int columnWidth =
                                        (mGridView.getWidth() / numColumns) - mImageThumbSpacing;
                                mAdapter.setNumColumns(numColumns);
                                mAdapter.setItemHeight(columnWidth);
                                if (BuildConfig.DEBUG) {
                                    Log.d(TAG, "onCreateView - numColumns set to " + numColumns);
                                }
                                mGridView.getViewTreeObserver()
                                        .removeOnGlobalLayoutListener(this);
                            }
                        }
                    }
                });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        mImageFetcher.setCancel(false);
        mImageFetcher.setExitTasksEarly(false);
        mAdapter.notifyDataSetChanged();
        if(Images.getImageList() == null) {
            syncPictureList(false);
        } else {
            mProgressBar.setVisibility(View.GONE);
        }
        DownloadQueue.setOnDowloadFinishedListener(this);
        updateActionBarTitle();
    }

    @Override
    public void onPause() {
        super.onPause();

        TaskExecutor.executeOnSingleThreadExecutor(new Runnable() {
            @Override
            public void run() {
                DownloadQueue.saveToCache();
            }
        });

        mImageFetcher.setPauseWork(false);
        mImageFetcher.setExitTasksEarly(true);
        mImageFetcher.flushCache();
        CacheUtils.flush();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mImageFetcher.closeCache();
        CacheUtils.close();
        if(mImageListTask != null) {
            mImageListTask.cancel(true);
            mImageListTask = null;
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        startDetailActivity(v, (int) id);
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


        MenuItem downloadFilter = menu.findItem(R.id.downloadFilter);
        downloadFilter.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItem syncItem = menu.findItem(R.id.sync_images_1);
        syncItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        syncItem = menu.findItem(R.id.sync_images_2);
        syncItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem searchItem = menu.findItem(R.id.search);
        searchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        searchItem.setActionView(R.layout.search_view);
        MenuItem clearSearchItem = menu.findItem(R.id.clear_search);
        clearSearchItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        MenuItem proccessDownloadQueueItem = menu.findItem(R.id.proccessDownloadQueue);
        proccessDownloadQueueItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);


        SearchManager searchManager =
                (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        mSearchView =
                (SearchView) menu.findItem(R.id.search).getActionView();
        mSearchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));

        mSearchView.setOnQueryTextListener(this);

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

            boolean isFilterd = Images.isFiltered();
            boolean isShowDownloadQueueOnly = Images.isShowDownloadQueueOnly();
            boolean isShowDownloadedOnly = Images.isShowDownloadedOnly();


            MenuItem downloadFilterItem = mMenu.findItem(R.id.downloadFilter);
            downloadFilterItem.setVisible(!isFilterd);

            MenuItem downloadsOnlyItem = mMenu.findItem(R.id.view_downloads_only);
            downloadsOnlyItem.setChecked(isShowDownloadQueueOnly);

            MenuItem dowloadedOnlyItem = mMenu.findItem(R.id.view_downloaded_only);
            dowloadedOnlyItem.setChecked(isShowDownloadedOnly);

            MenuItem clearSearchItem = mMenu.findItem(R.id.clear_search);
            clearSearchItem.setVisible(isFilterd && !isShowDownloadQueueOnly);

            MenuItem searchItem = mMenu.findItem(R.id.search);
            searchItem.setVisible(!Images.isShowDownloadQueueOnly());


            CameraData cameraData = Images.getCameraData();
            boolean multyStorage =  cameraData != null && cameraData.storages.size() > 1;


            String syncText = getString(R.string.sync_images);
            MenuItem syncItem = mMenu.findItem(R.id.sync_images_1);
            syncItem.setTitle(multyStorage ? cameraData.storages.get(0).displayName :  syncText);
            if(multyStorage) syncItem.setIcon(null);

            syncItem = mMenu.findItem(R.id.sync_images_2);
            syncItem.setVisible(multyStorage);
            syncItem.setTitle(multyStorage ? cameraData.storages.get(1).displayName :  syncText);
            if(multyStorage) syncItem.setIcon(null);

            MenuItem proccessDownloadQueueItem = mMenu.findItem(R.id.proccessDownloadQueue);
            proccessDownloadQueueItem.setVisible(isShowDownloadQueueOnly);
        }
    }

    private void removeFilters() {
        mSearchView.setQuery("", false);
        mSearchView.setIconified(true);
        Images.setShowDownloadQueueOnly(false);
        Images.setShowDownloadedOnly(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.view_downloaded_only:
            case R.id.view_downloads_only:
                mSearchView.setQuery("", false);
                mSearchView.setIconified(true);
                item.setChecked(!item.isChecked());
                Images.setShowDownloadQueueOnly(item.isChecked() && itemId == R.id.view_downloads_only);
                Images.setShowDownloadedOnly(item.isChecked() && itemId == R.id.view_downloaded_only);
                mAdapter.notifyDataSetChanged();
                updateMenuItems();
                updateActionBarTitle();
                return true;
            case R.id.sync_images_1:
            case R.id.sync_images_2:
                removeFilters();
                int currentStorageIndex = Images.getCurrentStorageIndex();
                int newStorageIndex = itemId == R.id.sync_images_1 ? 0 : 1;
                syncPictureList(newStorageIndex, currentStorageIndex == newStorageIndex, true);
                return true;
            case R.id.clear_search:
                Images.clearFilter();
                updateMenuItems();
                mAdapter.notifyDataSetChanged();
                return true;
            case R.id.proccessDownloadQueue:
                DownloadQueue.processDownloadQueue();
                return true;
            case R.id.about:
                showAboutDialog();
                return true;

        }
        return super.onOptionsItemSelected(item);
    }

    private void showAboutDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert);

        builder.setTitle(R.string.about)
                .setMessage(Html.fromHtml(String.format("<center><br/><p><b>%s</b> by hmrs.cr.</p><p>%s</p><p><i>%s</i></p></center>",
                        getString(R.string.app_name), getString(R.string.intro_message), Utils.VERSION_STRING)))
                .setPositiveButton(android.R.string.ok, null)
                .setIcon(R.mipmap.ic_launcher)
                .show();
    }

    private void showNoConnectedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), android.R.style.Theme_Material_Dialog_Alert);

        builder.setTitle("Connection error")
                .setMessage(Html.fromHtml(String.format("<center><br/><p><b>Can not connect to camera.</b></p><p>Open WiFi settings?</p></center>",
                        getString(R.string.app_name), getString(R.string.intro_message), Utils.VERSION_STRING)))
                .setPositiveButton("WiFi Settings", new DialogInterface.OnClickListener() {
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
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void syncPictureList(boolean ignoreCache) {
        syncPictureList(0, ignoreCache, true);
    }

    private void syncPictureList(int storageIndex, boolean ignoreCache, boolean showProgressBar) {
        if(mImageListTask == null) {
            if(ControllerFactory.DefaultController.connectToCamera()) {
                if(showProgressBar) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    mSwipeRefreshLayout.setVisibility(View.GONE);
                }
                mImageListTask = new ImageListTask();
                mImageListTask.execute(ignoreCache, storageIndex);
            } else {
                showNoConnectedDialog();
            }
        }
    }

    @Override
    public void onDownloadFinished(ImageData imageData, long donloadId, int remainingDownloads, boolean wasCanceled) {
        if(Images.isShowDownloadQueueOnly()) {
            mAdapter.notifyDataSetChanged();
        }
        if(remainingDownloads == 0) {
            ControllerFactory.DefaultController.powerOff(null);
        }
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mSearchView.setQuery("", false);
        mSearchView.setIconified(true);
        Images.setFilter(query);
        mAdapter.notifyDataSetChanged();
        updateMenuItems();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if(newText.length() == 4) {
            int i = Images.getImageList().getFirstMatchIntex(newText);
            if(i >= 0) {
                startDetailActivity(null, i);

            } else {
                Toast.makeText(getActivity(), String.format(getString(R.string.not_found), newText) , Toast.LENGTH_LONG).show();
            }
            mSearchView.setQuery("", false);
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        return false;
    }

    @Override
    public void onRefresh() {
        syncPictureList(Images.getCurrentStorageIndex(), true, false);
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
               mSwipeRefreshLayout.setSize(SwipeRefreshLayout.LARGE);
            }
        }

        @Override
        public int getCount() {
            // If columns have yet to be determined, return no items
            if (getNumColumns() == 0) {
                return 0;
            }

            // Size + number of columns for top empty row
            return Images.imageCount() + mNumColumns;
        }

        @Override
        public Object getItem(int position) {
            return position < mNumColumns ?
                    null : Images.getImageList().getImage(position - mNumColumns).getThumbUrl();
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
            final ImageView imageView;
            if (convertView == null) { // if it's not recycled, instantiate and initialize
                imageView = new RecyclingImageView(mContext);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setLayoutParams(mImageViewLayoutParams);
            } else { // Otherwise re-use the converted view
                imageView = (ImageView) convertView;
            }

            // Check the height matches our calculated column width
            if (imageView.getLayoutParams().height != mItemHeight) {
                imageView.setLayoutParams(mImageViewLayoutParams);
            }

            // Finally load the image asynchronously into the ImageView, this also takes care of
            // setting a placeholder image while the background thread runs
            ImageList imageList = Images.getImageList();

            final ImageData imageData = imageList.getImage(position - mNumColumns);
            mImageFetcher.loadImage(imageData.getThumbUrl(), imageData, imageView);
            //mImageFetcher.loadImageThumb(imageData, imageView);

          
            return imageView;
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
        CameraData cameraData = Images.getCameraData();
        if(cameraData != null) {
            Activity activity = getActivity();
            if(activity != null) {
                ActionBar actionBar = getActivity().getActionBar();
                actionBar.setTitle(cameraData.getDisplayName());
                StorageData storageData = Images.getCurrentStorage();

                if(Images.isShowDownloadQueueOnly()) {
                    actionBar.setSubtitle("DOWNLOAD QUEUE");
                } else if(Images.isShowDownloadedOnly()) {
                    actionBar.setSubtitle(String.format("%s (%d/%s) - Downloaded", storageData.name, Images.imageCount(), storageData.format).toUpperCase());
                } else {
                    actionBar.setSubtitle(String.format("%s (%d/%s)", storageData.name, Images.imageCount(), storageData.format).toUpperCase());
                }
            }
        }
    }

    private class ImageListTask extends AsyncTask<Object, Void, ImageListData> {


        @Override
        protected ImageListData doInBackground(Object... params) {
            boolean ignoreCache = params.length > 0 ? (Boolean)params[0] : false;
            int storageIndex = params.length > 1 ? (int)params[1] : 0;


            CameraData cameraData = Images.getCameraData();
            if(cameraData == null || ignoreCache) {
                cameraData = ControllerFactory.DefaultController.getDeviceInfo(ignoreCache);
            }

            Images.setCameraData(cameraData);
            Images.setCurrentStorageIndex(storageIndex);

            ImageListData imageListResponse = ControllerFactory.DefaultController.getImageList(Images.getCurrentStorage(), ignoreCache);

            if(imageListResponse != null) {
                DownloadQueue.loadFromCache(imageListResponse.dirList, ignoreCache);
                DownloadQueue.loadDownloadedFilesList();
            }

            return imageListResponse;
        }

        @Override
        protected void onPostExecute(ImageListData imageListResponse) {

            if(imageListResponse != null) {
                Images.setImageList(imageListResponse.dirList);
            } else {
                Images.setImageList(null);
            }

            String from = "cache";
            String cameraDisplayName = null;
            CameraData cameraData = Images.getCameraData();
            if(cameraData != null && cameraData.model != null) {
                cameraDisplayName = cameraData.getDisplayName();
                from = cameraDisplayName + " " + Images.getCurrentStorage().displayName;
            }

            updateActionBarTitle();
            updateMenuItems();

            mSwipeRefreshLayout.setRefreshing(false);
            mProgressBar.setVisibility(View.GONE);
            mAdapter.notifyDataSetChanged();

            if(imageListResponse != null) {
                String msg = String.format(getString(R.string.pictures_loaded), Images.imageCount(), from);
                Toast.makeText(ImageGridFragment.this.getContext(),  msg, Toast.LENGTH_LONG).show();
                mSwipeRefreshLayout.setVisibility(View.VISIBLE);
                mGridView.smoothScrollToPosition(0);
            } else {
                showNoConnectedDialog();
            }
            mImageListTask = null;
        }
    }

}
