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

package com.archos.mediascraper.preprocess;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class SortTitleUtilsTest {

    @Test
    public void testSortTitlesFromResourceFile() throws Exception {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("sort_title_test_cases.csv");
        assertNotNull("Could not find sort_title_test_cases.csv", inputStream);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            int testCount = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\|", -1);
                if (parts.length < 3) {
                    System.err.println("Skipping invalid line " + lineNumber + ": " + line);
                    continue;
                }

                String inputTitle = parts[0];
                String language = "null".equals(parts[1]) ? null : parts[1];
                String expectedSortTitle = parts[2];

                String actual = SortTitleUtils.extractSortTitle(inputTitle, language);
                assertEquals("Mismatch at line " + lineNumber + " for title '" + inputTitle + "' (" + language + ")",
                        expectedSortTitle, actual);
                testCount++;
            }
            System.out.println("Successfully ran " + testCount + " CSV-driven sort title test cases.");
        }
    }

    @Test
    public void testWhitespaceAndEmpty() {
        assertEquals("", SortTitleUtils.extractSortTitle("", "en"));
        assertEquals("", SortTitleUtils.extractSortTitle(null, "en"));
        assertEquals("   ", SortTitleUtils.extractSortTitle("   ", "en"));
        assertEquals("The", SortTitleUtils.extractSortTitle("The", "en"));
        assertEquals("Matrix, The", SortTitleUtils.extractSortTitle("  The Matrix  ", "en"));
    }
}
