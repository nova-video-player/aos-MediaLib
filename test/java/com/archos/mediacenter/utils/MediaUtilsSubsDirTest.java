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

package com.archos.mediacenter.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileOutputStream;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MediaUtilsSubsDirTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void availableExternalCacheKeepsExistingSubtitles() throws Exception {
        File external = temporaryFolder.newFolder("external");
        File subtitles = new File(external, "subtitles");
        assertTrue(subtitles.mkdir());
        File subtitle = new File(subtitles, "Castle.en.srt");
        assertTrue(subtitle.createNewFile());
        Context context = contextWithCaches(external, temporaryFolder.newFolder("internal"));

        assertEquals(subtitles, MediaUtils.getSubsDir(context));
        assertEquals(subtitles, MediaUtils.getSubsDir(context));
        assertTrue(subtitle.isFile());
    }

    @Test
    public void missingExternalCacheUsesWritableInternalCache() throws Exception {
        File internal = temporaryFolder.newFolder("internal");
        Context context = contextWithCaches(null, internal);

        File subtitles = MediaUtils.getSubsDir(context);

        assertEquals(new File(internal, "subtitles"), subtitles);
        File subtitle = new File(subtitles, "Castle.en.srt");
        try (FileOutputStream stream = new FileOutputStream(subtitle)) {
            stream.write(1);
        }
        assertEquals(1, subtitle.length());
        assertEquals(subtitles, MediaUtils.getSubsDir(context));
    }

    @Test
    public void fileBlockingExternalSubtitleDirectoryIsPreserved() throws Exception {
        File external = temporaryFolder.newFolder("external");
        File obstruction = new File(external, "subtitles");
        assertTrue(obstruction.createNewFile());
        File internal = temporaryFolder.newFolder("internal");

        File subtitles = MediaUtils.getSubsDir(contextWithCaches(external, internal));

        assertEquals(new File(internal, "subtitles"), subtitles);
        assertTrue(subtitles.isDirectory());
        assertTrue(obstruction.isFile());
    }

    @Test
    public void failedExternalDirectoryCreationUsesInternalCache() throws Exception {
        File unavailableExternal = temporaryFolder.newFile("external");
        File internal = temporaryFolder.newFolder("internal");

        File subtitles = MediaUtils.getSubsDir(contextWithCaches(unavailableExternal, internal));

        assertEquals(new File(internal, "subtitles"), subtitles);
        assertTrue(subtitles.isDirectory());
    }

    private Context contextWithCaches(File external, File internal) {
        return new ContextWrapper(ApplicationProvider.getApplicationContext()) {
            @Override
            public File getExternalCacheDir() {
                return external;
            }

            @Override
            public File getCacheDir() {
                return internal;
            }
        };
    }
}
