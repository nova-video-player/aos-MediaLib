// Copyright 2021 Courville Software
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
import android.util.Pair;
import com.archos.mediascraper.ScraperImage;
import com.uwetrottmann.tmdb2.entities.Image;
import com.uwetrottmann.tmdb2.entities.Images;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ShowIdImagesParser {

    private static final Logger log = LoggerFactory.getLogger(ShowIdImagesParser.class);
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

    public static ShowIdImagesResult getResult(String showTitle, TvShow tvShow, String language, Context context) {

        Images images = tvShow.images;

        ShowIdImagesResult result = new ShowIdImagesResult();

        // posters
        List<ScraperImage> posters = new ArrayList<>();
        List<Pair<Image, String>> tempPosters = new ArrayList<>();

        // backdrops
        List<ScraperImage> backdrops = new ArrayList<>();
        List<Pair<Image, String>> tempBackdrops = new ArrayList<>();

        // clearlogos
        List<ScraperImage> clearlogos = new ArrayList<>();
        List<String> tempClearLogos = new ArrayList<>();

        log.debug("getResult: global " + showTitle + " poster " + tvShow.poster_path + ", backdrop " + tvShow.backdrop_path);

        posters.add(genPoster(showTitle, tvShow.poster_path, language, true, context));
        backdrops.add(genBackdrop(showTitle, tvShow.backdrop_path, language, context));


        int i = 0;
        for (TvSeason season : tvShow.seasons) {
            i += 1;
            if (season != null) {
                log.debug("getResult: " + showTitle + " s" + i + " poster " + season.poster_path);
                if (season.poster_path != "null") posters.add(genPoster(showTitle, tvShow.poster_path, language, false, context));
            }
        }

        if (images.posters != null)
            for (Image poster : images.posters)
                tempPosters.add(Pair.create(poster, poster.iso_639_1));

        if (images.backdrops != null)
            for (Image backdrop : images.backdrops)
                tempBackdrops.add(Pair.create(backdrop, backdrop.iso_639_1));

        //set series clearlogos
        String apikey = "ac6ed0ad315f924847ff24fa4f555571";
        String url = "http://webservice.fanart.tv/v3/tv/" + tvShow.external_ids.tvdb_id + "?api_key=" + apikey;
        try {
            JSONObject json = new JSONObject(readUrl(url));
            JSONArray resultsff = json.getJSONArray("hdtvlogo");
            for(int j = 0; j < resultsff.length(); j++){
                JSONObject movieObject = resultsff.getJSONObject(j);
                tempClearLogos.add(movieObject.getString("url"));
                clearlogos.add(genClearLogo(showTitle, movieObject.getString("url"),  context));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(tempPosters, new Comparator<Pair<Image, String>>() {
            @Override
            public int compare(Pair<Image, String> b1, Pair<Image, String> b2) {
                return - Double.compare(b1.first.vote_average, b2.first.vote_average);
            }
        });

        Collections.sort(tempBackdrops, new Comparator<Pair<Image, String>>() {
            @Override
            public int compare(Pair<Image, String> b1, Pair<Image, String> b2) {
                return - Double.compare(b1.first.vote_average, b2.first.vote_average);
            }
        });

        for(Pair<Image, String> poster : tempPosters) {
            log.debug("getResult: generating ScraperImage for poster for " + showTitle + ", large=" + ScraperImage.TMPL + poster.first.file_path);
            posters.add(genPoster(showTitle, poster.first.file_path, poster.second, true, context));
        }

        for(Pair<Image, String> backdrop : tempBackdrops) {
            log.debug("getResult: generating ScraperImage for backdrop for " + showTitle + ", large=" + ScraperImage.TMPL + backdrop.first.file_path);
            posters.add(genBackdrop(showTitle, backdrop.first.file_path, backdrop.second, context));
        }

        result.posters = posters;
        result.backdrops = backdrops;
        result.clearlogos = clearlogos;
        return result;
    }

    public static ScraperImage genPoster(String showTitle, String path, String lang, Boolean isShowPoster, Context context) {
        ScraperImage image = new ScraperImage(isShowPoster ? ScraperImage.Type.SHOW_POSTER : ScraperImage.Type.EPISODE_POSTER, showTitle);
        image.setLanguage(lang);
        image.setLargeUrl(ScraperImage.TMPL + path);
        image.setThumbUrl(ScraperImage.TMPT + path);
        image.generateFileNames(context);
        log.debug("genPoster: " + showTitle + ", has poster " + image.getLargeUrl() + " path " + image.getLargeFile());
        return image;
    }

    public static ScraperImage genBackdrop(String showTitle, String path, String lang, Context context) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, showTitle);
        image.setLanguage(lang);
        image.setLargeUrl(ScraperImage.TMBL + path);
        image.setThumbUrl(ScraperImage.TMBT + path);
        image.generateFileNames(context);
        log.debug("genBackdrop: " + showTitle + ", has backdrop " + image.getLargeUrl() + " path " + image.getLargeFile());
        return image;
    }

    public static ScraperImage genClearLogo(String showTitle, String path, Context context) {
        ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_TITLE_CLEARLOGO, showTitle);
        image.setLargeUrl(path);
        image.setThumbUrl(path);
        image.generateFileNames(context);
        return image;
    }
}
