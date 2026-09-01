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

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class MovieScraperFileTest {

    @Test
    public void testMoviesFromResourceFile() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("movie_test_cases.csv");
        assertNotNull("Could not find movie_test_cases.csv", inputStream);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|");
                if (parts.length < 2) {
                    System.err.println("Skipping invalid line " + lineNumber + ": " + line);
                    continue;
                }

                String input = parts[0];
                String expectedName = parts[1];
                String expectedYear = (parts.length > 2 && !"null".equals(parts[2])) ? parts[2] : null;
                String expectedSuggestion = (parts.length > 3) ? parts[3] : (expectedName + (expectedYear != null ? " " + expectedYear : ""));

                System.out.println("Testing input: " + input);

                SearchInfo result = MovieDefaultMatcher.instance().getUserInputMatch(input, Uri.parse("/test/fake"));

                assertTrue("Result should be MovieSearchInfo", result instanceof MovieSearchInfo);
                MovieSearchInfo movieResult = (MovieSearchInfo) result;

                if (!expectedName.equals(movieResult.getName())) {
                    System.out.println("  -> NAME MISMATCH: '" + expectedName + "' != '" + movieResult.getName() + "'");
                }
                assertEquals("Name mismatch for input: " + input, expectedName, movieResult.getName());
                assertEquals("Year mismatch for input: " + input, expectedYear, movieResult.getYear());
                assertEquals("Suggestion mismatch for input: " + input, expectedSuggestion, movieResult.getSearchSuggestion());
                
                System.out.println("  -> PASSED: " + movieResult.getSearchSuggestion());
            }
        }
    }
}
