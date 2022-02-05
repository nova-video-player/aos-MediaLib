// Copyright 2020 Courville Software
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

package com.archos.mediascraper.themoviedb3;

import android.content.Context;
import android.util.SparseArray;

import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ShowTags;
import com.uwetrottmann.tmdb2.entities.CastMember;
import com.uwetrottmann.tmdb2.entities.CrewMember;
import com.uwetrottmann.tmdb2.entities.TvEpisode;
import com.uwetrottmann.tmdb2.entities.TvSeason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// process List of TvEpisodes
public class ShowIdEpisodes {
    private static final Logger log = LoggerFactory.getLogger(ShowIdEpisodes.class);

    private static final String DIRECTOR = "Director";

    public static Map<String, EpisodeTags> getEpisodes(int showId, List<TvEpisode> tvEpisodes, Map<Integer, TvSeason> tvSeasons, ShowTags showTags, String language,
                                                       MyTmdb tmdb, Context context) {

        Map<String, EpisodeTags> episodes = new HashMap<>();
        TvSeason tvSeason;
        // fill in once for all episodes in "en" in case there is something missing in specific language
        SparseArray<TvEpisode> globalEpisodes = new SparseArray<>();

        if (tvEpisodes != null) {
            for (TvEpisode tvEpisode : tvEpisodes) {
                log.debug("getEpisodes: filling showid " + showId + " s" + tvEpisode.season_number + "e" + tvEpisode.episode_number);
                EpisodeTags episodeTags = new EpisodeTags();
                // note: tvEpisode.credits is null thus use tvEpisode.guest_stars and tvEpisode.crew instead
                if (tvEpisode.guest_stars != null) {
                    for (CastMember guestStar : tvEpisode.guest_stars)
                        episodeTags.addActorIfAbsent(guestStar.name, guestStar.character);
                } else {
                    log.warn("getEpisodes: guest_star is null for showId " + showId);
                }
                if (tvEpisode.crew != null) {
                    for (CrewMember crew : tvEpisode.crew) {
                        assert crew.job != null;
                        if (crew.job.equals(DIRECTOR))
                            episodeTags.addDirectorIfAbsent(crew.name);
                    }
                } else {
                    log.debug("getEpisodes: crew is null for showId " + showId);
                }

                // set episode poster according to corresponding season poster
                log.debug("getEpisodes: tvEpisode.season_number=" + tvEpisode.season_number + ", tvSeasons.size=" + tvSeasons.size());

                if (tvSeasons != null) {
                    tvSeason = tvSeasons.get(tvEpisode.season_number);
                    if (tvSeason != null) {
                        // note tvSeason.poster_path can be null when show has only one serie e.g. https://api.themoviedb.org/3/tv/93911/season/1?language=en&api_key=051012651ba326cf5b1e2f482342eaa2
                        if (tvSeason.poster_path != null) {
                            episodeTags.addDefaultPoster(ShowIdImagesParser.genPoster(showTags.getTitle(), tvSeason.poster_path, language, false, context));
                        } else {
                            log.debug("getEpisodes: tvSeason.poster_path is null get showTag default poster");
                            episodeTags.addDefaultPoster(showTags.getDefaultPoster());
                        }
                        log.debug("getEpisodes: " + showTags.getTitle() + " s" + tvEpisode.season_number + ", has poster " + episodeTags.getDefaultPoster().getLargeUrl() + " path " + episodeTags.getDefaultPoster().getLargeFile());
                    }
                } else {
                    log.debug("getEpisodes: no poster set for " + showTags.getTitle() + "s" + tvEpisode.season_number);
                }
                log.debug("getEpisodes: " + tvEpisode.name + " has plot " + tvEpisode.overview);
                episodeTags.setPlot(tvEpisode.overview);
                episodeTags.setRating(Math.round(tvEpisode.vote_average.floatValue() * 10)/10.0f); // round up first decimal
                episodeTags.setTitle(tvEpisode.name);
                episodeTags.setImdbId(showTags.getImdbId());
                log.trace("getEpisodes: showId=" + showId + " episode has onlineId=" + tvEpisode.id);
                episodeTags.setOnlineId(tvEpisode.id);
                episodeTags.setAired(tvEpisode.air_date);
                episodeTags.setEpisode(tvEpisode.episode_number);
                episodeTags.setSeason(tvEpisode.season_number);
                episodeTags.setShowId(showId);
                episodeTags.setShowTags(showTags);
                if (tvEpisode.still_path != null) {
                    log.trace("getEpisodes: showId=" + showId + " episode has still=" + tvEpisode.still_path);
                    episodeTags.setEpisodePicture(tvEpisode.still_path, context, false);
                } else {
                    // TODO MARC wrong AR it is picture style
                    log.trace("getEpisodes: showId=" + showId + " episode has null still using season poster instead...");
                    // when no still revert to episodeTags.getDefaultPoster() that should be the season poster (global poster is showTags.getDefaultPoster().getLargeUrl())
                    if (episodeTags.getDefaultPoster() != null)
                        episodeTags.setEpisodePicture(episodeTags.getDefaultPoster().getLargeUrl(), context, true);
                    else // we get the tvShow global poster
                        episodeTags.setEpisodePicture(showTags.getDefaultPoster().getLargeUrl(), context, true);
                }
                if ((tvEpisode.overview == null || tvEpisode.overview.length() == 0 || tvEpisode.name == null || tvEpisode.name.length() == 0)
                        && !language.equals("en")) { // missing overview in native language
                    if (globalEpisodes.get(tvEpisode.id) == null) { // missing: get whole serie
                        log.debug("getEpisodes: description in " + language + " missing for tvEpisode.name s" + tvEpisode.season_number + "e" + tvEpisode.episode_number + "fallback in en for the whole season");
                        ShowIdSeasonSearchResult globalSeasonIdSearchResult = ShowIdSeasonSearch.getSeasonShowResponse(showId, tvEpisode.season_number, "en", tmdb);
                        // stack all episodes in en to find later the overview and name
                        if (globalSeasonIdSearchResult.status == ScrapeStatus.OKAY) {
                            if (globalSeasonIdSearchResult.tvSeason != null) {
                                for (TvEpisode globalTvEpisode : globalSeasonIdSearchResult.tvSeason.episodes)
                                    globalEpisodes.put(globalTvEpisode.id, globalTvEpisode);
                            } else { // an error at this point is PARSER related
                                log.debug("getEpisodes: error " + globalSeasonIdSearchResult.status);
                            }
                        }
                    }
                    // only use globalEpisode if an overview if not found
                    TvEpisode globalEpisode = globalEpisodes.get(tvEpisode.id);
                    if (globalEpisode != null) {
                        if (tvEpisode.overview == null || tvEpisode.overview.length() == 0)
                            episodeTags.setPlot(globalEpisode.overview);
                        if (tvEpisode.name == null)
                            episodeTags.setTitle(globalEpisode.name);
                    }
                }
                episodes.put(tvEpisode.season_number + "|" + tvEpisode.episode_number, episodeTags);
            }
        }
        return episodes;
    }
}
