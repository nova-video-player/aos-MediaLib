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

import com.archos.medialib.R;
import com.archos.mediascraper.ScraperImage;
import com.archos.mediascraper.ShowTags;
import com.uwetrottmann.tmdb2.entities.CastMember;
import com.uwetrottmann.tmdb2.entities.ContentRating;
import com.uwetrottmann.tmdb2.entities.CrewMember;
import com.uwetrottmann.tmdb2.entities.Genre;
import com.uwetrottmann.tmdb2.entities.Network;
import com.uwetrottmann.tmdb2.entities.Person;
import com.uwetrottmann.tmdb2.entities.ReleaseDate;
import com.uwetrottmann.tmdb2.entities.TvSeason;
import com.uwetrottmann.tmdb2.entities.TvShow;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ShowIdParser {
    private static final Logger log = LoggerFactory.getLogger(ShowIdParser.class);

    private static final String DIRECTOR = "Director";
    private static final String PRODUCER = "Producer";
    private static final String SCREENPLAY = "Screenplay";
    private static final String MUSICCOMPOSER = "Original Music Composer";
    private static String readUrl(String urlString) throws Exception {
        BufferedReader reader = null;
        try {
            URL url = new URL(urlString);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuffer buffer = new StringBuffer();
            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1)
                buffer.append(chars, 0, read);

            return buffer.toString();
        } finally {
            if (reader != null)
                reader.close();
        }
    }


    private static Context mContext;

    public static ShowTags getResult(TvShow serie, String year, Context context) {
        mContext = context;
        ShowTags result = new ShowTags();

        if (serie.overview != null) {
            log.debug("getResult: " + serie.name + " overview/plot " + serie.overview);
            result.setPlot(serie.overview);
        } else {
            log.warn("getResult: " + serie.name + " has no overview/plot");
        }

        // setting multiple season tags (season number, season  overview, season name, season air date)
        List<String> SeasonPlots = new ArrayList<>();
        String seasonPlot;
        if (serie.seasons != null) {
            for (TvSeason season : serie.seasons) {
                String pattern = "MMMM dd, yyyy";
                DateFormat df = new SimpleDateFormat(pattern);
                String airdate = "";
                String overview = "";
                String seasonNumber = "";
                String name = "";
                if (season.air_date != null){
                    Date date = season.air_date;
                    airdate = df.format(date);
                } else{
                    airdate = "No season air date";
                }
                if (season.overview != null){
                    overview = season.overview;
                } else{
                    overview = "No season overview";
                }
                if (season.name != null){
                    name = season.name;
                } else{
                    name = "No season name";
                }
                if (season.season_number != null){
                    seasonNumber = String.valueOf(season.season_number);
                } else{
                    seasonNumber = "No season number";
                }
                seasonPlot = seasonNumber + "=&%#" + overview + "=&%#" + name + "=&%#" + airdate + "&&&&####";
                SeasonPlots.add(seasonPlot);
                result.setSeasonPlots(SeasonPlots);
            }
        }

        // Utilizing the unused series director as a pipeline for series created by tag
        if (serie.created_by != null) {
            for (Person person : serie.created_by)
            result.addDirectorIfAbsent(person.name); // director = created_by
        }

        // Utilizing the unused series writer as a pipeline for series actor
        List<String> Actors = new ArrayList<>();
        String Actor = "";
        if (serie.credits != null) {
            if (serie.credits.cast != null) {
                for (CastMember actor : serie.credits.cast) {
                    assert actor.name != null;
                    if (!actor.name.isEmpty()) {
                        Actor = actor.name + "=&%#" + actor.character + "=&%#" + actor.profile_path;
                        Actors.add(Actor);
                        result.setWriters(Actors); // writer = actor
                    }
                }
            }
        }

        result.setRating(Math.round(serie.vote_average.floatValue() * 10)/10.0f);
        result.setTitle(serie.name + (( year != null) ? " " + year : ""));

        log.debug("getResult: found title=" + serie.name);

        if (serie.content_ratings.results != null)
            for (ContentRating results: serie.content_ratings.results)
                if (results.iso_3166_1.equals("US"))
                    result.setContentRating(results.rating);

        result.setImdbId(serie.external_ids.imdb_id);
        result.setOnlineId(serie.id);
        log.debug("getResult: onlineId=" + serie.id + ", imdbId=" + serie.external_ids.imdb_id);
        result.setGenres(getLocalizedGenres(serie.genres));

        for (Network network : serie.networks)
            result.addStudioIfAbsent(network.name, '|', ',');

        result.setPremiered(serie.first_air_date);

        if (serie.poster_path != null) {
            log.debug("getResult: " + serie.id + " has poster_path=" + ScraperImage.TMPL + serie.poster_path);
            result.addDefaultPosterTMDB(mContext, serie.poster_path);
        } else log.debug("getResult: no poster_path for " + serie.id);
        if (serie.backdrop_path != null) {
            log.debug("getResult: " + serie.id + " has backdrop_path=" + ScraperImage.TMBL + serie.backdrop_path);
            result.addDefaultBackdropTMDB(mContext, serie.backdrop_path);
        } else log.debug("getResult: no backdrop_path for " + serie.id);

        //set series logo
        String apikey = "ac6ed0ad315f924847ff24fa4f555571";
        String url = "https://webservice.fanart.tv/v3/tv/" + serie.external_ids.tvdb_id + "?api_key=" + apikey;
        List<String> enClearLogos = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(readUrl(url));
            JSONArray resultsff = json.getJSONArray("hdtvlogo");
            for(int i = 0; i < resultsff.length(); i++){
                JSONObject movieObject = resultsff.getJSONObject(i);
                if (movieObject.getString("lang").equalsIgnoreCase("en"))
                    enClearLogos.add(movieObject.getString("url"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (int i = 0; i < enClearLogos.size(); i++) {
            result.addClearLogoFTV(mContext, enClearLogos.get(0));
        }

        // setting multiple series tags using a single pipeline (tagline, type, status, vote_count, popularity, runtime, original language)
        int runtime = 0;
        if (serie.episode_run_time != null) {
            for (int i = 0; i < serie.episode_run_time.size(); i++) {
                runtime = serie.episode_run_time.get(0);
            }
        }
        String tmdbapikey = "?api_key=" + "0fd42d7cf783faf9a5eefeb78e1cc5c9";
        String baseTvUrl = "https://api.themoviedb.org/3/tv/";
        String lang = "&language=en-US";
        String newUrl = baseTvUrl + serie.id + tmdbapikey + lang;
        try {
            JSONObject json = new JSONObject(readUrl(newUrl));
            String tagline = json.getString("tagline"); // tagline is not available from UweTrottmann-tmdb-java
            String tvTag = tagline + "=&%#" + serie.type + "=&%#" + serie.status + "=&%#" + serie.vote_count + "=&%#" + serie.popularity + "=&%#" + runtime + "=&%#" + serie.original_language;
            result.addTaglineIfAbsent(tvTag);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // set Spoken languages
        try {
            JSONObject json = new JSONObject(readUrl(newUrl));
            JSONArray jsonArray = json.getJSONArray("spoken_languages");
            for (int i = 0; i < jsonArray.length(); i++) {
                String languageCode = jsonArray.getJSONObject(i).getString("iso_639_1");
                result.addSpokenlanguageIfAbsent(languageCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }


        if (serie.networks != null) {
                for (int i = 0; i < serie.networks.size(); i++) {
                    log.debug("getResult: " + serie.id + " has networklogo_path=" + ScraperImage.GSNL + serie.networks.get(i).name.replaceAll(" ", "%20").replaceAll("\t", "") + ".png");
                    result.addNetworkLogoGITHUB(mContext, serie.networks.get(i).name.replaceAll(" ", "%20").replaceAll("\t", "") + ".png");
                }
        } else log.debug("getResult: no networklogo_path for " + serie.id);

        if (serie.production_companies != null) {
            for (int i = 0; i < serie.production_companies.size(); i++) {
                log.debug("getResult: " + serie.id + " has studiologo_path=" + ScraperImage.GSNL + serie.production_companies.get(i).name.replaceAll(" ", "%20").replaceAll("/", "%20").replaceAll("\t", "") + ".png");
                result.addStudioLogoGITHUB(mContext, serie.production_companies.get(i).name.replaceAll(" ", "%20").replaceAll("/", "%20").replaceAll("\t", "") + ".png");
            }
        } else log.debug("getResult: no networklogo_path for " + serie.id);


        if (serie.origin_country != null) {
            for (int i = 0; i < serie.origin_country.size(); i++) {
                result.addCountryIfAbsent(serie.origin_country.get(i));
            }
        } else log.debug("getResult: no origin_country for " + serie.id);

        if (serie.credits != null) {
            if (serie.credits.cast != null)
                    for (int i = 0; i < serie.credits.cast.size(); i++) {
                        result.addActorPhotoTMDB(mContext, serie.credits.cast.get(i).profile_path);
                    }
        } else log.debug("getResult: no actor_photo_path for " + serie.id);

        if (serie.credits != null) {
            if (serie.credits.guest_stars != null)
                for (CastMember guestStar : serie.credits.guest_stars)
                    result.addActorIfAbsent(guestStar.name, guestStar.character);
            if (serie.credits.cast != null)
                for (CastMember actor : serie.credits.cast)
                    result.addActorIfAbsent(actor.name, actor.character);
            if (serie.credits.crew != null)
                for (CrewMember crew : serie.credits.crew) {
                    assert crew.job != null;
                    if (crew.job.equals(PRODUCER))
                        result.addProducerIfAbsent(crew.name);
                    if (crew.job.equals(SCREENPLAY))
                        result.addScreenplayIfAbsent(crew.name);
                    if (crew.job.equals(MUSICCOMPOSER))
                        result.addMusiccomposerIfAbsent(crew.name);
                }
        } else {
            log.warn("getResult: credit is null for showId " + serie.name);
        }

        return result;
    }

    // many genres are not translated on tmdb and localized request is returned in the local language making one to
    // one mapping difficult without changing all db structure --> revert to show trick which is the only way to cope
    // with fallback search in en is perfomed when localized search returns nothing
    private static List<String> getLocalizedGenres(List<Genre> genres) {
        ArrayList<String> localizedGenres = new ArrayList<>();
        for (Genre genre : genres) {
            switch (genre.id) {
                case 10759:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_action_adventure));
                    break;
                case 16:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_animation));
                    break;
                case 35:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_comedy));
                    break;
                case 80:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_crime));
                    break;
                case 99:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_documentary));
                    break;
                case 18:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_drama));
                    break;
                case 10751:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_family));
                    break;
                case 10762:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_kids));
                    break;
                case 9648:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_mystery));
                    break;
                case 10763:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_news));
                    break;
                case 10764:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_reality));
                    break;
                case 10765:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_scifi_fantasy));
                    break;
                case 10766:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_soap));
                    break;
                case 10767:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_talk));
                    break;
                case 10768:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_war_politics));
                    break;
                case 37:
                    localizedGenres.add(mContext.getString(R.string.tvshow_genre_western));
                    break;
                default:
                    log.warn("unknown genre: id=" + genre.id + ", name=" + genre.name);
                    localizedGenres.add(genre.name);
            }
        }
        return localizedGenres;
    }
}
