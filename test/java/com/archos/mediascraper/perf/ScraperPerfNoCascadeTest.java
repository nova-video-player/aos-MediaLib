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
import java.util.Locale;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Perf test #3: "cascade disabled" baseline. For each movie, issues exactly ONE TMDb search
 * request (the same primary candidate {@code MovieScraper3.getMatches2()} would try first: the
 * cleaned name with its parsed year, or the unstripped original name for the "year at start"
 * scenario) via {@link SearchMovie2#search} directly, bypassing all of getMatches2()'s cascade
 * logic entirely (no unified-scoring second pass, no year-less retry, no search-suggestion
 * fallback, no aggressive word-dropping retries).
 * <p>
 * This is the query-count floor (exactly 1 request/movie by construction) and is meant to be
 * compared against {@link ScraperPerfQueryCountTest}'s measured averages to answer "what would
 * we save if cascading were removed entirely, and what would it cost us in coverage (movies for
 * which TMDb returns zero results on the first try alone)?" There is no ground-truth ID in this
 * corpus, so this only measures coverage (found vs. not found), not top-match correctness -
 * see MediaLib/test/resources/scraper_test_cases.csv for correctness testing.
 * <p>
 * Reuses the same replayable long-lived cache as {@link ScraperPerfQueryCountTest} (see
 * {@link PerfCacheTmdb}): since the single query issued here is normally also the cascade's
 * first candidate, most/all of these requests are typically already cached from a prior
 * ScraperPerfQueryCountTest run against the same corpus.
 * <pre>
 *   NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
 *   NOVA_PERF_CACHE_DIR=/path/to/persistent/cache \
 *     ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfNoCascadeTest"
 * </pre>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScraperPerfNoCascadeTest {

    private static final String STUB_TMDB_API_KEY = "051012651ba326cf5b1e2f482342eaa2";

    private Context context;
    private PerfCacheTmdb perfTmdb;

    @Before
    public void setUp() {
        context = spy(ApplicationProvider.getApplicationContext());
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
    public void measureSingleQueryNoCascade() throws IOException {
        String externalListPath = System.getenv("NOVA_PERF_VIDEO_LIST");
        String language = Locale.getDefault().getLanguage();

        int totalMovies = 0;
        int totalShows = 0;
        int failures = 0;
        int notFoundOrEmpty = 0;
        int requestsIssued = 0;

        try (BufferedReader reader = openList(externalListPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                try {
                    Uri uri = Uri.parse(line);
                    SearchInfo info = SearchPreprocessor.instance().parseFileBased(uri, uri);
                    if (info == null || info.isTvShow()) {
                        if (info != null) totalShows++;
                        continue;
                    }

                    MovieSearchInfo movieInfo = (MovieSearchInfo) info;
                    // Mirror MovieScraper3.getMatches2()'s FIRST candidate only - see its
                    // useUnifiedScoring / isYearAtStart branching - then stop, i.e. no cascade.
                    String query;
                    String year;
                    boolean useUnifiedScoring = !movieInfo.isYearConfident() && movieInfo.getYear() != null;
                    if (useUnifiedScoring && movieInfo.isYearAtStart()) {
                        query = movieInfo.getOriginalName();
                        year = null;
                    } else {
                        query = movieInfo.getName();
                        year = movieInfo.getYear();
                    }
                    if (query == null || query.isBlank()) continue;

                    totalMovies++;
                    int before = perfTmdb.totalRequestCount();
                    SearchMovieResult result = SearchMovie2.search(query, language, year, 1, perfTmdb.searchService(), false);
                    requestsIssued += perfTmdb.totalRequestCount() - before;

                    if (result == null || result.status != ScrapeStatus.OKAY || result.result == null || result.result.isEmpty()) {
                        notFoundOrEmpty++;
                    }
                } catch (Throwable t) {
                    failures++;
                }
            }
        }

        System.out.println(String.format(Locale.ROOT,
                "No-cascade source: %s", externalListPath != null ? externalListPath : "bundled perf_video_sample.lst"));
        System.out.println(String.format(Locale.ROOT, "Movies processed: %d (TV shows skipped: %d, failures: %d)", totalMovies, totalShows, failures));
        System.out.println(String.format(Locale.ROOT, "Total TMDb search requests issued: %d (network: %d, cache hits: %d)",
                requestsIssued, perfTmdb.networkRequestCount.get(), perfTmdb.cacheHitCount.get()));
        if (totalMovies > 0) {
            System.out.println(String.format(Locale.ROOT, "Average requests/movie: %.2f (floor by construction)", (double) requestsIssued / totalMovies));
            System.out.println(String.format(Locale.ROOT, "No results on the single try: %d (%.1f%%)",
                    notFoundOrEmpty, 100.0 * notFoundOrEmpty / totalMovies));
        }

        assertTrue("No movies were processed - check NOVA_PERF_VIDEO_LIST / bundled sample resource", totalMovies > 0);
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
