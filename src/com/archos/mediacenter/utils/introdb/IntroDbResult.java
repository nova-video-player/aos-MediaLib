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

import java.util.ArrayList;
import java.util.List;

// Parsed result of a TheIntroDB GET /media query.
// Each segment type is an independent list; a list may be empty when the API
// returned no submissions for that segment type.
public class IntroDbResult {

    // A single segment. start_ms and end_ms may be null:
    //  - intro/recap: startMs null means "starts at the beginning"
    //  - credits/preview: endMs null means "end of media"
    public static class Segment {
        public final Long startMs;
        public final Long endMs;

        public Segment(Long startMs, Long endMs) {
            this.startMs = startMs;
            this.endMs = endMs;
        }

        // True when positionMs falls inside this segment (treating a null start
        // as 0 and a null end as +infinity / end of media).
        public boolean contains(long positionMs) {
            long start = (startMs != null) ? startMs : 0L;
            if (positionMs < start) return false;
            if (endMs == null) return true;
            return positionMs < endMs;
        }

        @Override
        public String toString() {
            return "[" + startMs + " -> " + endMs + "]";
        }
    }

    private String type; // "movie" or "tv"
    private Integer tmdbId;
    private Integer season;
    private Integer episode;

    private final List<Segment> intro = new ArrayList<>();
    private final List<Segment> recap = new ArrayList<>();
    private final List<Segment> credits = new ArrayList<>();
    private final List<Segment> preview = new ArrayList<>();

    public void setType(String type) { this.type = type; }
    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }
    public void setSeason(Integer season) { this.season = season; }
    public void setEpisode(Integer episode) { this.episode = episode; }

    public String getType() { return type; }
    public Integer getTmdbId() { return tmdbId; }
    public Integer getSeason() { return season; }
    public Integer getEpisode() { return episode; }

    public List<Segment> getIntro() { return intro; }
    public List<Segment> getRecap() { return recap; }
    public List<Segment> getCredits() { return credits; }
    public List<Segment> getPreview() { return preview; }

    public boolean hasAnySegment() {
        return !intro.isEmpty() || !recap.isEmpty() || !credits.isEmpty() || !preview.isEmpty();
    }

    @Override
    public String toString() {
        return "IntroDbResult{type=" + type + ", tmdbId=" + tmdbId + ", season=" + season +
                ", episode=" + episode + ", intro=" + intro + ", recap=" + recap +
                ", credits=" + credits + ", preview=" + preview + '}';
    }
}
