package com.archos.mediacenter.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class TrackNamingTest {

    @Before
    public void setUp() {
        // Set locale to French for the test logic if needed
        Locale.setDefault(Locale.FRENCH);
    }

    @Test
    public void testTrackNaming() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("track_naming_tests.csv");
        if (is == null) throw new RuntimeException("Could not find track_naming_tests.csv");
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        boolean firstLine = true;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            if (firstLine) {
                firstLine = false;
                continue;
            }
            // Basic CSV parsing (handles quotes)
            String[] parts = parseCsvLine(line);
            if (parts.length < 6) continue;

            String title = parts[0].isEmpty() ? null : parts[0];
            String lang = parts[1];
            String format = parts[2];
            int disposition = Integer.parseInt(parts[3]);
            boolean titleFirst = Boolean.parseBoolean(parts[4]);
            String expected = parts[5];

            // Manual mapping of labels for MediaLib test (no Context/Resources available here easily without mocking)
            // But we want to test the CORE logic in ISO639codes.java
            String dispLabel = getMockDispLabel(disposition, titleFirst);
            String unknownTrackName = "Unknown";
            String langName = ISO639codes.getLanguageNameForLetterCode(lang);

            String actual = ISO639codes.generateTrackName(title, lang, langName, format, disposition, titleFirst, dispLabel, unknownTrackName);
            
            System.out.println("Line " + lineNum + ": Input(title=" + title + ", lang=" + lang + ", disp=" + disposition + ") -> Actual: " + actual + " | Expected: " + expected);
            assertEquals("Failure at line " + lineNum + ": " + line, expected, actual);
        }
        reader.close();
    }

    private String getMockDispLabel(int disposition, boolean isAudio) {
        // Match the labels used in the CSV expected results (French names from prompt)
        if ((disposition & 0x0080) != 0) return "Malentendants"; // Hearing Impaired
        if ((disposition & 0x0004) != 0) return "original";
        if ((disposition & 0x0040) != 0) return "forced";
        if ((disposition & 0x0002) != 0) return isAudio ? "dubbed" : "traduit";
        if ((disposition & 0x0100) != 0) return isAudio ? "audio description" : "visual impaired";
        if ((disposition & 0x0008) != 0) return "commentary";
        return "";
    }

    private String[] parseCsvLine(String line) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                parts.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        parts.add(sb.toString());
        return parts.toArray(new String[0]);
    }
}
