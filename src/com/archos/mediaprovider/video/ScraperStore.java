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

package com.archos.mediaprovider.video;

import android.net.Uri;

import com.archos.mediaprovider.ArchosMediaCommon;
import com.archos.mediascraper.BaseTags;


public final class ScraperStore {
    public static final String AUTHORITY = ArchosMediaCommon.AUTHORITY_SCRAPER;
    public static final Uri ALL_CONTENT_URI = Uri.parse("content://" + AUTHORITY);
    public static final int SCRAPER_TYPE_MOVIE = BaseTags.MOVIE;
    public static final int SCRAPER_TYPE_SHOW = BaseTags.TV_SHOW;

    private static final String CONTENT_AUTHORITY = "content://" + AUTHORITY;

    public static class Movie {
        public static final String ID = "_id";
        public static final String VIDEO_ID = "video_id";
        public static final String NAME = "name_movie";
        public static final String YEAR = "year_movie";
        public static final String RATING = "rating_movie";
        public static final String COVER = "cover_movie";
        public static final String PLOT = "plot_movie";
        /** place to store local path to backdrop image */
        public static final String BACKDROP = "backdrop_movie";
        /** the url to a backdrop image */
        public static final String BACKDROP_URL = "backdrop_url_movie";
        public static final String POSTER_ID = "m_poster_id";
        public static final String BACKDROP_ID = "m_backdrop_id";
        /** id in online db "1858" > http://www.themoviedb.org/movie/1858 */
        public static final String ONLINE_ID = "m_online_id";
        /** IMDb id "tt0285331" > http://www.imdb.com/title/tt0285331 */
        public static final String IMDB_ID = "m_imdb_id";
        /** content rating like "PG-13" */
        public static final String CONTENT_RATING = "m_content_rating";
        /** actors preformatted */
        public static final String ACTORS_FORMATTED = "m_actors";
        /** directors preformatted - usually empty, use episode instead */
        public static final String DIRECTORS_FORMATTED = "m_directors";
        /** genres preformatted */
        public static final String GERNES_FORMATTED = "m_genres";
        /** studios preformatted */
        public static final String STUDIOS_FORMATTED = "m_studios";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/movie");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/movies");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/movie/id/");
            public static final Uri ALL_INFOS = Uri.parse(CONTENT_AUTHORITY + "/tags/movie/full/");
        }

        public static class Actor {
            public static final String MOVIE = "movie_v_plays_movie";
            public static final String ACTOR = "actor_v_plays_movie";
            public static final String NAME = "name_v_plays_movie";
            public static final String ROLE = "role_v_plays_movie";
        }

        public static class Director {
            public static final String MOVIE = "movie_v_films_movie";
            public static final String NAME = "name_v_films_movie";
            public static final String DIRECTOR = "director_v_films_movie";
        }

        public static class Studio {
            public static final String MOVIE = "movie_v_produces_movie";
            public static final String NAME = "name_v_produces_movie";
            public static final String STUDIO = "studio_v_produces_movie";
        }

        public static class Genre {
            public static final String MOVIE = "movie_v_belongs_movie";
            public static final String NAME = "name_v_belongs_movie";
            public static final String GENRE = "genre_v_belongs_movie";
        }
    }

    public static class Show {
        public static final String ID = "_id";
        public static final String NAME = "name_show";
        public static final String COVER = "cover_show";
        public static final String PREMIERED = "premiered_show";
        public static final String RATING = "rating_show";
        public static final String PLOT = "plot_show";
        /** place to store local path to backdrop image */
        public static final String BACKDROP = "backdrop_show";
        /** the url to a backdrop image */
        public static final String BACKDROP_URL = "backdrop_url_show";
        public static final String POSTER_ID = "s_poster_id";
        public static final String BACKDROP_ID = "s_backdrop_id";
        /** id in online db "73255" > http://thetvdb.com/?tab=series&id=73255 */
        public static final String ONLINE_ID = "s_online_id";
        /** IMDb id "tt0285331" > http://www.imdb.com/title/tt0285331 */
        public static final String IMDB_ID = "s_imdb_id";
        /** content rating like "TV-14" */
        public static final String CONTENT_RATING = "s_content_rating";
        /** actors preformatted */
        public static final String ACTORS_FORMATTED = "s_actors";
        /** directors preformatted - usually empty, use episode instead */
        public static final String DIRECTORS_FORMATTED = "s_directors";
        /** genres preformatted */
        public static final String GERNES_FORMATTED = "s_genres";
        /** studios preformatted */
        public static final String STUDIOS_FORMATTED = "s_studios";


        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/show");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/show/id/");
            public static final Uri NAME = Uri.parse(CONTENT_AUTHORITY + "/tags/show/name/");
            public static final Uri ALL_INFOS = Uri.parse(CONTENT_AUTHORITY + "/tags/show/full/id/");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/shows/");
        }

        public static class Actor {
            public static final String SHOW = "show_v_plays_show";
            public static final String ACTOR = "actor_v_plays_show";
            public static final String NAME = "name_v_plays_show";
            public static final String ROLE = "role_v_plays_show";
        }

        public static class Director {
            public static final String SHOW = "show_v_films_show";
            public static final String NAME = "name_v_films_show";
            public static final String DIRECTOR = "director_v_films_show";
        }

        public static class Studio {
            public static final String SHOW = "show_v_produces_show";
            public static final String NAME = "name_v_produces_show";
            public static final String STUDIO = "studio_v_produces_show";
        }

        public static class Genre {
            public static final String SHOW = "show_v_belongs_show";
            public static final String NAME = "name_v_belongs_show";
            public static final String GENRE = "genre_v_belongs_show";
        }
    }

    public static class EpisodeShowCombined {
        public static final String SCRAPER_ID = "episode._id";
        public static final String SHOW_NAME = "show.name_show";
        public static final String SHOW_COVER = "show.cover_show";
        public static final String EPISODE_NAME = "episode.name_episode";
        public static final String EPISODE_SEASON = "episode.season_episode";
        public static final String EPISODE_NUMBER = "episode.number_episode";
        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/episodeshowcomb");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/episodeshowcomb/all/");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/episodeshowcomb/id/");
        }
    }

    public static class AllVideos {
        public static final String SCRAPER_TYPE = "scraper_type";
        public static final String SCRAPER_ID = "scraper_id";
        public static final String MOVIE_OR_SHOW_NAME = "name";
        public static final String EPISODE_NAME = "name_episode";
        public static final String EPISODE_NUMBER = "number_episode";
        public static final String EPISODE_SEASON_NUMBER = "season_episode";
        public static final String MOVIE_YEAR = "year_movie";
        public static final String SHOW_PREMIERED = "premiered_show";
        public static final String EPISODE_AIRED = "aired_episode";
        public static final String MOVIE_OR_SHOW_RATING = "rating";
        public static final String EPISODE_RATING = "rating_episode";
        public static final String MOVIE_OR_SHOW_COVER = "cover";
        public static final String MOVIE_OR_SHOW_BACKDROP = "backdrop";
        public static final String MOVIE_OR_SHOW_BACKDROP_URL = "backdrop_url";
        public static final String MOVIE_OR_SHOW_PLOT = "plot";
        public static final String EPISODE_PLOT = "plot_episode";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/allvideos");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/allvideos/all/");
        }
    }

    public static class Seasons {
        public static final String SHOW_ID = "show_id";
        public static final String SEASON = "season";
        public static final String EPISODE_NUMBERS = "episode_numbers";
        public static final String EPISODE_IDS = "episode_ids";
        public static final String VIDEO_IDS = "video_ids";
        public static final String EPISODE_COUNT = "episode_count";

        public static class URI {
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/seasons");
        }
    }

    public static class Episode {
        public static final String ID = "_id";
        public static final String VIDEO_ID = "video_id";
        public static final String NAME = "name_episode";
        public static final String AIRED = "aired_episode";
        public static final String RATING = "rating_episode";
        public static final String PLOT = "plot_episode";
        public static final String SEASON = "season_episode";
        public static final String NUMBER = "number_episode";
        public static final String SHOW = "show_episode";
        public static final String COVER = "cover_episode";
        public static final String POSTER_ID = "e_poster_id";
        public static final String PICTURE = "picture_episode";
        /** (episode) id in online db "306192" > http://thetvdb.com/?tab=episode&seriesid=73255&id=306192 */
        public static final String ONLINE_ID = "e_online_id";
        /** (rarely) IMDb id "tt0285331" > http://www.imdb.com/title/tt0285331 */
        public static final String IMDB_ID = "e_imdb_id";
        /** actors (guests) preformatted */
        public static final String ACTORS_FORMATTED = "e_actors";
        /** directors preformatted */
        public static final String DIRECTORS_FORMATTED = "e_directors";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/episode");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/episode/id/");
            public static final Uri ALL_INFOS = Uri.parse(CONTENT_AUTHORITY + "/tags/episode/full/");
            public static final Uri ALL_SEASONS = Uri.parse(CONTENT_AUTHORITY + "/tags/seasons/");
            public static final Uri SHOW = Uri.parse(CONTENT_AUTHORITY + "/tags/show/");
        }

        public static class Actor {
            public static final String EPISODE = "episode_v_guests";
            public static final String ACTOR = "actor_v_guests";
            public static final String NAME = "name_v_guests";
            public static final String ROLE = "role_v_guests";
        }

        public static class Director {
            public static final String EPISODE = "episode_v_films_episode";
            public static final String NAME = "name_v_films_episode";
            public static final String DIRECTOR = "director_v_films_episode";
        }
    }

    public static class Actor {
        public static final String ID = "_id";
        public static final String NAME = "name_actor";
        public static final String COUNT = "count_actor";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/actor");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/actor/id/");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/actors");
            public static final Uri MOVIE = Uri.parse(CONTENT_AUTHORITY + "/tags/actor/movie/");
            public static final Uri SHOW = Uri.parse(CONTENT_AUTHORITY + "/tags/actor/show/");
            public static final Uri EPISODE = Uri.parse(CONTENT_AUTHORITY + "/tags/actor/episode/");
            public static final Uri NAME = Uri.parse(CONTENT_AUTHORITY + "/tags/actor/name/");
        }
    }

    public static class Genre {
        public static final String ID = "_id";
        public static final String NAME = "name_genre";
        public static final String COUNT = "count_genre";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/genre");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/genre/id/");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/genres");
            public static final Uri MOVIE = Uri.parse(CONTENT_AUTHORITY + "/tags/genre/movie/");
            public static final Uri SHOW = Uri.parse(CONTENT_AUTHORITY + "/tags/genre/show/");
            public static final Uri NAME = Uri.parse(CONTENT_AUTHORITY + "/tags/genre/name/");
        }
    }

    public static class Studio {
        public static final String ID = "_id";
        public static final String NAME = "name_studio";
        public static final String COUNT = "count_studio";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/studio");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/studio/id/");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/studios");
            public static final Uri MOVIE = Uri.parse(CONTENT_AUTHORITY + "/tags/studio/movie/");
            public static final Uri SHOW = Uri.parse(CONTENT_AUTHORITY + "/tags/studio/show/");
            public static final Uri NAME = Uri.parse(CONTENT_AUTHORITY + "/tags/studio/name/");
        }
    }

    public static class Director {
        public static final String ID = "_id";
        public static final String NAME = "name_director";
        public static final String COUNT = "count_director";

        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/director");
            public static final Uri ID = Uri.parse(CONTENT_AUTHORITY + "/tags/director/id/");
            public static final Uri ALL = Uri.parse(CONTENT_AUTHORITY + "/tags/directors");
            public static final Uri MOVIE = Uri.parse(CONTENT_AUTHORITY + "/tags/director/movie/");
            public static final Uri SHOW = Uri.parse(CONTENT_AUTHORITY + "/tags/director/show/");
            public static final Uri EPISODE = Uri.parse(CONTENT_AUTHORITY + "/tags/director/episode/");
            public static final Uri NAME = Uri.parse(CONTENT_AUTHORITY + "/tags/director/name/");
        }
    }

    public static class MoviePosters {
        public static final String ID = "_id";
        public static final String MOVIE_ID = "movie_id";
        public static final String THUMB_URL = "m_po_thumb_url";
        public static final String THUMB_FILE = "m_po_thumb_file";
        public static final String LARGE_URL = "m_po_large_url";
        public static final String LARGE_FILE = "m_po_large_file";
        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/movieposters");
            public static final Uri BY_MOVIE_ID = Uri.parse(CONTENT_AUTHORITY + "/tags/movieposters/byremote");
        }
    }
    public static class MovieBackdrops {
        public static final String ID = "_id";
        public static final String MOVIE_ID = "movie_id";
        public static final String THUMB_URL = "m_bd_thumb_url";
        public static final String THUMB_FILE = "m_bd_thumb_file";
        public static final String LARGE_URL = "m_bd_large_url";
        public static final String LARGE_FILE = "m_bd_large_file";
        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/moviebackdrops");
            public static final Uri BY_MOVIE_ID = Uri.parse(CONTENT_AUTHORITY + "/tags/moviebackdrops/byremote");
        }
    }
    public static class ShowPosters {
        public static final String ID = "_id";
        public static final String SHOW_ID = "show_id";
        public static final String THUMB_URL = "s_po_thumb_url";
        public static final String THUMB_FILE = "s_po_thumb_file";
        public static final String LARGE_URL = "s_po_large_url";
        public static final String LARGE_FILE = "s_po_large_file";
        public static final String SEASON = "s_po_season";
        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/showposters");
            public static final Uri BY_SHOW_ID = Uri.parse(CONTENT_AUTHORITY + "/tags/showposters/byremote");
        }
    }
    public static class ShowBackdrops {
        public static final String ID = "_id";
        public static final String SHOW_ID = "show_id";
        public static final String THUMB_URL = "s_bd_thumb_url";
        public static final String THUMB_FILE = "s_bd_thumb_file";
        public static final String LARGE_URL = "s_bd_large_url";
        public static final String LARGE_FILE = "s_bd_large_file";
        public static class URI {
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/showbackdrops");
            public static final Uri BY_SHOW_ID = Uri.parse(CONTENT_AUTHORITY + "/tags/showbackdrops/byremote");
        }
    }

    public static class MovieTrailers {
        public static final String ID = "_id";
        public static final String MOVIE_ID = "movie_id";
        public static final String VIDEO_KEY = "m_t_key";
        public static final String SITE = "m_t_site";
        public static final String NAME = "m_t_name";
        public static final String LANG = "m_t_lang";
        public static class URI { //these uris are interpretated by scraperprovider
            public static final Uri BASE = Uri.parse(CONTENT_AUTHORITY + "/tags/movietrailers");
            public static final Uri BY_MOVIE_ID = Uri.parse(CONTENT_AUTHORITY + "/tags/movietrailers/byremote");
        }
    }

}
