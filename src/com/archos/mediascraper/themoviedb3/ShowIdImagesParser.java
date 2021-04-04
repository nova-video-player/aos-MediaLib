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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ShowIdImagesParser {

    private static final Logger log = LoggerFactory.getLogger(ShowIdBackdropsParser.class);
    final static String BANNERS_URL = "https://artworks.thetvdb.com/banners/";

    public static ShowIdImagesResult getResult(String showTitle,
                                                  Images fanartsResponse,
                                                  Images globalFanartsResponse,
                                                  String language, Context context) {

        ShowIdImagesResult result = new ShowIdImagesResult();
        // banners
        List<ScraperImage> backdrops = new LinkedList<>();
        List<Pair<Image, String>> tempBackdrops = new ArrayList<>();
        // posters
        List<ScraperImage> posters = new LinkedList<>();
        List<Pair<Image, String>> tempPosters = new ArrayList<>();

        if (fanartsResponse != null) {
            if (!fanartsResponse.backdrops.isEmpty())
                for (Image fanart : fanartsResponse.backdrops)
                    tempBackdrops.add(Pair.create(fanart, language));

            if (!fanartsResponse.posters.isEmpty())
                for (Image fanart : fanartsResponse.posters)
                    tempPosters.add(Pair.create(fanart, language));
        }

        // TODO MARC remove half of images only en? or only en? --> divide by 2 storage need and dupplicates...
        // TODO MARC check if the en and fr images are the same and eliminate --> do it in movies too!
        //Image test;
        //if test.iso_639_1 == "en"

        // TODO MARC MovieIdImagesParser2 far better!!!

        if (!language.equals("en")) {
            if (globalFanartsResponse != null)
                if (!globalFanartsResponse.backdrops.isEmpty())
                    for (Image fanart : globalFanartsResponse.backdrops)
                        tempBackdrops.add(Pair.create(fanart, "en"));

            if (globalFanartsResponse != null)
                if (!globalFanartsResponse.posters.isEmpty())
                    for (Image fanart : globalFanartsResponse.posters)
                        tempBackdrops.add(Pair.create(fanart, "en"));
        }

        Collections.sort(tempBackdrops, new Comparator<Pair<Image, String>>() {
            @Override
            public int compare(Pair<Image, String> b1, Pair<Image, String> b2) {
                return - Double.compare(b1.first.vote_average, b2.first.vote_average);
            }
        });

        for(Pair<Image, String> backdrop : tempBackdrops) {
            log.debug("getResult: generating ScraperImage for backdrop for " + showTitle + ", large=" + BANNERS_URL + backdrop.first.fileName + ", thumb=" + BANNERS_URL + backdrop.first.fileName);
            ScraperImage image = new ScraperImage(ScraperImage.Type.SHOW_BACKDROP, showTitle);
            image.setLanguage(backdrop.second);
            image.setThumbUrl(BANNERS_URL + backdrop.first.thumbnail);
            image.setLargeUrl(BANNERS_URL + backdrop.first.fileName);
            image.generateFileNames(context);
            backdrops.add(image);
        }

        /*
        ScraperImage genericImage = null;
        if(!posters.isEmpty())
            genericImage = posters.get(0);
         */
        result.backdrops = backdrops;
        return result;
    }
}
