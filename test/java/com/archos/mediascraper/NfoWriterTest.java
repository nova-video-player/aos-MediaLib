// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0

package com.archos.mediascraper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.util.Xml;

import androidx.test.core.app.ApplicationProvider;

import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.FileUtilsQ;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NfoWriterTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        FileUtilsQ.getInstance(context);
    }

    @Test
    public void movieWithoutCollectionOmitsSet() throws Exception {
        MovieTags tags = new MovieTags();
        tags.setTitle("Standalone");

        assertFalse(writeMovie(tags).contains("<set"));
    }

    @Test
    public void movieWithCollectionWritesSet() throws Exception {
        MovieTags tags = new MovieTags();
        tags.setTitle("Part One");
        tags.setCollectionId(42);
        tags.setCollectionName("A Collection");

        String xml = writeMovie(tags);
        assertTrue(xml.contains("<set>"));
        assertTrue(xml.contains("<id>42</id>"));
        assertTrue(xml.contains("<name>A Collection</name>"));
    }

    @Test
    public void episodeWritesRuntimeInMinutes() throws Exception {
        ShowTags showTags = new ShowTags();
        showTags.setTitle("Example Show");
        EpisodeTags tags = new EpisodeTags();
        tags.setShowTags(showTags);
        tags.setTitle("Pilot");
        tags.setSeason(1);
        tags.setEpisode(1);
        tags.setRuntime(47, TimeUnit.MINUTES);

        StringWriter output = new StringWriter();
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(output);
        NfoWriter.writeXmlInner(serializer, tags);
        serializer.flush();

        assertTrue(output.toString().contains("<runtime>47</runtime>"));
    }

    @Test
    public void awaitPendingExportsIsAFifoBarrier() {
        AtomicInteger state = new AtomicInteger();
        NfoWriter.enqueueExportTask(() -> state.compareAndSet(0, 1));
        NfoWriter.enqueueExportTask(() -> state.compareAndSet(1, 2));

        NfoWriter.awaitPendingExports();

        assertEquals(2, state.get());
    }

    @Test
    public void failedShowWriteIsNotDeduplicatedAndCanRetry() throws Exception {
        File parent = temporaryFolder.newFile("not-a-directory");
        File video = new File(parent, "episode.mkv");
        ShowTags tags = new ShowTags();
        tags.setTitle("Example Show");
        NfoWriter.ExportContext exportContext = new NfoWriter.ExportContext();
        File target = new File(parent, "Example Show" + NfoParser.CUSTOM_SHOW_NFO_EXTENSION);
        Uri exportTarget = FileUtils.relocateNfoAppPublicDirForNfoJpgFiles(Uri.withAppendedPath(
                FileUtils.getParentUrl(Uri.fromFile(video)),
                StringUtils.fileSystemEncode(tags.getTitle()) + NfoParser.CUSTOM_SHOW_NFO_EXTENSION));
        String exportKey = exportTarget.toString();

        NfoWriter.exportInternal(Uri.fromFile(video), tags, exportContext);
        assertFalse(exportContext.contains(exportKey));

        assertTrue(parent.delete());
        assertTrue(parent.mkdir());
        NfoWriter.exportInternal(Uri.fromFile(video), tags, exportContext);

        assertTrue(target.isFile());
        assertTrue(exportContext.contains(exportKey));
    }

    private static String writeMovie(MovieTags tags) throws Exception {
        StringWriter output = new StringWriter();
        XmlSerializer serializer = Xml.newSerializer();
        serializer.setOutput(output);
        NfoWriter.writeXmlInner(serializer, tags);
        serializer.flush();
        return output.toString();
    }
}
