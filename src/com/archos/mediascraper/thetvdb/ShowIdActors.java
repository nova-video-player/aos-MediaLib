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

import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.xml.ShowScraper3;
import com.uwetrottmann.thetvdb.entities.ActorsResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;

import retrofit2.Response;

// Get the actors for specific show id
public class ShowIdActors {
    private static final Logger log = LoggerFactory.getLogger(ShowIdActors.class);

    public static ShowIdActorsResult getActors(int showId, MyTheTVdb theTvdb) {
        ShowIdActorsResult myResult = new ShowIdActorsResult();
        Map<String, String> parserResult;

        log.debug("getActors: quering thetvdb for showId " + showId);
        try {
            log.debug("ShowIdActorsResult: no boost for " + showId);
            Response<ActorsResponse> actorsResponse = theTvdb.series().actors(showId).execute();
            switch (actorsResponse.code()) {
                case 401: // auth issue
                    log.debug("search: auth error");
                    myResult.status = ScrapeStatus.AUTH_ERROR;
                    myResult.actors = ShowIdActorsResult.EMPTY_MAP;
                    ShowScraper3.reauth();
                    return myResult;
                case 404: // not found
                    myResult.status = ScrapeStatus.NOT_FOUND;
                    myResult.actors = ShowIdActorsResult.EMPTY_MAP;
                    break;
                default:
                    if (actorsResponse.isSuccessful()) {
                        if (actorsResponse.body() != null) {
                            parserResult = ShowIdActorsParser.getResult(actorsResponse.body());
                            myResult.actors = parserResult;
                            myResult.status = ScrapeStatus.OKAY;
                        } else {
                            myResult.actors = ShowIdActorsResult.EMPTY_MAP;
                            myResult.status = ScrapeStatus.NOT_FOUND;
                        }
                    } else { // an error at this point is PARSER related
                        log.debug("getActors: error " + actorsResponse.code());
                        myResult.actors = ShowIdActorsResult.EMPTY_MAP;
                        myResult.status = ScrapeStatus.ERROR_PARSER;
                    }
                    break;
            }
        } catch (IOException e) {
            log.debug("getActors: caught IOException getting actors for showId=" + showId);
            myResult.actors = ShowIdActorsResult.EMPTY_MAP;
            myResult.status = ScrapeStatus.ERROR_PARSER;
            myResult.reason = e;
        }
        return myResult;
    }
}
