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

package com.archos.mediascraper.thetvdb;

import android.content.Context;
import android.util.SparseArray;

import com.archos.mediascraper.EpisodeTags;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.Episode;
import com.uwetrottmann.thetvdb.entities.EpisodesResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

import retrofit2.Response;

// Get the episodes for specific show id
public class ShowIdEpisodes {
    private static final Logger log = LoggerFactory.getLogger(ShowIdEpisodes.class);

    public static ShowIdEpisodesResult getEpisodes(int showId, ShowTags showTags, String language,
                                                     MyTheTVdb theTvdb, Context context) {
        ShowIdEpisodesResult myResult = new ShowIdEpisodesResult();
        myResult.episodes = new HashMap<>();
        log.debug("getEpisodes: quering thetvdb for showId " + showId);
        try {
            log.debug("ShowIdEpisodesResult: no boost for " + showId);
            // fill in once for all episodes in "en" in case there is something missing in specific language
            SparseArray<Episode> globalEpisodes = null;
            Response<EpisodesResponse> globalEpisodesResponse = null;
            Integer page = 1;
            Response<EpisodesResponse> episodesResponse = null;
            while (page != null) {
                episodesResponse = theTvdb.series()
                        .episodes(showId, page, language)
                        .execute();
                switch (episodesResponse.code()) {
                    case 401: // auth issue
                        log.debug("getEpisodes: auth error");
                        myResult.status = ScrapeStatus.AUTH_ERROR;
                        myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
                        ShowScraper3.reauth();
                        return myResult;
                    case 404: // not found
                        myResult.status = ScrapeStatus.NOT_FOUND;
                        myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
                        page = null;
                        break;
                    default:
                        if (episodesResponse.isSuccessful()) {
                            if (episodesResponse.body() != null) {
                                //parserResult = ShowIdEpisodesParser.getResult(episodesResponse.body(), showTags);
                                //myResult.actors = parserResult;
                                for(Episode episode : episodesResponse.body().data) {
                                    EpisodeTags episodeTags = new EpisodeTags();
                                    episodeTags.setActors(episode.guestStars);
                                    episodeTags.setDirectors(episode.directors);
                                    episodeTags.setPlot(episode.overview);
                                    episodeTags.setRating(episode.siteRating.floatValue());
                                    episodeTags.setTitle(episode.episodeName);
                                    episodeTags.setImdbId(episode.imdbId);
                                    episodeTags.setOnlineId(episode.id);
                                    episodeTags.setAired(episode.firstAired);
                                    episodeTags.setEpisode(episode.airedEpisodeNumber);
                                    episodeTags.setSeason(episode.airedSeason);
                                    episodeTags.setShowTags(showTags);
                                    episodeTags.setEpisodePicture(episode.filename, context, false);
                                    /*
                                    if (genericImage != null)
                                        episodeTags.setPosters(genericImage.asList());
                                     */
                                    if ((episode.overview == null || episode.episodeName == null)
                                            && !language.equals("en")) { // missing overview in native language
                                        if (globalEpisodes == null) { // do it only once
                                            globalEpisodes = new SparseArray<>();
                                            Integer globalPage = 1;
                                            while (globalPage != null) {
                                                globalEpisodesResponse = theTvdb.series()
                                                        .episodes(showId, globalPage, "en")
                                                        .execute();
                                                switch (globalEpisodesResponse.code()) {
                                                    case 401: // auth issue
                                                        log.debug("getEpisodes: auth error");
                                                        myResult.status = ScrapeStatus.AUTH_ERROR;
                                                        myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
                                                        ShowScraper3.reauth();
                                                        return myResult;
                                                    case 404: // not found
                                                        globalPage = null;
                                                        break;
                                                    default:
                                                        if (globalEpisodesResponse.isSuccessful() && globalEpisodesResponse.body() != null) {
                                                            for (Episode globalEpisode : globalEpisodesResponse.body().data)
                                                                globalEpisodes.put(globalEpisode.id, globalEpisode);
                                                            globalPage = globalEpisodesResponse.body().links.next;

                                                        } else { // an error at this point is PARSER related
                                                            log.debug("getEpisodes: error " + globalEpisodesResponse.code());
                                                            globalPage = null;
                                                        }
                                                        break;
                                                }
                                            }
                                        }
                                        Episode globalEpisode = globalEpisodes.get(episode.id);
                                        if (globalEpisode != null) {
                                            if (episode.overview == null)
                                                episodeTags.setPlot(globalEpisode.overview);
                                            if (episode.episodeName == null)
                                                episodeTags.setTitle(globalEpisode.episodeName);
                                        }
                                    }
                                    myResult.episodes.put(episode.airedSeason + "|" + episode.airedEpisodeNumber, episodeTags);
                                }
                                page = episodesResponse.body().links.next;
                                myResult.status = ScrapeStatus.OKAY;
                            } else {
                                myResult.status = ScrapeStatus.NOT_FOUND;
                                myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
                                page = null;
                            }
                        } else { // an error at this point is PARSER related
                            log.debug("getEpisodes: error " + episodesResponse.code());
                            myResult.status = ScrapeStatus.ERROR_PARSER;
                            myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
                            page = null;
                        }
                        break;
                }
            }
        } catch (IOException e) {
            log.error("getEpisodes: caught IOException getting episodes for showId=" + showId);
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.episodes = ShowIdEpisodesResult.EMPTY_MAP;
            myResult.reason = e;
        }
        return myResult;
    }
}
