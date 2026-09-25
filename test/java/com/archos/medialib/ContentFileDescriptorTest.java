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

package com.archos.medialib;

import static org.junit.Assert.*;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = ContentFileDescriptorTest.FileStat.class)
public class ContentFileDescriptorTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private static final Uri URI = Uri.parse("content://buffer-test/movie");

    // Robolectric's legacy fstat reports a zero size; provide explicit provider metadata.
    @Implements(Os.class)
    public static class FileStat {
        static int mode = OsConstants.S_IFREG;
        @Implementation protected static StructStat fstat(java.io.FileDescriptor fd) {
            return new StructStat(0, 0, mode, 1, 0, 0, 0, 32, 0, 0, 0, 4096, 1);
        }
    }

    private AssetFileDescriptor open(long start, long length) throws Exception {
        FileStat.mode = OsConstants.S_IFREG;
        File file = temp.newFile();
        Files.write(file.toPath(), new byte[32]);
        AssetFileDescriptor asset = new AssetFileDescriptor(
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY), start, length);
        ContentProvider provider = new ContentProvider() {
            @Override public boolean onCreate() { return true; }
            @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode) { return asset; }
            @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String order) { return null; }
            @Override public String getType(Uri uri) { return null; }
            @Override public Uri insert(Uri uri, ContentValues values) { return null; }
            @Override public int delete(Uri uri, String s, String[] a) { return 0; }
            @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
        };
        ProviderInfo info = new ProviderInfo();
        info.authority = "buffer-test";
        provider.attachInfo(ApplicationProvider.getApplicationContext(), info);
        ShadowContentResolver.registerProviderInternal("buffer-test", provider);
        return asset;
    }

    private AssetFileDescriptor select() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        return ContentFileDescriptor.open(context.getContentResolver(), URI);
    }

    @Test public void acceptsRegularSliceWithoutLosingBounds() throws Exception {
        AssetFileDescriptor asset = open(4, 20);
        try (AssetFileDescriptor selected = select()) {
            assertNotNull(selected);
            assertTrue(selected.getFileDescriptor().valid());
            assertEquals(4, selected.getStartOffset());
            assertEquals(20, selected.getDeclaredLength());
        }
        assertFalse(asset.getFileDescriptor().valid());
    }

    @Test public void acceptsUnknownLengthRegularFile() throws Exception {
        AssetFileDescriptor asset = open(0, AssetFileDescriptor.UNKNOWN_LENGTH);
        try (AssetFileDescriptor selected = select()) {
            assertNotNull(selected);
            assertEquals(AssetFileDescriptor.UNKNOWN_LENGTH, selected.getDeclaredLength());
            assertEquals(0, selected.getStartOffset());
        }
    }

    @Test public void invalidSliceIsClosedAndFallsBack() throws Exception {
        AssetFileDescriptor asset = open(20, 20);
        assertNull(select());
        assertFalse(asset.getFileDescriptor().valid());
    }

    @Test public void emptySliceDoesNotBecomeWholeFileNativeSentinel() throws Exception {
        AssetFileDescriptor asset = open(4, 0);
        assertNull(select());
        assertFalse(asset.getFileDescriptor().valid());
    }

    @Test public void pipeDescriptorIsClosedAndFallsBack() throws Exception {
        AssetFileDescriptor asset = open(0, AssetFileDescriptor.UNKNOWN_LENGTH);
        FileStat.mode = OsConstants.S_IFIFO;
        assertNull(select());
        assertFalse(asset.getFileDescriptor().valid());
    }
}
