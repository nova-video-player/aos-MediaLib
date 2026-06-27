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

// Input parameters for an introdb.app GET /segments query.
// See https://introdb.app/docs/api. Reads are keyed on imdb_id + season + episode
// (all required); this provider is effectively TV-only for lookups.
public class IntroDbAppQueryParams {
    private String imdbId;
    private Integer season;
    private Integer episode;

    public IntroDbAppQueryParams() {
    }

    public void setImdbId(String imdbId) { this.imdbId = imdbId; }
    public void setSeason(Integer season) { this.season = season; }
    public void setEpisode(Integer episode) { this.episode = episode; }

    public String getImdbId() { return imdbId; }
    public Integer getSeason() { return season; }
    public Integer getEpisode() { return episode; }

    // imdb_id + season + episode are all required by the /segments endpoint.
    public boolean isValid() {
        return imdbId != null && !imdbId.isEmpty() && season != null && episode != null;
    }

    @Override
    public String toString() {
        return "IntroDbAppQueryParams{imdbId=" + imdbId + ", season=" + season +
                ", episode=" + episode + '}';
    }
}
