// Copyright 2026 Courville Software
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

package com.archos.mediascraper.perf;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.archos.filecorelibrary.FileUtilsQ;
import com.archos.medialib.R;
import com.archos.mediascraper.ScrapeStatus;
import com.archos.mediascraper.SearchResult;
import com.archos.mediascraper.preprocess.MovieSearchInfo;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.themoviedb3.SearchMovie2;
import com.archos.mediascraper.themoviedb3.SearchMovieResult;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Diagnostic (exploratory, not wired into the doc/findings) test answering: "would swapping the
 * order of the two unified-scoring candidate queries increase the short-circuit fix's hit rate?"
 * <p>
 * For every eligible-bucket movie (non-confident year, useUnifiedScoring branch) this issues BOTH
 * candidate queries unconditionally (both are normally already cached from prior
 * {@link ScraperPerfQueryCountTest} runs, so this costs no extra network calls) and independently
 * checks each one against {@code countExactTitleMatches() == 1} (mirrors
 * {@code MovieScraper3}'s short-circuit condition). It then tallies, per scenario
 * (year-at-start vs. year-at-end/middle), how often only the *current* first candidate hits vs.
 * how often only the *other* (currently second) candidate would have hit - the latter is exactly
 * the number of additional movies that would gain a 1-query short-circuit if the order were
 * swapped, at the cost of losing the short-circuit on the "only current hits" movies instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScraperPerfCascadeOrderTest {

    private static final String STUB_TMDB_API_KEY = "051012651ba326cf5b1e2f482342eaa2";

    private PerfCacheTmdb perfTmdb;

    @Before
    public void setUp() {
        Context context = spy(ApplicationProvider.getApplicationContext());
        doReturn(STUB_TMDB_API_KEY).when(context).getString(R.string.tmdb_api_key);
        FileUtilsQ.getInstance(context);

        String cacheDirPath = System.getenv("NOVA_PERF_CACHE_DIR");
        File cacheDir = (cacheDirPath != null && !cacheDirPath.isBlank())
                ? new File(cacheDirPath)
                : new File(System.getProperty("java.io.tmpdir"), "nova-tmdb-perf-cache");
        perfTmdb = new PerfCacheTmdb(STUB_TMDB_API_KEY, cacheDir);
        System.out.println("Perf TMDb cache directory: " + cacheDir.getAbsolutePath());
    }

    @Test
    public void measureOrderSwapPotential() throws IOException {
        String externalListPath = System.getenv("NOVA_PERF_VIDEO_LIST");
        String language = Locale.getDefault().getLanguage();

        int[] eligibleTotal = {0, 0}; // [yearAtStart, yearAtEndOrMiddle]
        int[] currentOnlyHit = {0, 0};
        int[] swappedOnlyHit = {0, 0};
        int[] bothHit = {0, 0};
        int[] neitherHit = {0, 0};
        int totalLines = 0, totalMovies = 0, exceptions = 0, blankSkipped = 0;

        try (BufferedReader reader = openList(externalListPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                totalLines++;

                try {
                    Uri uri = Uri.parse(line);
                    SearchInfo info = SearchPreprocessor.instance().parseFileBased(uri, uri);
                    if (info == null || info.isTvShow()) continue;
                    totalMovies++;

                    MovieSearchInfo movieInfo = (MovieSearchInfo) info;
                    boolean eligible = !movieInfo.isYearConfident() && movieInfo.getYear() != null;
                    if (!eligible) continue;

                    boolean yearAtStart = movieInfo.isYearAtStart();
                    int scenario = yearAtStart ? 0 : 1;

                    String currentQuery, currentYear, otherQuery, otherYear;
                    if (yearAtStart) {
                        // Scenario A current order: original name (no year) first, then cleaned name + year
                        currentQuery = movieInfo.getOriginalName(); currentYear = null;
                        otherQuery = movieInfo.getName(); otherYear = movieInfo.getYear();
                    } else {
                        // Scenario B current order: cleaned name + year first, then original name (no year)
                        currentQuery = movieInfo.getName(); currentYear = movieInfo.getYear();
                        otherQuery = movieInfo.getOriginalName(); otherYear = null;
                    }
                    if (currentQuery == null || currentQuery.isBlank() || otherQuery == null || otherQuery.isBlank()) {
                        blankSkipped++;
                        continue;
                    }

                    eligibleTotal[scenario]++;

                    boolean currentHits = singleExactMatch(currentQuery, currentYear, language);
                    boolean otherHits = singleExactMatch(otherQuery, otherYear, language);

                    if (currentHits && otherHits) bothHit[scenario]++;
                    else if (currentHits) currentOnlyHit[scenario]++;
                    else if (otherHits) swappedOnlyHit[scenario]++;
                    else neitherHit[scenario]++;
                } catch (Throwable t) {
                    exceptions++;
                    System.out.println("EXCEPTION on line '" + line + "': " + t);
                }
            }
        }

        System.out.println(String.format(Locale.ROOT,
                "Diagnostics: lines=%d movies=%d blankSkipped=%d exceptions=%d network=%d cacheHits=%d",
                totalLines, totalMovies, blankSkipped, exceptions, perfTmdb.networkRequestCount.get(), perfTmdb.cacheHitCount.get()));

        String[] labels = {"Scenario A (year at start)", "Scenario B (year at end/middle)"};
        for (int s = 0; s < 2; s++) {
            System.out.println(String.format(Locale.ROOT, "%s: %d eligible movies", labels[s], eligibleTotal[s]));
            if (eligibleTotal[s] == 0) continue;
            System.out.println(String.format(Locale.ROOT, "  both candidates single-exact-match (order-independent): %d", bothHit[s]));
            System.out.println(String.format(Locale.ROOT, "  only CURRENT first candidate hits (today's short-circuit): %d", currentOnlyHit[s]));
            System.out.println(String.format(Locale.ROOT, "  only OTHER candidate would hit (swap would gain): %d", swappedOnlyHit[s]));
            System.out.println(String.format(Locale.ROOT, "  neither hits: %d", neitherHit[s]));
            int net = swappedOnlyHit[s] - currentOnlyHit[s];
            System.out.println(String.format(Locale.ROOT, "  net movies gained by swapping order: %+d", net));
        }

        assertTrue("No eligible movies processed - check NOVA_PERF_VIDEO_LIST", eligibleTotal[0] + eligibleTotal[1] > 0);
    }

    private boolean singleExactMatch(String query, String year, String language) {
        SearchMovieResult result = SearchMovie2.search(query, language, year, 1, perfTmdb.searchService(), false);
        if (result == null || result.status != ScrapeStatus.OKAY || result.result == null || result.result.isEmpty()) return false;
        return countExactTitleMatches(result.result, query) == 1;
    }

    private int countExactTitleMatches(List<SearchResult> results, String query) {
        String normalizedQuery = normalize(query);
        Set<Integer> ids = new HashSet<>();
        for (SearchResult result : results) {
            if (normalizedQuery.equals(normalize(result.getTitle())) || normalizedQuery.equals(normalize(result.getOriginalTitle()))) {
                ids.add(result.getId());
            }
        }
        return ids.size();
    }

    private String normalize(String title) {
        if (title == null) return "";
        return title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "");
    }

    private BufferedReader openList(String externalListPath) throws IOException {
        if (externalListPath != null && !externalListPath.isBlank()) {
            return Files.newBufferedReader(Paths.get(externalListPath), StandardCharsets.UTF_8);
        }
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("perf_video_sample.lst");
        if (inputStream == null) {
            throw new IOException("Could not find bundled perf_video_sample.lst resource");
        }
        return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    }
}
