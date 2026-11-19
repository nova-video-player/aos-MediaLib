// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import static org.mockito.Mockito.*;
import static org.junit.Assert.*;

import android.net.Uri;
import com.archos.filecorelibrary.StreamOverHttp.ReadMode;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23, shadows = SubtitleAnalysisReadModeTest.NativeRetriever.class)
public class SubtitleAnalysisReadModeTest {
    @Implements(value = AvosMediaMetadataRetriever.class, isInAndroidSdk = false)
    public static class NativeRetriever {
        static String opened;
        @Implementation protected void create() { }
        @Implementation protected void nativeSetDataSource(String path, String[] keys, String[] values) {
            opened = path;
        }
    }

    @Test public void allShareProtocolsOptInOnlyDuringAnalysis() throws Exception {
        Map<String, String> headers = Collections.singletonMap("X-Test", "preserved");
        for (String scheme : new String[]{"sshj", "sftp", "smb", "smbj", "ftp", "ftps", "webdav", "webdavs"}) {
            Uri uri = Uri.parse(scheme + "://example.test/video.mkv");
            AvosMediaMetadataRetriever retriever = new AvosMediaMetadataRetriever();
            SmbProxy analysis = mock(SmbProxy.class);
            SmbProxy metadata = mock(SmbProxy.class);
            try (MockedStatic<SmbProxy> proxy = mockStatic(SmbProxy.class)) {
                proxy.when(() -> SmbProxy.needToStream(scheme)).thenReturn(true);
                proxy.when(() -> SmbProxy.setDataSource(uri, retriever, headers, ReadMode.SUBTITLE_ANALYSIS))
                        .thenReturn(analysis);
                proxy.when(() -> SmbProxy.setDataSource(uri, retriever, headers, ReadMode.DEFAULT))
                        .thenReturn(metadata);
                SubtitleLanguageAnalysis.openForAnalysis(retriever, RuntimeEnvironment.getApplication(), uri, headers);
                proxy.verify(() -> SmbProxy.setDataSource(uri, retriever, headers, ReadMode.SUBTITLE_ANALYSIS));
                SubtitleLanguageAnalysis.open(retriever, RuntimeEnvironment.getApplication(), uri, headers);
                proxy.verify(() -> SmbProxy.setDataSource(uri, retriever, headers, ReadMode.DEFAULT));
                verify(analysis).stop(); // Normal metadata replaces and closes the analysis proxy.
            }
        }
    }

    @Test public void httpAndLocalInputsStillGoDirectlyToNative() throws Exception {
        for (String source : new String[]{"https://example.test/video.mkv", "http://example.test/video.mkv",
                "file:///video.mkv", "/video.mkv"}) {
            AvosMediaMetadataRetriever retriever = new AvosMediaMetadataRetriever();
            SubtitleLanguageAnalysis.openForAnalysis(retriever, RuntimeEnvironment.getApplication(), Uri.parse(source), null);
            assertEquals(source.startsWith("file:") ? "/video.mkv" : source, NativeRetriever.opened);
        }
    }

    @Test public void documentUrisKeepProviderDescriptorHandling() throws Exception {
        AvosMediaMetadataRetriever retriever = mock(AvosMediaMetadataRetriever.class);
        Uri uri = Uri.parse("content://documents/video");
        SubtitleLanguageAnalysis.openForAnalysis(retriever, RuntimeEnvironment.getApplication(), uri, null);
        verify(retriever).setDataSource(RuntimeEnvironment.getApplication(), uri);
        verify(retriever, never()).setSubtitleAnalysisDataSource(anyString(), any());
    }
}
