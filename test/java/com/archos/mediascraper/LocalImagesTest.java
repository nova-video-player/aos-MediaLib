// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0

package com.archos.mediascraper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

// sdk<=29 keeps canManageExternalStorage()==true so findBackdrop does not relocate
// local file uris to the app public dir, letting LocalStorageFileEditor probe the
// real temp files created here.
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29)
public class LocalImagesTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void findsStaticFanartInVideoFolder() throws Exception {
        File movieDir = temp.newFolder("Movies", "Film");
        File video = new File(movieDir, "film.mkv");
        File fanart = new File(movieDir, "fanart.jpg");
        fanart.createNewFile();

        Uri result = LocalImages.findBackdrop(Uri.fromFile(video), null, false);

        assertEquals(fanart.getAbsolutePath(), result.getPath());
    }

    @Test
    public void findsShowRootBackgroundFromSeasonFolder() throws Exception {
        File showDir = temp.newFolder("Show");
        File seasonDir = new File(showDir, "Season 01");
        seasonDir.mkdirs();
        File episode = new File(seasonDir, "Show.S01E01.mkv");
        File background = new File(showDir, "background.jpg");
        background.createNewFile();

        Uri result = LocalImages.findBackdrop(Uri.fromFile(episode), "Show", true);

        assertEquals(background.getAbsolutePath(), result.getPath());
    }

    @Test
    public void movieDoesNotInheritGrandparentFanart() throws Exception {
        File moviesDir = temp.newFolder("Movies");
        File filmDir = new File(moviesDir, "Film");
        filmDir.mkdirs();
        File video = new File(filmDir, "film.mkv");
        // fanart lives in the shared Movies/ folder, not beside the film
        File grandparentFanart = new File(moviesDir, "fanart.jpg");
        grandparentFanart.createNewFile();

        Uri result = LocalImages.findBackdrop(Uri.fromFile(video), null, false);

        assertNull(result);
    }
}
