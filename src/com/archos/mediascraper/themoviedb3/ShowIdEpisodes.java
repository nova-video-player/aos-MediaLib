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
import com.archos.mediascraper.ScraperImage;
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
    private static final String WRITER = "Writer";

    // Cache season poster ScraperImage objects to avoid redundant object creation and hash computations
    // when multiple episodes share the same season poster
    private static final Map<String, ScraperImage> sSeasonPosterImageCache = new HashMap<>();

    public static Map<String, EpisodeTags> getEpisodes(String seasonKey, int showId, List<TvEpisode> tvEpisodes, Map<Integer, TvSeason> tvSeasons, ShowTags showTags, String language,
                                                       final boolean adultScrape, MyTmdb tmdb, Context context) {

        Map<String, EpisodeTags> episodes = new HashMap<>();
        TvSeason tvSeason;
        // fill in once for all episodes in "en" in case there is something missing in specific language
        SparseArray<TvEpisode> globalEpisodes = new SparseArray<>();

        if (tvEpisodes != null) {
            for (TvEpisode tvEpisode : tvEpisodes) {
                if (log.isDebugEnabled()) log.debug("getEpisodes: filling showid {} s{}e{}", showId, tvEpisode.season_number, tvEpisode.episode_number);
                EpisodeTags episodeTags = new EpisodeTags();
                // note: tvEpisode.credits is null thus use tvEpisode.guest_stars and tvEpisode.crew instead
                if (tvEpisode.guest_stars != null) {
                    for (CastMember guestStar : tvEpisode.guest_stars)
                        episodeTags.addActorIfAbsent(guestStar.name, guestStar.character);
                } else {
                    if (log.isDebugEnabled()) log.debug("getEpisodes: guest_star is null for showId {}", showId);
                }
                if (tvEpisode.crew != null) {
                    for (CrewMember crew : tvEpisode.crew) {
                        assert crew.job != null;
                        if (crew.job.equals(DIRECTOR))
                            episodeTags.addDirectorIfAbsent(crew.name);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("getEpisodes: crew is null for showId {}", showId);
                }
                if (tvEpisode.crew != null) {
                    for (CrewMember crew : tvEpisode.crew) {
                        assert crew.job != null;
                        if (crew.job.equals(WRITER))
                            episodeTags.addWriterIfAbsent(crew.name);
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("getEpisodes: crew is null for showId {}", showId);
                }

                // set episode poster according to corresponding season poster
                if (log.isDebugEnabled()) log.debug("getEpisodes: tvEpisode.season_number={}, tvSeasons.size={}", tvEpisode.season_number, tvSeasons.size());

                if (tvSeasons != null) {
                    tvSeason = tvSeasons.get(tvEpisode.season_number);
                    if (tvSeason != null) {
                        if (tvSeason.credits != null && tvSeason.credits.cast != null) {
                            for (CastMember seasonStar : tvSeason.credits.cast)
                                episodeTags.addActorIfAbsent(seasonStar.name, seasonStar.character);
                        }
                        // note tvSeason.poster_path can be null when show has only one serie e.g. https://api.themoviedb.org/3/tv/93911/season/1?language=en&api_key=051012651ba326cf5b1e2f482342eaa2
                        if (tvSeason.poster_path != null) {
                            // Cache ScraperImage objects to avoid redundant creation for episodes sharing the same season poster
                            String posterKey = showId + "|" + tvSeason.poster_path + "|" + language;
                            ScraperImage seasonPoster = sSeasonPosterImageCache.get(posterKey);
                            if (seasonPoster == null) {
                                seasonPoster = ShowIdImagesParser.genPoster(showTags.getTitle(), tvSeason.poster_path, language, false, context);
                                sSeasonPosterImageCache.put(posterKey, seasonPoster);
                                if (log.isTraceEnabled()) log.trace("getEpisodes: cached season poster ScraperImage for {}", posterKey);
                            } else {
                                if (log.isTraceEnabled()) log.trace("getEpisodes: reusing cached season poster ScraperImage for {}", posterKey);
                            }
                            episodeTags.addDefaultPoster(seasonPoster);
                        } else {
                            if (log.isDebugEnabled()) log.debug("getEpisodes: tvSeason.poster_path is null get showTag default poster");
                            episodeTags.addDefaultPoster(showTags.getDefaultPoster());
                        }
                        if (log.isDebugEnabled()) log.debug("getEpisodes: {} s{}, has poster {} path {}", showTags.getTitle(), tvEpisode.season_number, episodeTags.getDefaultPoster() != null ? episodeTags.getDefaultPoster().getLargeUrl() : "null", episodeTags.getDefaultPoster() != null ? episodeTags.getDefaultPoster().getLargeFile() : "null");
                    }
                } else {
                    if (log.isDebugEnabled()) log.debug("getEpisodes: no poster set for {}s{}", showTags.getTitle(), tvEpisode.season_number);
                }
                if (log.isDebugEnabled()) log.debug("getEpisodes: {} has plot {}", tvEpisode.name, tvEpisode.overview);
                episodeTags.setPlot(tvEpisode.overview);
                episodeTags.setRating(Math.round(tvEpisode.vote_average.floatValue() * 10)/10.0f); // round up first decimal
                episodeTags.setTitle(tvEpisode.name);
                if (tvEpisode.external_ids != null) episodeTags.setImdbId(tvEpisode.external_ids.imdb_id);
                if (log.isTraceEnabled()) log.trace("getEpisodes: showId={} episode has onlineId={}", showId, tvEpisode.id);
                episodeTags.setOnlineId(tvEpisode.id);
                episodeTags.setAired(tvEpisode.air_date);
                episodeTags.setEpisode(tvEpisode.episode_number);
                episodeTags.setSeason(tvEpisode.season_number);
                episodeTags.setShowId(showId);
                episodeTags.setShowTags(showTags);
                if (tvEpisode.still_path != null) {
                    if (log.isTraceEnabled()) log.trace("getEpisodes: showId={} episode has still={}", showId, tvEpisode.still_path);
                    episodeTags.setEpisodePicture(tvEpisode.still_path, context, false);
                } else {
                    // TODO MARC wrong AR it is picture style
                    if (log.isTraceEnabled()) log.trace("getEpisodes: showId={} episode has null still using season poster instead...", showId);
                    // when no still revert to episodeTags.getDefaultPoster() that should be the season poster (global poster is showTags.getDefaultPoster().getLargeUrl())
                    if (episodeTags.getDefaultPoster() != null)
                        episodeTags.setEpisodePicture(episodeTags.getDefaultPoster().getLargeUrl(), context, true);
                    else if (showTags.getDefaultPoster() != null) // we get the tvShow global poster
                        episodeTags.setEpisodePicture(showTags.getDefaultPoster().getLargeUrl(), context, true);
                    else
                        if (log.isDebugEnabled()) log.debug("getEpisodes: no poster available for showId={} s{}e{}", showId, tvEpisode.season_number, tvEpisode.episode_number);
                }
                if ((tvEpisode.overview == null || tvEpisode.overview.length() == 0 || tvEpisode.name == null || tvEpisode.name.length() == 0)
                        && !language.equals("en")) { // missing overview in native language
                    if (globalEpisodes.get(tvEpisode.id) == null) { // missing: get whole serie
                        if (log.isDebugEnabled()) log.debug("getEpisodes: description in {} missing for tvEpisode.name s{}e{} fallback in en for the whole season", language, tvEpisode.season_number, tvEpisode.episode_number);
                        String fallbackSeasonKey = seasonKey;
                        int lastPipe = seasonKey.lastIndexOf("|");
                        if (lastPipe != -1) {
                            fallbackSeasonKey = seasonKey.substring(0, lastPipe + 1) + "en";
                        }
                        ShowIdSeasonSearchResult globalSeasonIdSearchResult = ShowIdSeasonSearch.getSeasonShowResponse(fallbackSeasonKey, showId, tvEpisode.season_number, "en", adultScrape, tmdb);
                        // stack all episodes in en to find later the overview and name
                        if (globalSeasonIdSearchResult.status == ScrapeStatus.OKAY) {
                            if (globalSeasonIdSearchResult.tvSeason != null && globalSeasonIdSearchResult.tvSeason.episodes != null) {
                                for (TvEpisode globalTvEpisode : globalSeasonIdSearchResult.tvSeason.episodes)
                                    globalEpisodes.put(globalTvEpisode.id, globalTvEpisode);
                            } else { // an error at this point is PARSER related
                                if (log.isDebugEnabled()) log.debug("getEpisodes: error {}", globalSeasonIdSearchResult.status);
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
                episodes.put(showId + "|"  + + tvEpisode.season_number + "|" + tvEpisode.episode_number + "|" + language, episodeTags);
            }
        }
        return episodes;
    }
}
