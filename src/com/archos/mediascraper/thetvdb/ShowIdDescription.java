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

import com.archos.mediascraper.ShowTags;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.Series;
import com.uwetrottmann.thetvdb.entities.SeriesResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import retrofit2.Response;

// set overview for showId in english
// return boolean for success
public class ShowIdDescription {
    private static final Logger log = LoggerFactory.getLogger(ShowIdDescription.class);

    public static boolean addDescription(int showId, ShowTags tag, MyTheTVdb theTvdb) {
        if (tag == null) return false;
        Response<SeriesResponse> seriesResponse = null;
        try {
            seriesResponse = theTvdb.series().series(showId, "en").execute();
            switch (seriesResponse.code()) {
                case 401: // auth issue
                    log.debug("search: auth error");
                    ShowScraper3.reauth();
                    return false;
                case 404: // not found
                    log.debug("getBaseInfo: movieId " + showId + " not found");
                    return false;
                default:
                    if (seriesResponse.isSuccessful()) {
                        if (seriesResponse.body() != null) {
                            Series series = seriesResponse.body().data;
                            if (series.overview != null) tag.setPlot(series.overview);
                            else {
                                log.debug("addDescription: overview is null for showId=" + showId + ", series.id=" + series.id + ", serie.imdbId=" + series.imdbId);
                                tag.setPlot("");
                            }
                            if (series.seriesName != null) tag.setTitle(series.seriesName);
                            else {
                                log.debug("addDescription: seriesName is null for showId=" + showId + ", series.id=" + series.id + ", serie.imdbId=" + series.imdbId);
                                tag.setTitle("");
                            }
                            return true;
                        } else
                            return false;
                    } else // an error at this point is PARSER related
                        return false;
            }
        } catch (IOException e) {
            log.error("addImages: caught IOException getting summary for showId=" + showId);
            return false;
        }
    }
}
