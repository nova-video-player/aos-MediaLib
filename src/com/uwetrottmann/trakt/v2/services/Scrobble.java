package com.uwetrottmann.trakt.v2.services;

import com.archos.mediacenter.utils.trakt.Trakt;
import com.archos.mediacenter.utils.trakt.TraktAPI;
import com.archos.mediacenter.utils.trakt.TraktAPI.Response;
import com.archos.mediacenter.utils.trakt.TraktAPI.ShowPerSeason;
import com.uwetrottmann.trakt.v2.entities.BaseMovie;
import com.uwetrottmann.trakt.v2.entities.BaseShow;
import com.uwetrottmann.trakt.v2.entities.GenericProgress;
import com.uwetrottmann.trakt.v2.entities.LastActivities;
import com.uwetrottmann.trakt.v2.entities.Progress;
import com.uwetrottmann.trakt.v2.entities.RatedEpisode;
import com.uwetrottmann.trakt.v2.entities.RatedMovie;
import com.uwetrottmann.trakt.v2.entities.RatedSeason;
import com.uwetrottmann.trakt.v2.entities.RatedShow;
import com.uwetrottmann.trakt.v2.entities.SyncItems;
import com.uwetrottmann.trakt.v2.entities.SyncResponse;
import com.uwetrottmann.trakt.v2.entities.WatchlistedEpisode;
import com.uwetrottmann.trakt.v2.enums.Extended;
import com.uwetrottmann.trakt.v2.enums.RatingsFilter;
import com.uwetrottmann.trakt.v2.exceptions.OAuthUnauthorizedException;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.EncodedPath;
import retrofit.http.EncodedQuery;
import retrofit.http.GET;
import retrofit.http.POST;

import java.util.List;
/**
 *
 * @author alexandre roux
 *
 */
public interface Scrobble {
	/**
     * <b>OAuth Required</b>
     *
     * <p> User starts a video
     */

    @POST("/scrobble/start")
    Response startWatching(
            @Body Progress prog) throws OAuthUnauthorizedException;
	/**
     * <b>OAuth Required</b>
     *
     * <p> User pauses a video
     */

    @POST("/scrobble/pause")
    void pauseWatching(
            @Body Progress prog,Callback<Response> res
    ) throws OAuthUnauthorizedException;
	/**
     * <b>OAuth Required</b>
     *
     * <p> User stops a video
     */

    @POST("/scrobble/stop")
    TraktAPI.Response stopWatching(
            @Body Progress prog) throws OAuthUnauthorizedException;

  
}
