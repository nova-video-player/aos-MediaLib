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

// Input parameters for a TheIntroDB GET /media query.
// See https://theintrodb.org/docs (openapi v3).
// tmdb_id is the preferred/canonical identifier; imdb_id/tvdb_id are fallbacks.
public class IntroDbQueryParams {
    private Integer tmdbId;
    private String imdbId;
    private Integer tvdbId;
    private boolean isShow;
    private Integer season;
    private Integer episode;
    private Long durationMs;

    public IntroDbQueryParams() {
    }

    public void setTmdbId(Integer tmdbId) { this.tmdbId = tmdbId; }
    public void setImdbId(String imdbId) { this.imdbId = imdbId; }
    public void setTvdbId(Integer tvdbId) { this.tvdbId = tvdbId; }
    public void setIsShow(boolean show) { this.isShow = show; }
    public void setSeason(Integer season) { this.season = season; }
    public void setEpisode(Integer episode) { this.episode = episode; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Integer getTmdbId() { return tmdbId; }
    public String getImdbId() { return imdbId; }
    public Integer getTvdbId() { return tvdbId; }
    public boolean isShow() { return isShow; }
    public Integer getSeason() { return season; }
    public Integer getEpisode() { return episode; }
    public Long getDurationMs() { return durationMs; }

    // At least one resolvable identifier must be present.
    public boolean hasIdentifier() {
        return tmdbId != null || imdbId != null || tvdbId != null;
    }

    @Override
    public String toString() {
        return "IntroDbQueryParams{tmdbId=" + tmdbId + ", imdbId=" + imdbId + ", tvdbId=" + tvdbId +
                ", isShow=" + isShow + ", season=" + season + ", episode=" + episode +
                ", durationMs=" + durationMs + '}';
    }
}
