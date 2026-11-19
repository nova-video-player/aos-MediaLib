// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.Uri;
import android.os.CancellationSignal;
import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.MetaFile2Factory;
import com.archos.filecorelibrary.ReadOptions;
import java.io.ByteArrayInputStream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23, shadows = MatroskaHeaderTest.Inspector.class)
public class MatroskaHeaderTest {
    @Implements(value = MatroskaHeader.class, isInAndroidSdk = false)
    public static class Inspector {
        @Implementation protected static void __staticInitializer__() { }
        @Implementation protected static String inspect(byte[] prefix, long length) {
            assertEquals(65536, prefix.length);
            assertEquals(5_000_000_000L, length);
            return "{\"tracks\":[],\"checksums\":[]}";
        }
    }

    @Test public void networkHeaderUsesMetadataSizeWhenEditorDoesNotProvideLength() throws Exception {
        for (String scheme : new String[]{"sshj", "sftp", "smb", "smbj"}) {
            Uri uri = Uri.parse(scheme + "://server/share/movie.mkv");
            FileEditor editor = mock(FileEditor.class);
            when(editor.length()).thenReturn(-1L); // Actual default inherited by these backends.
            when(editor.getInputStream(0, ReadOptions.METADATA))
                    .thenReturn(new ByteArrayInputStream(new byte[65536]));
            MetaFile2 metadata = mock(MetaFile2.class);
            when(metadata.length()).thenReturn(5_000_000_000L);
            try (MockedStatic<FileEditorFactory> editors = mockStatic(FileEditorFactory.class);
                 MockedStatic<MetaFile2Factory> files = mockStatic(MetaFile2Factory.class)) {
                editors.when(() -> FileEditorFactory.getFileEditorForUrl(uri, RuntimeEnvironment.getApplication()))
                        .thenReturn(editor);
                files.when(() -> MetaFile2Factory.getMetaFileForUrl(uri)).thenReturn(metadata);
                JSONObject header = MatroskaHeader.read(RuntimeEnvironment.getApplication(), uri, new CancellationSignal());
                assertEquals(5_000_000_000L, header.getLong("size"));
                assertFalse(header.getString("before").isEmpty());
                verify(editor, never()).length();
                verify(editor, never()).getOutputStream();
            }
        }
    }
}
