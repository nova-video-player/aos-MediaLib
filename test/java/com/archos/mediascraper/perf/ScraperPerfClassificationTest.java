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

import android.net.Uri;

import com.archos.mediascraper.preprocess.MovieSearchInfo;
import com.archos.mediascraper.preprocess.SearchInfo;
import com.archos.mediascraper.preprocess.SearchPreprocessor;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.Assert.assertTrue;

/**
 * Perf test #1: pure filename-classification pass, no network at all. For each line of the
 * input list, runs it through {@link SearchPreprocessor#parseFileBased} and tallies how many
 * movies fall into each year-detection bucket.
 * <p>
 * This answers the question "how much of a real library is even eligible for the
 * getMatches2() unified-scoring short-circuit?" (see MovieScraper3.java / issue #1923 follow-up):
 * only movies whose year is present but NOT "confident" (i.e. not from an explicit "(YYYY)")
 * take the useUnifiedScoring branch that the short-circuit speeds up.
 * <p>
 * By default runs against the small bundled {@code perf_video_sample.lst} resource (just to
 * validate the harness itself). Point the {@code NOVA_PERF_VIDEO_LIST} environment variable at
 * a real, large filename list (one path/filename per line) to get a representative breakdown,
 * e.g.:
 * <pre>
 *   NOVA_PERF_VIDEO_LIST=/path/to/video.lst \
 *     ./gradlew :MediaLib:testDebugUnitTest --tests "com.archos.mediascraper.perf.ScraperPerfClassificationTest"
 * </pre>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ScraperPerfClassificationTest {

    @Test
    public void classifyMovieYearDetection() throws IOException {
        String externalListPath = System.getenv("NOVA_PERF_VIDEO_LIST");

        int totalLines = 0;
        int totalMovies = 0;
        int totalShows = 0;
        int moviesConfidentYear = 0;
        int moviesNonConfidentYearEligible = 0; // year present but not confident: useUnifiedScoring branch
        int moviesNoYear = 0;
        int parseFailures = 0;

        try (BufferedReader reader = openList(externalListPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                totalLines++;

                try {
                    Uri uri = Uri.parse(line);
                    SearchInfo info = SearchPreprocessor.instance().parseFileBased(uri, uri);
                    if (info == null) {
                        parseFailures++;
                        continue;
                    }
                    if (info.isTvShow()) {
                        totalShows++;
                        continue;
                    }
                    totalMovies++;
                    MovieSearchInfo movieInfo = (MovieSearchInfo) info;
                    if (movieInfo.getYear() == null) {
                        moviesNoYear++;
                    } else if (movieInfo.isYearConfident()) {
                        moviesConfidentYear++;
                    } else {
                        moviesNonConfidentYearEligible++;
                    }
                } catch (Throwable t) {
                    parseFailures++;
                }
            }
        }

        System.out.println(String.format(Locale.ROOT,
                "Classification source: %s", externalListPath != null ? externalListPath : "bundled perf_video_sample.lst"));
        System.out.println(String.format(Locale.ROOT, "Total lines processed: %d (parse failures: %d)", totalLines, parseFailures));
        System.out.println(String.format(Locale.ROOT, "Movies: %d, TV shows: %d", totalMovies, totalShows));
        if (totalMovies > 0) {
            System.out.println(String.format(Locale.ROOT,
                    "  confident year \"(YYYY)\" (NOT eligible for short-circuit gain): %d (%.1f%%)",
                    moviesConfidentYear, 100.0 * moviesConfidentYear / totalMovies));
            System.out.println(String.format(Locale.ROOT,
                    "  non-confident year, e.g. scene release (ELIGIBLE for short-circuit gain): %d (%.1f%%)",
                    moviesNonConfidentYearEligible, 100.0 * moviesNonConfidentYearEligible / totalMovies));
            System.out.println(String.format(Locale.ROOT,
                    "  no year at all (NOT eligible, different code path): %d (%.1f%%)",
                    moviesNoYear, 100.0 * moviesNoYear / totalMovies));
        }

        assertTrue("No lines were processed - check NOVA_PERF_VIDEO_LIST / bundled sample resource", totalLines > 0);
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
