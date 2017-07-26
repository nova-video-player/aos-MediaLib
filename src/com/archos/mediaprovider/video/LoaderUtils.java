// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediaprovider.video;

import com.archos.mediacenter.utils.trakt.Trakt;

/**
 * Created by vapillon on 29/05/15.
 */
public class LoaderUtils {

    public final static String HIDE_USER_HIDDEN_FILTER = VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER+"=0";

    public final static String HIDE_WATCHED_FILTER = "("+VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN+" IS NULL OR "+
            VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " != "+ Trakt.TRAKT_DB_MARKED +") AND "+
            "("+VideoStore.Video.VideoColumns.BOOKMARK+" IS NULL OR "+VideoStore.Video.VideoColumns.BOOKMARK+" != -2)";
    //most database helper won't return any video object if set to true
    static public boolean mustHideUserHiddenObjects() {
        return true;
    }

    static public boolean mustHideWatchedVideo() {
        return false; // TODO
    }
}
