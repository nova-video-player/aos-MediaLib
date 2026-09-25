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
import static org.junit.Assume.assumeTrue;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.StreamOverHttp;
import com.archos.filecorelibrary.contentstorage.ContentStorageFileEditor;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BufferDescriptorDeviceTest {
    private Context context;
    private File fixture;
    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getContext();
        ArchosUtils.setGlobalContext(context);
        byte[] bytes = new byte[65536];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) (i * 31);
        install(bytes);
    }
    private void install(byte[] bytes) throws Exception {
        if (fixture == null) fixture = File.createTempFile("buffer-fixture", ".bin", context.getCacheDir());
        try (OutputStream out = new FileOutputStream(fixture)) {
            byte[] guard = new byte[BufferTestProvider.PREFIX];
            Arrays.fill(guard, (byte) 0xa5);
            out.write(guard); out.write(bytes); out.write(guard);
        }
        BufferTestProvider.backing = fixture;
        BufferTestProvider.payload = bytes;
    }
    @After public void tearDown() { if (fixture != null) fixture.delete(); }

    @Test public void realAssetSlicePreservesOffsetLengthAndOwnership() throws Exception {
        AssetFileDescriptor descriptor = ContentFileDescriptor.open(context.getContentResolver(), BufferTestProvider.uri("slice"));
        assertNotNull(descriptor);
        assertEquals(BufferTestProvider.PREFIX, descriptor.getStartOffset());
        assertEquals(65536, descriptor.getDeclaredLength());
        descriptor.close();
        assertFalse(descriptor.getFileDescriptor().valid());
        try (InputStream in = new ContentStorageFileEditor(BufferTestProvider.uri("slice"), context).getInputStream(123)) {
            assertArrayEquals(Arrays.copyOfRange(BufferTestProvider.payload, 123, 65536), readAll(in));
        }
        try (AssetFileDescriptor unknown = ContentFileDescriptor.open(context.getContentResolver(), BufferTestProvider.uri("unknown"))) {
            assertNotNull(unknown);
            assertEquals(AssetFileDescriptor.UNKNOWN_LENGTH, unknown.getDeclaredLength());
        }
    }

    @Test public void pipeFallsBackToSequentialHttpWithoutLosingBytes() throws Exception {
        Uri uri = BufferTestProvider.uri("pipe");
        assertNull(ContentFileDescriptor.open(context.getContentResolver(), uri));
        StreamOverHttp proxy = new StreamOverHttp(uri, "application/octet-stream", StreamOverHttp.ReadMode.PLAYBACK);
        HttpURLConnection conn = (HttpURLConnection) new URL(proxy.getUri(null).toString()).openConnection();
        conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
        conn.setRequestProperty("Range", "bytes=123-456");
        try {
            assertEquals(200, conn.getResponseCode());
            assertEquals("none", conn.getHeaderField("Accept-Ranges"));
            try (InputStream in = conn.getInputStream()) { assertArrayEquals(BufferTestProvider.payload, readAll(in)); }
        } finally { conn.disconnect(); proxy.close(); }
    }

    /** Opt-in native check: provide a small seekable MP4 accessible to this test APK. */
    @Test public void nativePrepareAndSeekAfterJavaDescriptorCloses() throws Exception {
        String path = InstrumentationRegistry.getArguments().getString("avosFixturePath");
        assumeTrue("Supply avosFixturePath for the native descriptor test", path != null);
        File movie = new File(path);
        assertTrue("Use a small MP4 fixture (under 16 MiB)", movie.isFile() && movie.length() > 0 && movie.length() < 16 * 1024 * 1024);
        install(Files.readAllBytes(movie.toPath()));
        LibAvos.init(context);
        assertTrue("Test APK must contain the AVOS native libraries", LibAvos.isAvailable());
        prepareAndSeek(BufferTestProvider.uri("slice"), true);
        prepareAndSeek(Uri.fromFile(movie), true);
        prepareAndSeek(BufferTestProvider.uri("pipe"), false);
        String storage = InstrumentationRegistry.getArguments().getString("avosStorageUri");
        if (storage != null) prepareAndSeek(Uri.parse(storage), true);
    }

    private void prepareAndSeek(Uri uri, boolean seekable) throws Exception {
        AvosMediaPlayer player = new AvosMediaPlayer();
        CountDownLatch prepared = new CountDownLatch(1), seeked = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        player.setOnPreparedListener(p -> prepared.countDown());
        player.setOnSeekCompleteListener(new IMediaPlayer.OnSeekCompleteListener() {
            @Override public void onSeekComplete(IMediaPlayer p) { seeked.countDown(); }
            @Override public void onAllSeekComplete(IMediaPlayer p) { seeked.countDown(); }
        });
        player.setOnErrorListener((p, code, extra, message) -> {
            error.set("Native error " + code + "/" + extra);
            prepared.countDown(); seeked.countDown(); return true;
        });
        try {
            player.setDataSource(context, uri);
            player.prepareAsync();
            assertTrue("Prepare timed out", prepared.await(20, TimeUnit.SECONDS));
            assertNull(error.get());
            assertTrue("Missing duration", player.getDuration() > 0);
            if (seekable) {
                player.seekTo(player.getDuration() / 2);
                assertTrue("Seek timed out", seeked.await(20, TimeUnit.SECONDS));
                assertNull(error.get());
            }
        } finally { player.release(); }
    }
    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) >= 0;) {
            if (n == 0) throw new IOException("No progress");
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }
}
