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

package com.archos.mediacenter.utils.introdb;

import com.archos.mediacenter.utils.MediaUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Provider-agnostic, normalized set of skippable segments. Both introdb providers
// (theintrodb.org and introdb.app) are mapped into this single model by
// IntroDbManager, so callers never see provider specifics. Segments are kept as
// lists per type (theintrodb.org may return several per type); each segment is
// tagged with its source and keeps the optional confidence/submissionCount that
// only introdb.app supplies (0 otherwise).
public class IntroSegments {

    // OUTRO (introdb.app) and CREDITS (theintrodb.org) are kept distinct on purpose
    // to preserve provider provenance; they describe the same end-of-episode block.
    public enum Type { INTRO, RECAP, OUTRO, CREDITS, PREVIEW }

    public static class Segment {
        public final Long startMs;        // null = start of media
        public final Long endMs;          // null = end of media
        public final double confidence;   // 0 when the provider does not supply it
        public final int submissionCount; // 0 when the provider does not supply it
        public final String source;       // provider host the segment came from

        public Segment(Long startMs, Long endMs, double confidence, int submissionCount, String source) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.confidence = confidence;
            this.submissionCount = submissionCount;
            this.source = source;
        }

        // True when positionMs falls inside this segment (null start = 0,
        // null end = +infinity / end of media).
        public boolean contains(long positionMs) {
            long start = (startMs != null) ? startMs : 0L;
            if (positionMs < start) return false;
            if (endMs == null) return true;
            return positionMs < endMs;
        }

        @Override
        public String toString() {
            return "[" + startMs + " -> " + endMs + " conf=" + confidence +
                    " n=" + submissionCount + " src=" + source + "]";
        }
    }

    private final Map<Type, List<Segment>> segments = new EnumMap<>(Type.class);

    public void add(Type type, Segment segment) {
        if (segment == null) return;
        List<Segment> list = segments.get(type);
        if (list == null) {
            list = new ArrayList<>();
            segments.put(type, list);
        }
        list.add(segment);
    }

    public List<Segment> get(Type type) {
        List<Segment> list = segments.get(type);
        return list != null ? list : Collections.<Segment>emptyList();
    }

    public boolean has(Type type) {
        List<Segment> list = segments.get(type);
        return list != null && !list.isEmpty();
    }

    public boolean hasAny() {
        for (List<Segment> list : segments.values())
            if (!list.isEmpty()) return true;
        return false;
    }

    // Types eligible for the standard auto-skip, in priority order: intro, credits, outro,
    // preview. RECAP is intentionally excluded here: it is only skipped on demand (binge
    // mode, episode-to-episode transition) via the includeRecap flag of findSkip.
    private static final Type[] SKIP_TYPES = { Type.INTRO, Type.CREDITS, Type.OUTRO, Type.PREVIEW };
    private static final Type[] RECAP_ONLY = { Type.RECAP };
    // Standard types plus recap, recap kept right after intro in priority order.
    private static final Type[] SKIP_TYPES_WITH_RECAP = { Type.INTRO, Type.RECAP, Type.CREDITS, Type.OUTRO, Type.PREVIEW };
    private static final Type[] NONE = {};

    // Result of a skip lookup: which segment type matched and the end (ms) to jump to.
    public static class Skip {
        public final Type type;
        public final long endMs;
        public Skip(Type type, long endMs) {
            this.type = type;
            this.endMs = endMs;
        }
    }

    // Eligible types for this lookup: standard set (intro/credits/outro/preview) and/or recap,
    // depending on what the caller enabled.
    private static Type[] eligibleTypes(boolean includeStandard, boolean includeRecap) {
        if (includeStandard && includeRecap) return SKIP_TYPES_WITH_RECAP;
        if (includeStandard) return SKIP_TYPES;
        if (includeRecap) return RECAP_ONLY;
        return NONE;
    }

    // Auto-skip: find an eligible segment (priority order) that contains positionMs and has
    // a concrete end, then extend that end across any other eligible segments that overlap,
    // so overlapping intervals (e.g. credits + outro) are skipped in a single jump to the
    // furthest end. Returns null when nothing applies. Segments with no concrete end
    // (null endMs = end of media) are skipped so we never jump to the end of the file.
    // includeStandard covers intro/credits/outro/preview; includeRecap adds recap.
    public Skip findSkip(long positionMs, boolean includeStandard, boolean includeRecap) {
        Type[] types = eligibleTypes(includeStandard, includeRecap);
        for (Type type : types) {
            for (Segment s : get(type)) {
                if (s.endMs == null) continue;    // need a concrete end to jump to
                if (s.endMs <= positionMs) continue;
                if (s.contains(positionMs))
                    return new Skip(type, mergedEnd(s.endMs, types));
            }
        }
        return null;
    }

    // Grow endMs while any eligible segment starts at or before the current end and ends
    // later, merging the chain of overlapping skippable segments into a single target.
    private long mergedEnd(long endMs, Type[] types) {
        boolean extended = true;
        while (extended) {
            extended = false;
            for (Type type : types) {
                for (Segment s : get(type)) {
                    if (s.endMs == null) continue;
                    long start = (s.startMs != null) ? s.startMs : 0L;
                    if (start <= endMs && s.endMs > endMs) {
                        endMs = s.endMs;
                        extended = true;
                    }
                }
            }
        }
        return endMs;
    }

    // Types in display order, used by the summary/debug renderers. The caller supplies
    // translatable labels keyed by Type (resolved from string resources in the app module).
    private static final Type[] DISPLAY_TYPES = { Type.INTRO, Type.RECAP, Type.OUTRO, Type.CREDITS, Type.PREVIEW };

    // Human-readable consolidated summary, using the same time format as the
    // leanback resume box (MediaUtils.formatTime). Returns null when empty.
    public String toDebugString(Map<Type, String> labels) {
        StringBuilder sb = new StringBuilder("IntroDB");
        boolean any = false;
        for (Type type : DISPLAY_TYPES)
            any |= appendLines(sb, labels.get(type), type);
        return any ? sb.toString() : null;
    }

    // Compact one-line summary for the Play mode tile/menu: "/"-separated, no
    // header, same time format as toDebugString. A null segment end means end of
    // media and is rendered using the caller-supplied endLabel. Returns null when empty.
    public String toSummaryString(Map<Type, String> labels, String endLabel) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (Type type : DISPLAY_TYPES)
            any |= appendSummary(sb, labels.get(type), type, endLabel);
        return any ? sb.toString() : null;
    }

    private boolean appendSummary(StringBuilder sb, String label, Type type, String endLabel) {
        boolean any = false;
        for (Segment s : get(type)) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(label).append(": ")
                    .append(time(s.startMs)).append(" \u2192 ").append(endTime(s.endMs, endLabel));
            any = true;
        }
        return any;
    }

    // Like time() but a null end means end of media: show endLabel rather than "unknown".
    private static String endTime(Long ms, String endLabel) {
        if (ms == null) return endLabel;
        String s = MediaUtils.formatTime(ms);
        return (s == null || s.isEmpty()) ? endLabel : s;
    }

    private boolean appendLines(StringBuilder sb, String label, Type type) {
        boolean any = false;
        for (Segment s : get(type)) {
            sb.append('\n').append(label).append(": ")
                    .append(time(s.startMs)).append(" -> ").append(time(s.endMs));
            any = true;
        }
        return any;
    }

    private static String time(Long ms) {
        if (ms == null) return "unknown";
        String s = MediaUtils.formatTime(ms);
        return (s == null || s.isEmpty()) ? "unknown" : s;
    }

    @Override
    public String toString() {
        return "IntroSegments" + segments;
    }
}
