// Copyright 2017 Archos SA
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

package com.archos.mediacenter.utils.trakt;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Header;
import retrofit.http.Headers;
import retrofit.http.POST;
import retrofit.http.Path;


public interface TraktAPI {

    /*
     * Responses
     */

    public static class Response {
        String status;
        String message;
        String error;
    }

    /*
     * Params
     */

    public static class AuthParam {
        String username;
        String password;
    }

    public static class MovieWatchingParam extends AuthParam {
        String imdb_id;
        String tmdb_id; //optional
        String title;
        String year;
        int duration; // in minutes
        int progress; // %
    }

    public static class Movie {
        String imdb_id;
        String tmdb_id; //optional
        String title;
        String year;
        String last_played;
    }

    public static class MovieListParam extends AuthParam {
        Movie[] movies;
    }

    public static class ShowWatchingParam extends AuthParam {
        String tvdb_id;
        String imdb_id; // optional
        String title;
        String year;
        int season;
        int episode;
        String episode_tvdb_id;  // optional
        int duration; // in minutes
        int progress; // %
    }

    public static class Episode {
        int season;
        int episode;
        int tvdb;
        String watched_at;
        String last_played;
    }
    public static class ID{
        int trakt;
        int tvdb;
        int imdb;
    }
    public static class EpisodeListParam extends AuthParam {
        String tvdb_id;
        String imdb_id; // optional
        Episode[] episodes;
        String title;

        String year;
    }

    public static class ShowPerSeason {
        String title;
        String year;
        String imdb_id; // optional
        String tvdb_id;
        Season seasons[];
    }

    public static class Season {
        int season;
        int episodes[];
    }

    public static class LastActivityElm {
        long watched;
        long collection;
    }

    public static class LastActivity {
        long all;
        LastActivityElm movie;
        LastActivityElm episode;
    }

   
}
