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

import com.archos.mediascraper.saxhandler.NfoShowHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NfoShowHandlerTest {

    @Test
    public void fallsBackToBareYearWhenPremieredAbsent() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title><year>1989</year></tvshow>");

        assertEquals(1989, tags.getPremieredYear());
    }

    @Test
    public void premieredWinsOverYear() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<premiered>1989-12-17</premiered><year>1990</year></tvshow>");

        assertEquals(1989, tags.getPremieredYear());
    }

    @Test
    public void fallsBackToYearWhenPremieredIsInvalid() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<premiered>not-a-date</premiered><year>1989</year></tvshow>");

        assertEquals(1989, tags.getPremieredYear());
    }

    @Test
    public void premieredWinsOverYearRegardlessOfOrder() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<year>1990</year><premiered>1989-12-17</premiered></tvshow>");

        assertEquals(1989, tags.getPremieredYear());
    }

    @Test
    public void readsTmdbUniqueId() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<uniqueid type=\"tmdb\" default=\"true\">1423</uniqueid></tvshow>");

        assertEquals(1423, tags.getOnlineId());
    }

    @Test
    public void tmdbUniqueIdWinsOverLegacyId() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<id>999</id><uniqueid type=\"tmdb\">1423</uniqueid></tvshow>");

        assertEquals(1423, tags.getOnlineId());
    }

    @Test
    public void tmdbUniqueIdWinsOverLegacyIdRegardlessOfOrder() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<uniqueid type=\"tmdb\">1423</uniqueid><id>999</id></tvshow>");

        assertEquals(1423, tags.getOnlineId());
    }

    @Test
    public void imdbUniqueIdSetsImdbId() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<uniqueid type=\"imdb\">tt0103584</uniqueid></tvshow>");

        assertEquals("tt0103584", tags.getImdbId());
    }

    @Test
    public void unknownUniqueIdTypeIsIgnored() throws Exception {
        ShowTags tags = parse("<tvshow><title>Show</title>"
                + "<id>999</id><uniqueid type=\"tvdb\">555</uniqueid></tvshow>");

        assertEquals(999, tags.getOnlineId());
    }

    private static ShowTags parse(String xml) throws Exception {
        NfoShowHandler handler = new NfoShowHandler();
        NfoParser.getNewParser().parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)), handler);
        return handler.getResult(null, Uri.parse("file:///tvshow.nfo"));
    }
}
