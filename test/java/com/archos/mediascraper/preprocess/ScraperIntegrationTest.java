// Copyright 2025 Courville Software
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

package com.archos.mediascraper.preprocess;

import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import android.preference.PreferenceManager;
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.Scraper;
import com.archos.mediascraper.SearchResult;
import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.medialib.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScraperIntegrationTest {

    private Context context;
    private Scraper scraper;

    @Before
    public void setUp() {
        context = spy(ApplicationProvider.getApplicationContext());
        // Stub the API key to avoid resource lookup failure in library test context
        doReturn("051012651ba326cf5b1e2f482342eaa2").when(context).getString(R.string.tmdb_api_key);
        
        // Initialize FileUtilsQ to ensure static fields like publicAppDirectory are set
        FileUtilsQ.getInstance(context);
        
        scraper = new Scraper(context);
    }

    @Test
    public void testScrapingFromCsv() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("scraper_test_cases.csv");
        assertNotNull("Could not find scraper_test_cases.csv", inputStream);

        List<String> errors = new java.util.ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length < 4) {
                    System.err.println("Skipping invalid line " + lineNumber + ": " + line);
                    continue;
                }

                String uriString = parts[0];
                String type = parts[1];
                String expectedTitle = parts[2];
                String expectedIdStr = parts[3];
                int expectedId = Integer.parseInt(expectedIdStr);
                String language = parts.length >= 5 ? parts[4] : "en";

                System.out.println("Testing URI: " + uriString + " (Type: " + type + ", Lang: " + language + ")");

                // Set language in preferences
                PreferenceManager.getDefaultSharedPreferences(context).edit().putString("favScraperLang", language).commit();

                try {
                    Uri uri = Uri.parse(uriString);
                    SearchInfo info = SearchPreprocessor.instance().parseFileBased(uri, uri);

                    if (info == null) {
                        errors.add("SearchInfo is null for " + uriString);
                        continue;
                    }

                    // Print parsed details
                    if (info.isTvShow()) {
                        TvShowSearchInfo tvShowInfo = (TvShowSearchInfo) info;
                        System.out.println(String.format("  -> PREPROCESSED: Name='%s' Year=%s S%02dE%02d Suggestion='%s'", 
                            tvShowInfo.getShowName(), tvShowInfo.getFirstAiredYear(), tvShowInfo.getSeason(), tvShowInfo.getEpisode(), tvShowInfo.getSearchSuggestion()));
                    } else {
                        MovieSearchInfo movieInfo = (MovieSearchInfo) info;
                        System.out.println(String.format("  -> PREPROCESSED: Name='%s' Year=%s Suggestion='%s'", 
                            movieInfo.getName(), movieInfo.getYear(), movieInfo.getSearchSuggestion()));
                    }

                    // Verify type detection
                    if ("Movie".equalsIgnoreCase(type) && info.isTvShow()) {
                         errors.add("Expected Movie but got TvShow for " + uriString);
                    } else if ("Show".equalsIgnoreCase(type) && !info.isTvShow()) {
                         errors.add("Expected TvShow but got Movie for " + uriString);
                    }

                    // Perform Scrape
                    ScrapeSearchResult result = scraper.getBestMatches(info, 1);

                    if (result == null) {
                        errors.add("Scrape result is null for " + uriString);
                        continue;
                    }
                    
                    if (result.results != null && !result.results.isEmpty()) {
                        SearchResult topMatch = result.results.get(0);
                        boolean found = false;
                        int foundPosition = -1;
                        for (SearchResult match : result.results) {
                            foundPosition++;
                            if (match.getId() == expectedId) {
                                found = true;
                                StringBuilder sb = new StringBuilder();
                                sb.append("  -> MATCH FOUND #").append(foundPosition + 1).append(": ").append(match.getTitle());
                                sb.append(" (ID: ").append(match.getId()).append(")");
                                if (match.getReleaseOrFirstAiredDate() != null) {
                                    sb.append(" [Release: ").append(new java.text.SimpleDateFormat("yyyy").format(match.getReleaseOrFirstAiredDate())).append("]");
                                }
                                if (match.isTvShow()) {
                                    int s = match.getOriginSearchSeason();
                                    int e = match.getOriginSearchEpisode();
                                    if (s >= 0 && e >= 0) {
                                        sb.append(String.format(" S%02dE%02d", s, e));
                                    }
                                }
                                System.out.println(sb.toString());
                                if (match.isTvShow()) {
                                    android.os.Bundle options = new android.os.Bundle();
                                    options.putInt(Scraper.ITEM_REQUEST_SEASON, match.getOriginSearchSeason());
                                    options.putInt(Scraper.ITEM_REQUEST_EPISODE, match.getOriginSearchEpisode());
                                    com.archos.mediascraper.ScrapeDetailResult detailResult = scraper.getDetails(match, options);
                                    if (detailResult != null && detailResult.tag != null) {
                                        if (detailResult.tag instanceof com.archos.mediascraper.EpisodeTags) {
                                            com.archos.mediascraper.EpisodeTags epTag = (com.archos.mediascraper.EpisodeTags) detailResult.tag;
                                            System.out.println(String.format("  -> DETAILS: Title='%s' Plot='%s' Picture='%s' Poster='%s'",
                                                epTag.getTitle(), epTag.getPlot(), epTag.getEpisodePicture(), epTag.getDefaultPoster() != null ? epTag.getDefaultPoster().getLargeUrl() : "null"));
                                        }
                                    }
                                }
                                break;
                            }
                        }

                        if (topMatch.getId() != expectedId) {
                            String errorMsg = "TOP ID MISMATCH for " + uriString + ". Expected ID " + expectedId +
                                    ". Top result: " + topMatch.getTitle() + " (" + topMatch.getId() + ")" +
                                    (found ? ". Expected result was at position " + (foundPosition + 1) : ". Expected result was not returned");
                            System.out.println("  -> " + errorMsg);
                            errors.add(errorMsg);
                        }
                    } else {
                        String errorMsg = "No search results found for " + uriString + " (Status: " + result.status + ")";
                        System.err.println("  -> " + errorMsg);
                        errors.add(errorMsg);
                    }
                } catch (Throwable t) {
                    String errorMsg = "Exception processing " + uriString + ": " + t.getMessage();
                    System.err.println("  -> " + errorMsg);
                    t.printStackTrace();
                    errors.add(errorMsg);
                }
            }
        }
        
        if (!errors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Encountered ").append(errors.size()).append(" failures:\n");
            for (String error : errors) {
                sb.append(" - ").append(error).append("\n");
            }
            System.err.println(sb.toString());
            fail(sb.toString());
        }
    }
}
