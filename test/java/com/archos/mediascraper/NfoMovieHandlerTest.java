// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import com.archos.mediascraper.saxhandler.NfoMovieHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NfoMovieHandlerTest {

    @Test
    public void readsTmdbUniqueId() throws Exception {
        MovieTags tags = parse("<movie><title>Film</title>"
                + "<uniqueid type=\"tmdb\" default=\"true\">550</uniqueid></movie>");

        assertEquals(550, tags.getOnlineId());
    }

    @Test
    public void tmdbUniqueIdWinsOverLegacyTmdbId() throws Exception {
        MovieTags tags = parse("<movie><title>Film</title>"
                + "<tmdbid>999</tmdbid><uniqueid type=\"tmdb\">550</uniqueid></movie>");

        assertEquals(550, tags.getOnlineId());
    }

    @Test
    public void imdbUniqueIdWinsOverLegacyIdRegardlessOfOrder() throws Exception {
        // movie <id> carries the imdb id in the legacy format
        MovieTags tags = parse("<movie><title>Film</title>"
                + "<uniqueid type=\"imdb\">tt0137523</uniqueid><id>tt0000001</id></movie>");

        assertEquals("tt0137523", tags.getImdbId());
    }

    private static MovieTags parse(String xml) throws Exception {
        NfoMovieHandler handler = new NfoMovieHandler();
        NfoParser.getNewParser().parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)), handler);
        return handler.getResult(null, Uri.parse("file:///film.mkv"));
    }
}
