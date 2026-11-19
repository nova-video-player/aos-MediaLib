// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Separate app process contains native analysis failures. Never opens a writable media handle. */
public final class SubtitleLanguageAnalysisService extends Service {
    private static final Logger log = LoggerFactory.getLogger(SubtitleLanguageAnalysisService.class);
    public static final int ANALYZE = 1, CANCEL = 2, COMPLETE = 3, ERROR = 4, PROGRESS = 5;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private CancellationSignal cancellation;
    private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what == CANCEL) {
            log.info("analysis cancellation requested");
            if (cancellation != null)
                cancellation.cancel();
            return true;
        }
        if (message.what != ANALYZE || cancellation != null)
            return true;
        cancellation = new CancellationSignal();
        CancellationSignal signal = cancellation;
        Messenger reply = message.replyTo;
        Bundle args = new Bundle(message.getData());
        executor.execute(() -> {
            long started = SystemClock.elapsedRealtime();
            log.info("worker start pid={}", android.os.Process.myPid());
            try {
                HashMap<String, String> headers = new HashMap<>();
                Bundle values = args.getBundle("headers");
                if (values != null)
                    for (String key : values.keySet()) headers.put(key, values.getString(key));
                JSONObject result =
                    SubtitleLanguageAnalysis.analyze(this, Uri.parse(args.getString("uri")), headers, signal,
                            stage -> send(reply, PROGRESS, stage));
                // Persist in the worker before replying: leaving the screen cannot lose completed analysis.
                send(reply, PROGRESS, "persisting");
                SubtitleLanguageStore.save(this, args.getString("uri"), result);
                log.info("results persisted elapsed_ms={}", SystemClock.elapsedRealtime() - started);
                send(reply, COMPLETE, "saved");
            } catch (Exception | LinkageError e) {
                log.error("Explicit language analysis failed after "
                        + (SystemClock.elapsedRealtime() - started) + " ms", e);
                send(reply, ERROR, SubtitleLanguageException.codeOf(e).name());
            } finally {
                new Handler(Looper.getMainLooper()).post(() -> cancellation = null);
            }
        });
        return true;
    }));
    private static void send(Messenger reply, int what, String value) {
        if (reply == null)
            return;
        try {
            Message message = Message.obtain(null, what);
            Bundle data = new Bundle();
            data.putString("result", value);
            message.setData(data);
            reply.send(message);
        } catch (RemoteException ignored) {
        }
    }
    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }
    @Override
    public boolean onUnbind(Intent intent) {
        log.info("analysis client unbound; cancelling active job");
        if (cancellation != null)
            cancellation.cancel();
        return false;
    }
    @Override
    public void onDestroy() {
        if (cancellation != null)
            cancellation.cancel();
        executor.shutdown();
        super.onDestroy();
    }
}
