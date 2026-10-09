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

import android.content.Context;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediascraper.AutoScrapeService;
import com.archos.mediaprovider.ImportState;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ProcessLifecycleOwner;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by vapillon on 29/05/15.
 */
public class LoaderUtils {

    static public boolean mMustHideWatchedVideo = false;
    static public boolean mSmartRecentlyRows = false;
    static public volatile boolean mScrapeInProgress = false;
    // True only for scrapes large enough to be disruptive to the browse rows (a full/incremental
    // scrape of multiple files), as opposed to the one-file follow-up scrape triggered by a single
    // delete or import. Set by AutoScrapeService from the work count at the start of a scrape and
    // kept until that scrape finishes.
    static public volatile boolean mBulkScrapeInProgress = false;
    // True from the moment a network scan's follow-up scrape is requested until that scrape's
    // worker has established its real deferral state (or finished). It covers the gap between
    // the scanner's completion broadcast and the scrape worker counting its work, during which
    // neither the scanner flag nor the bulk flag reflects the imminent scrape.
    static public volatile boolean mPostScanScrapePending = false;
    private static final AtomicInteger sNetworkScansInProgress = new AtomicInteger();
    public final static String HIDE_USER_HIDDEN_FILTER = VideoStore.Video.VideoColumns.ARCHOS_HIDDEN_BY_USER+"=0";

    public final static String HIDE_WATCHED_FILTER = "("+VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN+" IS NULL OR "+
            VideoStore.Video.VideoColumns.ARCHOS_TRAKT_SEEN + " != "+ Trakt.TRAKT_DB_MARKED +") AND "+
            "("+VideoStore.Video.VideoColumns.BOOKMARK+" IS NULL OR "+VideoStore.Video.VideoColumns.BOOKMARK+" != -2)";
    //most database helper won't return any video object if set to true
    static public boolean mustHideUserHiddenObjects() {
        return true;
    }

    static public boolean mustHideWatchedVideo() {
        return mMustHideWatchedVideo;
    }

    static public boolean getScrapeInProgress() {
        return mScrapeInProgress;
    }

    static public boolean setScrapeInProgress(boolean isScrapeInProgress) {
        return mScrapeInProgress = isScrapeInProgress;
    }

    static public boolean setBulkScrapeInProgress(boolean isBulkScrapeInProgress) {
        return mBulkScrapeInProgress = isBulkScrapeInProgress;
    }

    static public boolean setPostScanScrapePending(boolean isPending) {
        return mPostScanScrapePending = isPending;
    }

    static public void clearPostScanScrapePending(Context context) {
        mPostScanScrapePending = false;
        notifyCategoryRowsIfReady(context);
    }

    /** Keep row loaders deferred until a network scan has decided whether to start scraping. */
    static public void beginNetworkScan() {
        sNetworkScansInProgress.incrementAndGet();
    }

    static public void endNetworkScan(Context context) {
        sNetworkScansInProgress.decrementAndGet();
        notifyCategoryRowsIfReady(context);
    }

    static public boolean isCategoryRowsDeferralActive() {
        return sNetworkScansInProgress.get() > 0
                || AutoScrapeService.getNetworkScanCount() > 0
                || NetworkScannerReceiver.isScannerWorking()
                || mPostScanScrapePending
                || mBulkScrapeInProgress
                || ImportState.VIDEO.isInitialImport();
    }

    /** The category raw-query cursors observe ALL_CONTENT_URI. Notify after work becomes safe. */
    static public void notifyCategoryRowsIfReady(Context context) {
        if (!isCategoryRowsDeferralActive()) {
            context.getContentResolver().notifyChange(VideoStore.ALL_CONTENT_URI, null);
        }
    }
    
    static public boolean isSmartRecentlyRows() {
        return mSmartRecentlyRows;
    }

    static public boolean isAppInForeground() {
        return ProcessLifecycleOwner.get()
                .getLifecycle()
                .getCurrentState()
                .isAtLeast(Lifecycle.State.STARTED);
    }
}
