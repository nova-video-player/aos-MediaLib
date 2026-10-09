// Copyright 2026 Courville Software
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.Context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class CategoryRowsRefreshTest {

    private Context context;
    private ContentResolver resolver;

    @Before
    public void setUp() {
        context = mock(Context.class);
        resolver = mock(ContentResolver.class);
        when(context.getContentResolver()).thenReturn(resolver);
        LoaderUtils.setBulkScrapeInProgress(false);
        LoaderUtils.setPostScanScrapePending(false);
    }

    @After
    public void tearDown() {
        LoaderUtils.setBulkScrapeInProgress(false);
        LoaderUtils.setPostScanScrapePending(false);
    }

    @Test
    public void networkScanNotifiesCategoryLoadersOnlyAfterTheFinalDecision() {
        LoaderUtils.beginNetworkScan();
        LoaderUtils.notifyCategoryRowsIfReady(context);
        verifyNoInteractions(resolver);

        LoaderUtils.setPostScanScrapePending(true);
        LoaderUtils.endNetworkScan(context);
        verifyNoInteractions(resolver);

        LoaderUtils.setBulkScrapeInProgress(true);
        LoaderUtils.clearPostScanScrapePending(context);
        verifyNoInteractions(resolver);

        LoaderUtils.setBulkScrapeInProgress(false);
        LoaderUtils.notifyCategoryRowsIfReady(context);
        verify(resolver).notifyChange(VideoStore.ALL_CONTENT_URI, null);
    }
}
