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

    public static final String FILTER_DOWNLOADED = "____FILTER_DOWNLOADED";

    private final ImageList mOriginalImageList;

    public FilteredImageList(ImageList imageList) {
        mOriginalImageList = imageList;
    }

    public FilteredImageList(ImageList imageList, String filter) {
        mOriginalImageList = imageList;
        setFilter(filter);
    }

    public void setFilter(String filter) {
        mImageList.clear();
        if(filter == null || filter.length() == 0) {
            mImageList.addAll(mOriginalImageList.mImageList);
        } else {
            boolean downloadedOnly = FILTER_DOWNLOADED.equals(filter);
            for (int c = 0; c < mOriginalImageList.length(); c++) {
                ImageData imageData = mOriginalImageList.getImage(c);
                if ((downloadedOnly && imageData.existsOnLocalStorage()) || imageData.match(filter)) {
                    mImageList.add(imageData);
                }
            }
        }
    }

    @Override
    protected ImageData createImageData(String dirName, String fileName) {
        return null;
    }
}
