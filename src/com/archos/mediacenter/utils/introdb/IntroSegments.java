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

    // Auto-skip target: the end (ms) of the first INTRO then RECAP segment that
    // contains positionMs and has a concrete end to jump to, or null if none.
    // Credits/outro/preview are intentionally excluded (a null end = end of media).
    public Long findSkipTarget(long positionMs) {
        Long target = skipTargetIn(Type.INTRO, positionMs);
        if (target == null) target = skipTargetIn(Type.RECAP, positionMs);
        return target;
    }

    private Long skipTargetIn(Type type, long positionMs) {
        for (Segment s : get(type)) {
            if (s.endMs == null) continue;        // need a concrete end to jump to
            if (s.endMs <= positionMs) continue;
            if (s.contains(positionMs)) return s.endMs;
        }
        return null;
    }

    // Human-readable consolidated summary, using the same time format as the
    // leanback resume box (MediaUtils.formatTime). Returns null when empty.
    public String toDebugString() {
        StringBuilder sb = new StringBuilder("IntroDB");
        boolean any = false;
        any |= appendLines(sb, "Intro", Type.INTRO);
        any |= appendLines(sb, "Recap", Type.RECAP);
        any |= appendLines(sb, "Outro", Type.OUTRO);
        any |= appendLines(sb, "Credit", Type.CREDITS);
        any |= appendLines(sb, "Preview", Type.PREVIEW);
        return any ? sb.toString() : null;
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
