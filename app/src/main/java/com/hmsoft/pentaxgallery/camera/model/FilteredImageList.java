/*
 * Copyright (C) 2018 Mauricio Rodriguez (ranametal@users.sf.net)
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
 */

package com.hmsoft.pentaxgallery.camera.model;

public class FilteredImageList extends ImageList {

    public static abstract class ImageFilter {
        public abstract boolean  passFilter(ImageData imageData);
        public ImageList getImageList() {
            return null;
        }
    }

    public static final ImageFilter DownloadedFilter = new ImageFilter() {
        @Override
        public boolean passFilter(ImageData imageData) {
            return imageData.existsOnLocalStorage();
        }
    };

    public static final ImageFilter FlaggedFilter = new ImageFilter() {
        @Override
        public boolean passFilter(ImageData imageData) {
            return imageData.isFlagged();
        }
    };

    public static final ImageFilter RawFilter = new ImageFilter() {
        @Override
        public boolean passFilter(ImageData imageData) {
            return imageData.isRaw;
        }
    };

    public static final ImageFilter JpgFilter = new ImageFilter() {
        @Override
        public boolean passFilter(ImageData imageData) {
            return !imageData.isRaw;
        }
    };

    private final ImageList mOriginalImageList;
    private ImageFilter mFilter;

    public FilteredImageList(ImageList imageList) {
        mOriginalImageList = imageList;
    }

    public FilteredImageList(ImageList imageList, ImageFilter filter) {
        mOriginalImageList = imageList;
        setFilter(filter);
    }

    public void rebuildFilter() {
        setFilter(mFilter);
    }

    public ImageList getList() {
        if(mFilter != null && mFilter.getImageList() != null) {
            return mFilter.getImageList();
        }
        return this;
    }

    public void setFilter(ImageFilter filter) {
        mFilter = filter;
        mImageList.clear();
        if(filter == null) {
            mImageList.addAll(mOriginalImageList.mImageList);
        } else {
            if(filter.getImageList() == null) {
                for (int c = 0; c < mOriginalImageList.length(); c++) {
                    ImageData imageData = mOriginalImageList.getImage(c);
                    if (filter.passFilter(imageData)) {
                        mImageList.add(imageData);
                    }
                }
            }
        }
    }

    public void setFilter(final String filter) {
        ImageFilter textFilter = new ImageFilter() {
            @Override
            public boolean passFilter(ImageData imageData) {
                return imageData.match(filter);
            }
        };
        setFilter(textFilter);
    }
  
    public boolean hasFilter(ImageFilter filter) {
        return mFilter == filter;
    }

    @Override
    protected ImageData createImageData(String dirName, String fileName) {
        return null;
    }
}
