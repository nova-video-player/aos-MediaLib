// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Log;
import java.util.Map;

/** Prepares native language metadata before playback; all work belongs on a source worker. */
public final class SubtitleLanguagePreparation {
    private SubtitleLanguagePreparation() { }

    public static void prepare(Context context, Uri uri, Map<String, String> headers, CancellationSignal cancellation) {
        if (cancellation.isCanceled() || !LibAvos.isAvailable()) return;
        IMediaMetadataRetriever retriever = null;
        try {
            retriever = MediaFactory.createMetadataRetriever(context);
            IMediaMetadataRetriever active = retriever;
            cancellation.setOnCancelListener(active::cancel);
            if (cancellation.isCanceled()) return;
            if ("content".equals(uri.getScheme())) retriever.setDataSource(context, uri);
            else retriever.setDataSource("file".equals(uri.getScheme()) ? uri.getPath() : uri.toString(), headers);
            if (!cancellation.isCanceled()) retriever.guessSubtitleLanguages();
        } catch (Exception error) {
            // Detection cannot prevent playback; normal source opening reports actual media errors.
            Log.w("SubtitleLanguages", "Language preparation unavailable", error);
        } finally {
            cancellation.setOnCancelListener(null);
            if (retriever != null) {
                try { retriever.release(); } catch (Exception error) { Log.w("SubtitleLanguages", "Release failed", error); }
            }
        }
    }
}
