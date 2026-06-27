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

// Parsed result of an introdb.app GET /segments query.
// Unlike theintrodb.org (lists per type), this provider returns a single
// segment object per type (intro / recap / outro), or null when absent.
public class IntroDbAppResult {

    // A single segment with both ms and a confidence/submission_count for ranking.
    public static class Segment {
        public final long startMs;
        public final long endMs;
        public final double confidence;
        public final int submissionCount;

        public Segment(long startMs, long endMs, double confidence, int submissionCount) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.confidence = confidence;
            this.submissionCount = submissionCount;
        }

        public boolean contains(long positionMs) {
            return positionMs >= startMs && positionMs < endMs;
        }

        @Override
        public String toString() {
            return "[" + startMs + " -> " + endMs + " conf=" + confidence +
                    " n=" + submissionCount + "]";
        }
    }

    private String imdbId;
    private Integer season;
    private Integer episode;

    private Segment intro;
    private Segment recap;
    private Segment outro;

    public void setImdbId(String imdbId) { this.imdbId = imdbId; }
    public void setSeason(Integer season) { this.season = season; }
    public void setEpisode(Integer episode) { this.episode = episode; }
    public void setIntro(Segment intro) { this.intro = intro; }
    public void setRecap(Segment recap) { this.recap = recap; }
    public void setOutro(Segment outro) { this.outro = outro; }

    public String getImdbId() { return imdbId; }
    public Integer getSeason() { return season; }
    public Integer getEpisode() { return episode; }
    public Segment getIntro() { return intro; }
    public Segment getRecap() { return recap; }
    public Segment getOutro() { return outro; }

    public boolean hasAnySegment() {
        return intro != null || recap != null || outro != null;
    }

    @Override
    public String toString() {
        return "IntroDbAppResult{imdbId=" + imdbId + ", season=" + season + ", episode=" + episode +
                ", intro=" + intro + ", recap=" + recap + ", outro=" + outro + '}';
    }
}
