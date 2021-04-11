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

// Get the episodes for specific show id
public class ShowIdEpisodes {
    private static final Logger log = LoggerFactory.getLogger(ShowIdEpisodes.class);

    private static final String DIRECTOR = "Director";

    public static ShowIdEpisodesResult getEpisodes(int showId, ShowTags showTags, String language,
                                                     MyTmdb tmdb, Context context) {
        ShowIdEpisodesResult myResult = new ShowIdEpisodesResult();
        myResult.episodes = new HashMap<>();
        // TODO MARC Map<String, EpisodeTags> episodes = Collections.<String, EpisodeTags>emptyMap();
        log.debug("getEpisodes: quering thetvdb for showId " + showId);
        // fill in once for all episodes in "en" in case there is something missing in specific language
        SparseArray<TvEpisode> globalEpisodes = null;

        ShowIdSearchResult showIdSearchResult = ShowIdSearch.getTvShowResponse(showId, language, tmdb);
        ShowIdSearchResult globalShowIdSearchResult = new ShowIdSearchResult();

        if (showIdSearchResult.status == ScrapeStatus.OKAY) {
            if (showIdSearchResult.tvShow != null) {
                for(TvSeason tvSeason : showIdSearchResult.tvShow.seasons) {
                    for (TvEpisode tvEpisode : tvSeason.episodes) {
                        EpisodeTags episodeTags = new EpisodeTags();
                        // TODO MARC check if not tvEpisode.guest_stars and tvEpisode.crew instead
                        if (tvEpisode.credits != null) {
                            if (tvEpisode.credits.guest_stars != null)
                                for (CastMember guestStar : tvEpisode.credits.guest_stars)
                                    episodeTags.addActorIfAbsent(guestStar.name, guestStar.character);
                            if (tvEpisode.credits.cast != null)
                                for (CastMember actor : tvEpisode.credits.cast)
                                    episodeTags.addActorIfAbsent(actor.name, actor.character);
                            if (tvEpisode.credits.crew != null)
                                for (CrewMember crew : tvEpisode.credits.crew)
                                    if (crew.job == DIRECTOR)
                                        episodeTags.addDirectorIfAbsent(crew.name);
                        }
                        episodeTags.setPlot(tvEpisode.overview);
                        episodeTags.setRating(tvEpisode.vote_average.floatValue());
                        episodeTags.setTitle(tvEpisode.name);
                        episodeTags.setImdbId(tvEpisode.external_ids.imdb_id);
                        episodeTags.setOnlineId(tvEpisode.id);
                        episodeTags.setAired(tvEpisode.air_date);
                        episodeTags.setEpisode(tvEpisode.episode_number);
                        episodeTags.setSeason(tvEpisode.season_number);
                        episodeTags.setShowTags(showTags);
                        // TODO MARC check it is the still here
                        episodeTags.setEpisodePicture(tvEpisode.still_path, context);
                        if ((tvEpisode.overview == null || tvEpisode.name == null)
                                && !language.equals("en")) { // missing overview in native language
                            if (globalEpisodes == null) { // do it only once
                                globalEpisodes = new SparseArray<>();
                                if (globalShowIdSearchResult.tvShow == null) {
                                    globalShowIdSearchResult = ShowIdSearch.getTvShowResponse(showId, "en", tmdb);
                                    // stack all episodes in en to find later the overview and name
                                    if (globalShowIdSearchResult.status == ScrapeStatus.OKAY) {
                                        if (globalShowIdSearchResult.tvShow != null) {
                                            for (TvSeason globalTvSeason : globalShowIdSearchResult.tvShow.seasons)
                                                for (TvEpisode globalTvEpisode : globalTvSeason.episodes)
                                                    globalEpisodes.put(globalTvEpisode.id, globalTvEpisode);
                                        } else { // an error at this point is PARSER related
                                            log.debug("getEpisodes: error " + globalShowIdSearchResult.status);
                                        }
                                    }
                                }
                            }
                            // only use globalEpisode if an overview if not found
                            TvEpisode globalEpisode = globalEpisodes.get(tvEpisode.id);
                            if (globalEpisode != null) {
                                if (tvEpisode.overview == null)
                                    episodeTags.setPlot(globalEpisode.overview);
                                if (tvEpisode.name == null)
                                    episodeTags.setTitle(globalEpisode.name);
                            }
                        }
                        myResult.episodes.put(tvEpisode.season_number + "|" + tvEpisode.episode_number, episodeTags);
                    }
                }
                myResult.status = ScrapeStatus.OKAY;
            } else {
                myResult.status = ScrapeStatus.NOT_FOUND;
                myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
            }
        } else { // an error at this point is PARSER related
            log.debug("getEpisodes: error " + showIdSearchResult.status);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
        }
        return myResult;
    }
}
