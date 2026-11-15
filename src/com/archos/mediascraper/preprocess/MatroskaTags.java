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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the tags a Matroska file carries about itself.
 *
 * Nova guessed the film from the file name and asked TMDB to identify it, which
 * fails for remakes and for anything with a messy name. A file tagged with
 * TMDB_ID already knows what it is, so the scraper only has to look inside.
 *
 * The container is read in small pieces, never in full: the head of the file,
 * then - when the SeekHead points at the Tags element - just that block, and
 * only as a last resort a scan of the tail, because some muxers append the
 * tags after the last cluster and write no SeekHead at all.
 */
public final class MatroskaTags {

    /** Reads a byte range of the file; the caller decides how. */
    public interface RangeReader {
        byte[] read(long offset, int length) throws IOException;
    }

    private static final long ID_EBML = 0x1A45DFA3L;
    private static final long ID_SEGMENT = 0x18538067L;
    private static final long ID_SEEK_HEAD = 0x114D9B74L;
    private static final long ID_SEEK = 0x4DBBL;
    private static final long ID_SEEK_ID = 0x53ABL;
    private static final long ID_SEEK_POSITION = 0x53ACL;
    private static final long ID_TAGS = 0x1254C367L;
    private static final long ID_TAG = 0x7373L;
    private static final long ID_SIMPLE_TAG = 0x67C8L;
    private static final long ID_TAG_NAME = 0x45A3L;
    private static final long ID_TAG_STRING = 0x4487L;

    private static final long UNKNOWN_SIZE = 0x00FFFFFFFFFFFFFFL;
    private static final int MAX_ELEMENTS = 4096;
    private static final int MAX_TAG_BYTES = 4 * 1024 * 1024;

    private MatroskaTags() {
    }

    /**
     * @param fileSize size of the file, used to bound every read
     * @param reader   reads byte ranges of that file
     * @return the TMDB id stored in the file, or null when there is none
     */
    public static Integer findTmdbId(long fileSize, RangeReader reader) {
        if (fileSize <= 0 || reader == null) return null;
        try {
            HeadScan head = scanHead(reader.read(0, (int) Math.min(Bytes.HEAD, fileSize)));
            if (head.id != null) return head.id;

            if (head.tagsPosition >= 0 && head.segmentDataStart >= 0) {
                long offset = head.segmentDataStart + head.tagsPosition;
                if (offset >= 0 && offset < fileSize) {
                    int length = (int) Math.min(Bytes.TAGS, fileSize - offset);
                    Integer id = tagsElement(reader.read(offset, length));
                    if (id != null) return id;
                }
            }

            if (fileSize > Bytes.HEAD) {
                long from = Math.max(0, fileSize - Bytes.TAIL);
                return scanForTagsElement(reader.read(from, (int) (fileSize - from)));
            }
        } catch (IOException e) {
            // an unreadable file is the same as an untagged one
        }
        return null;
    }

    private static final class Bytes {
        static final int HEAD = 1 << 20;      // 1 MB: header, SeekHead, Info, Tracks
        static final int TAGS = 256 << 10;    // a Tags element is normally tiny
        static final int TAIL = 4 << 20;      // only when there is no usable SeekHead
    }

    /** What the head of the file told us. */
    private static final class HeadScan {
        Integer id;                    // set when Tags were inside the head
        long segmentDataStart = -1;    // absolute offset of the first Segment child
        long tagsPosition = -1;        // SeekPosition of Tags, relative to the segment
    }

    /**
     * Walks EBML elements from the start. Along the way it remembers where the
     * segment data begins and whether the SeekHead advertises a Tags element.
     */
    private static HeadScan scanHead(byte[] data) {
        HeadScan scan = new HeadScan();
        if (data == null || data.length < 4) return scan;
        Reader reader = new Reader(data, 0, data.length);
        for (int elements = 0; elements < MAX_ELEMENTS && reader.has(2); elements++) {
            int elementStart = reader.pos;
            long id = reader.readVint(true);
            if (id < 0) return scan;
            long size = reader.readVint(false);
            if (size < 0 || size == UNKNOWN_SIZE) return scan;
            int headerLength = reader.pos - elementStart;
            if (id == ID_SEGMENT) {
                // the segment is bigger than the head: descend into it anyway
                scan.segmentDataStart = elementStart + headerLength;
                continue;
            }
            if (size > reader.remaining()) {
                // a block that does not fit in the head: nothing more to learn
                return scan;
            }
            if (id == ID_TAGS) {
                scan.id = tmdbIdFromTags(new Reader(data, reader.pos,
                        reader.pos + (int) size));
                return scan;
            }
            if (id == ID_SEEK_HEAD) {
                Long position = tagsPositionInSeekHead(new Reader(data, reader.pos,
                        reader.pos + (int) size));
                if (position != null) scan.tagsPosition = position;
            }
            reader.pos += (int) size;
        }
        return scan;
    }

    /** SeekHead payload: Seek entries of (SeekID, SeekPosition). */
    private static Long tagsPositionInSeekHead(Reader reader) {
        while (reader.has(2)) {
            long id = reader.readVint(true);
            if (id < 0) return null;
            long size = reader.readVint(false);
            if (size < 0 || size > reader.remaining()) return null;
            if (id == ID_SEEK) {
                Long position = positionOfSeek(new Reader(reader.data, reader.pos,
                        reader.pos + (int) size));
                if (position != null) return position;
            }
            reader.pos += (int) size;
        }
        return null;
    }

    private static Long positionOfSeek(Reader reader) {
        long seekId = -1;
        long position = -1;
        while (reader.has(2)) {
            long id = reader.readVint(true);
            if (id < 0) return null;
            long size = reader.readVint(false);
            if (size < 0 || size > reader.remaining()) return null;
            if (id == ID_SEEK_ID) {
                seekId = reader.readUint((int) size);
            } else if (id == ID_SEEK_POSITION) {
                position = reader.readUint((int) size);
            }
            reader.pos += (int) size;
        }
        return seekId == ID_TAGS && position >= 0 ? position : null;
    }

    /** Parses a Tags element that starts at the first byte of the range. */
    private static Integer tagsElement(byte[] data) {
        if (data == null || data.length < 4) return null;
        Reader reader = new Reader(data, 0, data.length);
        long id = reader.readVint(true);
        if (id != ID_TAGS) return null;
        long size = reader.readVint(false);
        if (size < 0 || size > MAX_TAG_BYTES) return null;
        int available = Math.min((int) size, reader.remaining());
        return tmdbIdFromTags(new Reader(data, reader.pos, reader.pos + available));
    }

    /**
     * The tail range starts in the middle of something, so it has no usable
     * element boundary: the Tags element is looked for by its four id bytes,
     * and every hit is parsed and validated.
     */
    private static Integer scanForTagsElement(byte[] data) {
        if (data == null || data.length < 8) return null;
        for (int offset = 0; offset + 6 <= data.length; offset++) {
            if ((data[offset] & 0xFF) != 0x12 || (data[offset + 1] & 0xFF) != 0x54
                    || (data[offset + 2] & 0xFF) != 0xC3
                    || (data[offset + 3] & 0xFF) != 0x67) {
                continue;
            }
            Reader reader = new Reader(data, offset + 4, data.length);
            long size = reader.readVint(false);
            if (size < 0 || size > MAX_TAG_BYTES) {
                offset += 3;
                continue;
            }
            int available = size > reader.remaining() ? reader.remaining() : (int) size;
            Integer id = tmdbIdFromTags(new Reader(data, reader.pos,
                    reader.pos + available));
            if (id != null) return id;
        }
        return null;
    }

    private static Integer tmdbIdFromTags(Reader reader) {
        while (reader.has(2)) {
            long id = reader.readVint(true);
            if (id < 0) return null;
            long size = reader.readVint(false);
            if (size < 0 || size > reader.remaining()) return null;
            if (id == ID_TAG) {
                Map<String, String> values = new HashMap<>();
                readTag(new Reader(reader.data, reader.pos, reader.pos + (int) size), values);
                Integer movieId = tmdbIdFromValues(values);
                if (movieId != null) return movieId;
            }
            reader.pos += (int) size;
        }
        return null;
    }

    private static void readTag(Reader reader, Map<String, String> values) {
        while (reader.has(2)) {
            long id = reader.readVint(true);
            if (id < 0) return;
            long size = reader.readVint(false);
            if (size < 0 || size > reader.remaining()) return;
            if (id == ID_SIMPLE_TAG) {
                readSimpleTag(new Reader(reader.data, reader.pos, reader.pos + (int) size),
                        values);
            }
            reader.pos += (int) size;
        }
    }

    private static void readSimpleTag(Reader reader, Map<String, String> values) {
        String name = null;
        String value = null;
        while (reader.has(2)) {
            long id = reader.readVint(true);
            if (id < 0) break;
            long size = reader.readVint(false);
            if (size < 0 || size > reader.remaining()) break;
            if (id == ID_TAG_NAME) {
                name = reader.readString((int) size);
            } else if (id == ID_TAG_STRING) {
                value = reader.readString((int) size);
            }
            reader.pos += (int) size;
        }
        if (name != null && value != null) values.put(name, value);
    }

    private static Integer tmdbIdFromValues(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = entry.getKey().toUpperCase(Locale.US)
                    .replace('-', '_').replace(' ', '_');
            if (!name.equals("TMDB_ID") && !name.equals("TMDBID") && !name.equals("TMDB")) {
                continue;
            }
            String digits = entry.getValue().trim();
            int start = -1;
            int end = -1;
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                if (c >= '0' && c <= '9') {
                    if (start < 0) start = i;
                    end = i + 1;
                } else if (start >= 0) {
                    break;
                }
            }
            if (start < 0) continue;
            try {
                return Integer.valueOf(digits.substring(start, end));
            } catch (NumberFormatException ignored) {
                // a broken tag is the same as no tag
            }
        }
        return null;
    }

    /** Minimal byte reader for one EBML element. */
    private static final class Reader {
        private final byte[] data;
        private int pos;
        private final int limit;

        Reader(byte[] data, int pos, int limit) {
            this.data = data;
            this.pos = pos;
            this.limit = Math.min(limit, data.length);
        }

        boolean has(int count) {
            return pos + count <= limit;
        }

        int remaining() {
            return limit - pos;
        }

        /**
         * EBML variable length integer. The element id keeps its length marker,
         * a size drops it - that is how the format defines both.
         */
        long readVint(boolean keepMarker) {
            if (!has(1)) return -1;
            int first = data[pos] & 0xFF;
            if (first == 0) return -1;
            int length = 1;
            int mask = 0x80;
            while ((first & mask) == 0) {
                mask >>= 1;
                length++;
                if (length > 8) return -1;
            }
            if (!has(length)) return -1;
            long value = keepMarker ? first : (first & (mask - 1));
            for (int i = 1; i < length; i++) {
                value = (value << 8) | (data[pos + i] & 0xFF);
            }
            pos += length;
            return value;
        }

        long readUint(int size) {
            long value = 0;
            int count = Math.min(size, remaining());
            for (int i = 0; i < count; i++) {
                value = (value << 8) | (data[pos + i] & 0xFF);
            }
            return value;
        }

        String readString(int size) {
            String text = new String(data, pos, Math.min(size, remaining()),
                    StandardCharsets.UTF_8);
            int zero = text.indexOf('\u0000');
            if (zero >= 0) text = text.substring(0, zero);
            return text.trim();
        }
    }
}
