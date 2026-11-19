// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import android.database.sqlite.SQLiteException;
import android.os.OperationCanceledException;
import java.io.IOException;

/** Stable error codes cross the worker boundary; technical details stay in logs. */
public final class SubtitleLanguageException extends IOException {
    public enum Code {
        FAILED(R.string.subtitle_language_error_failed),
        ANALYSIS(R.string.subtitle_language_error_analysis),
        METADATA(R.string.subtitle_language_error_metadata),
        EDIT_UNSUPPORTED(R.string.subtitle_language_error_edit),
        FILE_CHANGED(R.string.subtitle_language_error_changed),
        RESTORE_CHANGED(R.string.subtitle_language_error_restore_changed),
        PENDING_EDIT(R.string.subtitle_language_error_pending),
        NO_EDIT(R.string.subtitle_language_error_no_edit),
        BUSY(R.string.subtitle_language_error_busy),
        VERIFY(R.string.subtitle_language_error_verify),
        ACCESS(R.string.subtitle_language_error_access),
        STORAGE(R.string.subtitle_language_error_storage),
        CANCELLED(R.string.subtitle_language_error_cancelled);

        public final int messageResource;
        Code(int messageResource) { this.messageResource = messageResource; }
    }

    public final Code code;

    public SubtitleLanguageException(Code code, String detail) {
        super(detail);
        this.code = code;
    }

    public SubtitleLanguageException(Code code, String detail, Throwable cause) {
        super(detail, cause);
        this.code = code;
    }

    public static Code codeOf(Throwable error) {
        Code fallback = Code.FAILED;
        for (int depth = 0; error != null && depth < 16; depth++, error = error.getCause()) {
            if (error instanceof SubtitleLanguageException) return ((SubtitleLanguageException) error).code;
            if (error instanceof OperationCanceledException) return Code.CANCELLED;
            if (error instanceof SQLiteException) return Code.STORAGE;
            if (error instanceof IOException || error instanceof SecurityException) fallback = Code.ACCESS;
        }
        return fallback;
    }

    public static Code fromWire(String code) {
        if (code != null) {
            try { return Code.valueOf(code); }
            catch (IllegalArgumentException ignored) { }
        }
        return Code.FAILED;
    }
}
