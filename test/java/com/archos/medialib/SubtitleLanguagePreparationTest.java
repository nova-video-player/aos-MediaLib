// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import static org.mockito.Mockito.*;
import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class SubtitleLanguagePreparationTest {
    @Test public void cancellationDuringSourceSetupStopsAnalysisAndReleasesRetriever() throws Exception {
        exercise(true);
    }

    @Test public void completedPreparationUsesHeadersAndReleasesRetriever() throws Exception {
        exercise(false);
    }

    private void exercise(boolean cancelDuringSetup) throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        CancellationSignal cancellation = new CancellationSignal();
        IMediaMetadataRetriever retriever = mock(IMediaMetadataRetriever.class);
        java.util.Map<String, String> headers = Collections.singletonMap("User-Agent", "Nova-test");
        String url = "https://example.invalid/video.mkv";
        if (cancelDuringSetup) doAnswer(call -> { cancellation.cancel(); return null; })
                .when(retriever).setDataSource(url, headers);
        try (MockedStatic<LibAvos> library = mockStatic(LibAvos.class);
             MockedStatic<MediaFactory> factory = mockStatic(MediaFactory.class)) {
            library.when(LibAvos::isAvailable).thenReturn(true);
            factory.when(() -> MediaFactory.createMetadataRetriever(context)).thenReturn(retriever);
            SubtitleLanguagePreparation.prepare(context, Uri.parse(url), headers, cancellation);
        }
        verify(retriever).setDataSource(url, headers);
        verify(retriever, times(cancelDuringSetup ? 0 : 1)).guessSubtitleLanguages();
        verify(retriever, times(cancelDuringSetup ? 1 : 0)).cancel();
        verify(retriever).release();
        clearInvocations(retriever);
        cancellation.cancel();
        verifyNoInteractions(retriever); // no cancellation callback can outlive release
    }
}
