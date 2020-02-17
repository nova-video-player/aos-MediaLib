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
import android.util.Log;

import com.archos.medialib.R;
import com.archos.mediascraper.ShowTags;
import com.uwetrottmann.thetvdb.entities.Series;
import com.uwetrottmann.thetvdb.entities.SeriesResponse;

import java.util.ArrayList;
import java.util.List;

public class ShowIdParser {

    private static final String TAG = ShowIdParser.class.getSimpleName();
    private static final boolean DBG = false;

    private static Context mContext;

    public static ShowTags getResult(SeriesResponse seriesResponse, Context context) {
        mContext = context;
        ShowTags result = new ShowTags();
        Series series = seriesResponse.data;

        result.setPlot(series.overview);
        result.setRating(series.siteRating.floatValue());
        result.setTitle(series.seriesName);
        result.setContentRating(series.rating);
        result.setImdbId(series.imdbId);
        result.setOnlineId(series.id);
        result.setGenres(getLocalizedGenres(series.genre));
        result.addStudioIfAbsent(series.network, '|', ',');
        result.setPremiered(series.firstAired);

        if (DBG) Log.d(TAG, "getResult: found title=" + series.seriesName + ", genre " + series.genre);

        return result;
    }


    private static List<String> getLocalizedGenres(List<String> genres) {
        ArrayList<String> localizedGenres = new ArrayList<>();

        for (String genre : genres) {
            switch (genre) {
                case "Action":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_action));
                    break;
                case "Adventure":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_adventure));
                    break;
                case "Animation":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_animation));
                    break;
                case "Anime":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_anime));
                    break;
                case "Children":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_children));
                    break;
                case "Comedy":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_comedy));
                    break;
                case "Crime":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_crime));
                    break;
                case "Documentary":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_documentary));
                    break;
                case "Drama":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_drama));
                    break;
                case "Family":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_family));
                    break;
                case "Fantasy":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_fantasy));
                    break;
                case "Food":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_food));
                    break;
                case "Game Show":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_game_show));
                    break;
                case "Home and Garden":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_home_garden));
                    break;
                case "Horror":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_horror));
                    break;
                case "Mini-Series":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_mini_series));
                    break;
                case "News":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_news));
                    break;
                case "Reality":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_reality));
                    break;
                case "Romance":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_romance));
                    break;
                case "Science-Fiction":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_science_fiction));
                    break;
                case "Soap":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_soap));
                    break;
                case "Special Interest":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_special_interest));
                    break;
                case "Sport":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_sport));
                    break;
                case "Suspense":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_suspense));
                    break;
                case "Talk Show":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_talk_show));
                    break;
                case "Thriller":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_thriller));
                    break;
                case "Travel":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_travel));
                    break;
                case "Western":
                    localizedGenres.add(mContext.getString(R.string.tv_show_genre_western));
                    break;
                default:
                    localizedGenres.add(genre);
            }
        }
        return localizedGenres;
    }
}
