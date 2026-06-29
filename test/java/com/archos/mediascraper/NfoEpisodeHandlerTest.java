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

import com.archos.mediascraper.saxhandler.NfoEpisodeHandler;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class NfoEpisodeHandlerTest {

    @Test
    public void importsRuntimeInMinutes() throws Exception {
        EpisodeTags tags = parse("<episodedetails><title>Pilot</title>"
                + "<runtime>47</runtime></episodedetails>");

        assertEquals(47, tags.getRuntime(TimeUnit.MINUTES));
    }

    @Test
    public void stillImportsNestedDurationInSeconds() throws Exception {
        EpisodeTags tags = parse("<episodedetails><title>Pilot</title><fileinfo>"
                + "<streamdetails><video><durationinseconds>2820</durationinseconds>"
                + "</video></streamdetails></fileinfo></episodedetails>");

        assertEquals(47, tags.getRuntime(TimeUnit.MINUTES));
    }

    private static EpisodeTags parse(String xml) throws Exception {
        NfoEpisodeHandler handler = new NfoEpisodeHandler();
        NfoParser.getNewParser().parse(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)), handler);
        return handler.getResult(null, Uri.parse("file:///video.mkv"));
    }
}
