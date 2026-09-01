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
import com.archos.mediascraper.ScrapeSearchResult;
import com.archos.mediascraper.preprocess.MovieSearchInfo;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;
import com.archos.mediascraper.xml.MovieScraper3;

import org.junit.After;
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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * Perf test #2: measures how many TMDb search requests {@code MovieScraper3.getMatches2()}
 * actually issues across a real filename corpus, using a long-lived, replayable on-disk cache
 * (see {@link PerfCacheTmdb}) so the same corpus can be re-run many times - including against
 * different versions of the cascade logic (e.g. via `git stash` around MovieScraper3.java) -
 * without hitting the network more than once per distinct query.
 * <p>
 * TV shows are skipped: the short-circuit under test only applies to
 * {@code MovieScraper3.getMatches2()}'s unified-scoring branch.
 * <p>
 * By default runs against the small bundled {@code perf_video_sample.lst} resource (just to
 * validate the harness itself - not representative of real-world query counts). Point the
 * {@code NOVA_PERF_VIDEO_LIST} environment variable at a real, large filename list to get a
 * representative measurement, e.g.:
 * <pre>
 *   NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
 *   NOVA_PERF_CACHE_DIR=/path/to/persistent/cache \
 *     ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfQueryCountTest"
 * </pre>
 * Re-running with the same NOVA_PERF_CACHE_DIR replays cached answers (fast, free, no network),
 * so it is safe to compare two code variants back-to-back. The very first run against a given
 * corpus - or any new query the corpus introduces - does hit the real TMDb API once.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScraperPerfQueryCountTest {

    private static final String STUB_TMDB_API_KEY = "051012651ba326cf5b1e2f482342eaa2";

    private Context context;
    private MovieScraper3 movieScraper;
    private PerfCacheTmdb perfTmdb;

    @Before
    public void setUp() throws Exception {
        context = spy(ApplicationProvider.getApplicationContext());
        doReturn(STUB_TMDB_API_KEY).when(context).getString(R.string.tmdb_api_key);
        FileUtilsQ.getInstance(context);

        movieScraper = new MovieScraper3(context);

        String cacheDirPath = System.getenv("NOVA_PERF_CACHE_DIR");
        File cacheDir = (cacheDirPath != null && !cacheDirPath.isBlank())
                ? new File(cacheDirPath)
                : new File(System.getProperty("java.io.tmpdir"), "nova-tmdb-perf-cache");
        perfTmdb = new PerfCacheTmdb(STUB_TMDB_API_KEY, cacheDir);
        System.out.println("Perf TMDb cache directory: " + cacheDir.getAbsolutePath());
        swapStaticServices(perfTmdb);
    }

    @After
    public void tearDown() throws Exception {
        // Avoid leaking our perf client into any other test class that runs afterward in the
        // same JVM/classloader: next access to MovieScraper3.getTmdb()/getSearchService() will
        // lazily reauth() with the real production client again.
        resetStaticServices();
    }

    @Test
    public void measureQueryCountAcrossCorpus() throws IOException {
        String externalListPath = System.getenv("NOVA_PERF_VIDEO_LIST");

        int totalMovies = 0;
        int totalShows = 0;
        int failures = 0;
        int noResults = 0; // getMatches2() returned a non-null result with an empty results list

        int confidentCount = 0, confidentQueries = 0;
        int eligibleCount = 0, eligibleQueries = 0; // non-confident year, present: useUnifiedScoring branch
        int noYearCount = 0, noYearQueries = 0;

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

                    // Defensive re-swap: a genuine 401 mid-run would make SearchMovie2 call
                    // MovieScraper3.reauth(), which rebuilds the static tmdb/searchService
                    // fields with a real (uncounted, short-TTL) production client. Re-assert our
                    // instance before every movie so such an event can never silently escape
                    // this harness's counters.
                    swapStaticServices(perfTmdb);

                    MovieSearchInfo movieInfo = (MovieSearchInfo) info;
                    int before = perfTmdb.totalRequestCount();
                    ScrapeSearchResult result = movieScraper.getMatches2(info, 1);
                    int queries = perfTmdb.totalRequestCount() - before;
                    if (result == null) failures++;
                    else if (result.results == null || result.results.isEmpty()) noResults++;

                    totalMovies++;
                    if (movieInfo.getYear() == null) {
                        noYearCount++;
                        noYearQueries += queries;
                    } else if (movieInfo.isYearConfident()) {
                        confidentCount++;
                        confidentQueries += queries;
                    } else {
                        eligibleCount++;
                        eligibleQueries += queries;
                    }
                } catch (Throwable t) {
                    failures++;
                }
            }
        }

        int totalQueries = confidentQueries + eligibleQueries + noYearQueries;

        System.out.println(String.format(Locale.ROOT,
                "Query-count source: %s", externalListPath != null ? externalListPath : "bundled perf_video_sample.lst"));
        System.out.println(String.format(Locale.ROOT, "Movies processed: %d (TV shows skipped: %d, failures: %d)", totalMovies, totalShows, failures));
        System.out.println(String.format(Locale.ROOT, "Total TMDb search requests issued: %d (network: %d, cache hits: %d)",
                totalQueries, perfTmdb.networkRequestCount.get(), perfTmdb.cacheHitCount.get()));
        if (totalMovies > 0) {
            System.out.println(String.format(Locale.ROOT, "Average requests/movie overall: %.2f", (double) totalQueries / totalMovies));
            System.out.println(String.format(Locale.ROOT, "Movies with zero results after full cascade: %d (%.1f%%)",
                    noResults, 100.0 * noResults / totalMovies));
        }
        printBucket("confident year \"(YYYY)\"", confidentCount, confidentQueries);
        printBucket("non-confident year (useUnifiedScoring, short-circuit-eligible)", eligibleCount, eligibleQueries);
        printBucket("no year at all", noYearCount, noYearQueries);

        assertTrue("No movies were processed - check NOVA_PERF_VIDEO_LIST / bundled sample resource", totalMovies > 0);
    }

    private void printBucket(String label, int count, int queries) {
        if (count == 0) {
            System.out.println(String.format(Locale.ROOT, "  %s: 0 movies", label));
            return;
        }
        System.out.println(String.format(Locale.ROOT, "  %s: %d movies, %d requests, avg %.2f/movie",
                label, count, queries, (double) queries / count));
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

    private static void swapStaticServices(PerfCacheTmdb perfTmdb) throws Exception {
        setStaticField("tmdb", perfTmdb);
        setStaticField("searchService", perfTmdb.searchService());
    }

    private static void resetStaticServices() throws Exception {
        setStaticField("tmdb", null);
        setStaticField("searchService", null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = MovieScraper3.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
