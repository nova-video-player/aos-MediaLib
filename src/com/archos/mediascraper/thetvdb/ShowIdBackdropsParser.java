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
import android.util.Pair;
import com.archos.mediascraper.ScraperImage;
import com.uwetrottmann.thetvdb.entities.SeriesImageQueryResult;
import com.uwetrottmann.thetvdb.entities.SeriesImageQueryResultResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class ShowIdBackdropsParser {

    private static final Logger log = LoggerFactory.getLogger(ShowIdBackdropsParser.class);
    final static String BANNERS_URL = "https://www.thetvdb.com/banners/";

    public static ShowIdBackdropsResult getResult(String showTitle,
                                                SeriesImageQueryResultResponse fanartsResponse,
                                                SeriesImageQueryResultResponse globalFanartsResponse,
                                                String language, Context context) {


        ShowIdBackdropsResult result = new ShowIdBackdropsResult();
        // banners
        List<ScraperImage> backdrops = new LinkedList<>();
        List<Pair<SeriesImageQueryResult, String>> tempBackdrops = new ArrayList<>();

        if (fanartsResponse != null)
            if (! fanartsResponse.data.isEmpty())
                for(SeriesImageQueryResult fanart : fanartsResponse.data)
                    tempBackdrops.add(Pair.create(fanart, language));

        if (!language.equals("en"))
            if (globalFanartsResponse != null)
                if (! globalFanartsResponse.data.isEmpty())
                    for(SeriesImageQueryResult fanart : globalFanartsResponse.data)
                        tempBackdrops.add(Pair.create(fanart, "en"));

        Collections.sort(tempBackdrops, new Comparator<Pair<SeriesImageQueryResult, String>>() {
            @Override
            public int compare(Pair<SeriesImageQueryResult, String> b1, Pair<SeriesImageQueryResult, String> b2) {
                return - Double.compare(b1.first.ratingsInfo.average, b2.first.ratingsInfo.average);
            }
        });
        for(Pair<SeriesImageQueryResult, String> backdrop : tempBackdrops) {
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
