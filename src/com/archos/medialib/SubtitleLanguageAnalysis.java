// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.SystemClock;
import com.archos.filecorelibrary.NetworkInitialization;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only explicit job. The caller owns persistence and user-visible progress. */
public final class SubtitleLanguageAnalysis {
    private static final Logger log = LoggerFactory.getLogger(SubtitleLanguageAnalysis.class);
    public static SubtitleLanguageResult analyze(Context context, Uri uri, Map<String, String> headers, CancellationSignal cancel)
        throws Exception {
        return analyze(context, uri, headers, cancel, stage -> { });
    }
    public interface Progress { void update(String stage); }
    public static SubtitleLanguageResult analyze(Context context, Uri uri, Map<String, String> headers,
            CancellationSignal cancel, Progress progress) throws Exception {
        long started = SystemClock.elapsedRealtime();
        log.info("analysis start pid={} scheme={} detector=fastText-0.9.2 model={}",
                android.os.Process.myPid(), uri.getScheme(), SubtitleLanguageModel.SHA256);
        log.info("stage=network-init");
        NetworkInitialization.awaitReady(context);
        cancel.throwIfCanceled();
        LibAvos.init(context);
        progress.update("model");
        LibAvos.prepareSubtitleLanguageModel(context);
        log.info("model prepared elapsed_ms={}", SystemClock.elapsedRealtime() - started);
        progress.update("sampling");
        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(context);
        try {
            cancel.setOnCancelListener(retriever::cancel);
            log.info("stage=source-open retriever={}", retriever.getClass().getSimpleName());
            openForAnalysis(retriever, context, uri, headers);
            log.info("stage=native-sampling elapsed_ms={}", SystemClock.elapsedRealtime() - started);
            cancel.throwIfCanceled();
            if (!retriever.guessSubtitleLanguages()) {
                cancel.throwIfCanceled();
                throw new SubtitleLanguageException(SubtitleLanguageException.Code.ANALYSIS,
                        "Subtitle analysis could not complete");
            }
            int count = integer(retriever, IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK);
            String[] languages = new String[count];
            int[] confidences = new int[count];
            int identified = 0;
            for (int i = 0; i < count; i++) {
                int base = IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK
                    + i * IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_MAX;
                String language =
                    retriever.extractMetadata(base + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_LANGUAGE);
                int source = integer(retriever, IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_SOURCE + i);
                int score = integer(retriever, IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_LANGUAGE_CONFIDENCE + i);
                if (source != SubtitleLanguage.UNKNOWN) identified++;
                log.info("result track={} source={} language={} confidence={}", i, source, language, score);
                boolean inferred = source == SubtitleLanguage.FASTTEXT && language != null
                        && language.matches("[a-z]{2,3}") && !"und".equals(language) && score >= 85 && score <= 100;
                languages[i] = inferred ? language : "und";
                confidences[i] = inferred ? score : 0;
            }
            log.info("analysis complete tracks={} identified={} cancelled={} elapsed_ms={}",
                    count, identified, cancel.isCanceled(), SystemClock.elapsedRealtime() - started);
            return new SubtitleLanguageResult(languages, confidences);
        } finally {
            cancel.setOnCancelListener(null);
            retriever.release();
            log.info("retriever released elapsed_ms={}", SystemClock.elapsedRealtime() - started);
        }
    }
    static void open(IMediaMetadataRetriever r, Context context, Uri uri, Map<String, String> headers)
        throws Exception {
        if ("content".equals(uri.getScheme()))
            r.setDataSource(context, uri);
        else
            r.setDataSource(
                uri.getScheme() == null || "file".equals(uri.getScheme()) ? uri.getPath() : uri.toString(), headers);
    }
    static void openForAnalysis(IMediaMetadataRetriever r, Context context, Uri uri, Map<String, String> headers)
        throws Exception {
        if (r instanceof AvosMediaMetadataRetriever && !"content".equals(uri.getScheme()))
            ((AvosMediaMetadataRetriever) r).setSubtitleAnalysisDataSource(
                    uri.getScheme() == null || "file".equals(uri.getScheme()) ? uri.getPath() : uri.toString(), headers);
        else
            open(r, context, uri, headers);
    }
    static int integer(IMediaMetadataRetriever r, int key) {
        try {
            return Integer.parseInt(r.extractMetadata(key));
        } catch (Exception e) {
            return 0;
        }
    }
}
