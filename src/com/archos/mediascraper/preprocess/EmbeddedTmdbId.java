// Copyright 2025 Nova Video Player contributors
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

package com.archos.mediascraper.preprocess;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The TMDB id a video file carries in itself (Matroska tag TMDB_ID).
 *
 * The container is read through FileCoreLibrary, which works for local files,
 * SAF, SMB, SFTP and WebDAV alike. Reads are bounded: the head of the file,
 * then the Tags block the SeekHead points at, and only as a last resort the
 * tail - so a 25 GB remux costs a few small reads, not a scan.
 *
 * Everything is best effort: an unreadable file, a share that cannot seek or a
 * file without tags all end up as null and the scraper falls back to searching
 * by name.
 */
public final class EmbeddedTmdbId {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedTmdbId.class);

    private static final int CACHE_SIZE = 256;
    private static final long READ_TIME_BUDGET_MS = 3000;

    /** key is "uri:size" so that a re-tagged file (new size) is read again */
    private static final Map<String, Integer> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Integer>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Integer> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    private EmbeddedTmdbId() {
    }

    public static Integer fromFile(Context context, Uri uri) {
        if (uri == null || uri.getScheme() == null) return null;

        long size = -1;
        try {
            MetaFile2 meta = MetaFile2Factory.getMetaFileForUrl(uri);
            if (meta != null && !meta.isDirectory()) size = meta.length();
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("fromFile: no length for {}: {}", uri, e.toString());
        }
        if (size <= 0) return null;

        String key = uri + ":" + size;
        if (CACHE.containsKey(key)) return CACHE.get(key);

        Integer found = null;
        try {
            FileEditor editor = FileEditorFactory.getFileEditorForUrl(uri, context);
            found = MatroskaTags.findTmdbId(size, new EditorRangeReader(editor));
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("fromFile: cannot read {}: {}", uri, e.toString());
        }
        if (log.isDebugEnabled()) log.debug("fromFile: {} -> tmdb id {}", uri, found);
        CACHE.put(key, found);
        return found;
    }

    /** Forgets everything; used when files were renamed or re-tagged. */
    public static void clearCache() {
        CACHE.clear();
    }

    /** One read at an absolute offset, with a time budget so a slow share cannot stall scraping. */
    private static final class EditorRangeReader implements MatroskaTags.RangeReader {
        private final FileEditor editor;

        EditorRangeReader(FileEditor editor) {
            this.editor = editor;
        }

        @Override
        public byte[] read(long offset, int length) throws IOException {
            if (length <= 0) return new byte[0];
            byte[] buffer = new byte[length];
            int filled = 0;
            InputStream in;
            try {
                in = offset > 0 ? editor.getInputStream(offset) : editor.getInputStream();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e.toString());
            }
            if (in == null) throw new IOException("no stream for " + editor.getUri());
            try {
                long started = System.currentTimeMillis();
                while (filled < length) {
                    int read = in.read(buffer, filled, length - filled);
                    if (read <= 0) break;
                    filled += read;
                    if (System.currentTimeMillis() - started > READ_TIME_BUDGET_MS) break;
                }
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                    // nothing useful to do
                }
            }
            if (filled == length) return buffer;
            byte[] shorter = new byte[filled];
            System.arraycopy(buffer, 0, shorter, 0, filled);
            return shorter;
        }
    }
}
