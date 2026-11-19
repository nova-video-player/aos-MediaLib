// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.SystemClock;
import com.archos.filecorelibrary.NetworkInitialization;
import java.io.IOException;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only explicit job. The caller owns persistence and user-visible progress. */
public final class SubtitleLanguageAnalysis {
    private static final Logger log = LoggerFactory.getLogger(SubtitleLanguageAnalysis.class);
    public static JSONObject analyze(Context context, Uri uri, Map<String, String> headers, CancellationSignal cancel)
        throws Exception {
        return analyze(context, uri, headers, cancel, stage -> { });
    }
    public interface Progress { void update(String stage); }
    public static JSONObject analyze(Context context, Uri uri, Map<String, String> headers,
            CancellationSignal cancel, Progress progress) throws Exception {
        long started = SystemClock.elapsedRealtime();
        log.info("analysis start pid={} scheme={} detector=fastText-0.9.2 model={}",
                android.os.Process.myPid(), uri.getScheme(), SubtitleLanguageModel.SHA256);
        progress.update("header");
        log.info("stage=network-init");
        NetworkInitialization.awaitReady(context);
        cancel.throwIfCanceled();
        LibAvos.init(context);
        log.info("stage=header elapsed_ms={}", SystemClock.elapsedRealtime() - started);
        JSONObject mkv = MatroskaHeader.read(context, uri, cancel);
        log.info("header complete bytes={} tracks={} elapsed_ms={}", mkv.optLong("size"),
                mkv.getJSONArray("tracks").length(), SystemClock.elapsedRealtime() - started);
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
            if (!retriever.guessSubtitleLanguages())
                throw new IOException("Subtitle analysis could not complete");
            String fingerprint = retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_FINGERPRINT);
            int count = integer(retriever, IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK);
            JSONArray entries = mkv.getJSONArray("tracks");
            if (count != entries.length())
                throw new IOException("Subtitle track mapping does not match Matroska headers");
            JSONArray tracks = new JSONArray();
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
                tracks.put(new JSONObject()
                        .put("index", i)
                        .put("uid", entries.getJSONObject(i).getString("uid"))
                        .put("language", language == null ? "" : language)
                        .put("source", source)
                        .put("score", score)
                        .put("status",
                            source == SubtitleLanguage.UNKNOWN
                                ? (integer(retriever, base + IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_TRACK_KIND)
                                              > 2
                                          ? "unsupported"
                                          : "unresolved")
                                : "identified"));
            }
            log.info("analysis complete tracks={} identified={} cancelled={} elapsed_ms={}",
                    count, identified, cancel.isCanceled(), SystemClock.elapsedRealtime() - started);
            return new JSONObject()
                .put("fingerprint", fingerprint)
                .put("tracks", tracks)
                .put("mkv", mkv)
                .put("model", SubtitleLanguageModel.SHA256)
                .put("detector", "fastText-0.9.2")
                .put("cancelled", cancel.isCanceled())
                .put("analyzedAt", System.currentTimeMillis());
        } finally {
            cancel.setOnCancelListener(null);
            retriever.release();
            log.info("retriever released elapsed_ms={}", SystemClock.elapsedRealtime() - started);
        }
    }
    public static String fingerprint(Context context, Uri uri) throws Exception {
        IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(context);
        try {
            open(retriever, context, uri, null);
            return retriever.extractMetadata(IMediaMetadataRetriever.METADATA_KEY_SUBTITLE_FINGERPRINT);
        } finally {
            retriever.release();
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
