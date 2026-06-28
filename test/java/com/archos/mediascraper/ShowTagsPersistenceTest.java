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

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.MatrixCursor;
import android.net.Uri;

import com.archos.mediaprovider.video.ScraperStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ShowTagsPersistenceTest {

    @Test
    public void duplicateInsertRaceReusesExistingShow() {
        Context context = mock(Context.class);
        ContentResolver resolver = mock(ContentResolver.class);
        AtomicBoolean insertAttempted = new AtomicBoolean();
        when(context.getContentResolver()).thenReturn(resolver);
        when(resolver.insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class)))
                .thenAnswer(invocation -> {
                    insertAttempted.set(true);
                    return null;
                });
        when(resolver.query(any(Uri.class), any(String[].class), nullable(String.class),
                nullable(String[].class), nullable(String.class))).thenAnswer(invocation -> {
                    Uri uri = invocation.getArgument(0);
                    String[] projection = invocation.getArgument(1);
                    MatrixCursor cursor = new MatrixCursor(projection);
                    if (insertAttempted.get() && ScraperStore.Show.URI.ALL.equals(uri)
                            && projection.length == 9) {
                        cursor.addRow(new Object[] {
                                77L, null, 0f, null, null, null, 5678L, -1L, -1L
                        });
                    }
                    return cursor;
                });

        ShowTags show = new ShowTags();
        show.setTitle("Concurrent show");
        show.setOnlineId(5678L);

        assertEquals(77L, show.save(context, 42L));
        verify(resolver).insert(eq(ScraperStore.Show.URI.BASE), any(ContentValues.class));
    }
}
