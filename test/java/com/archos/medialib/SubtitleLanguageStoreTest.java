// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;
import static org.junit.Assert.*;

import android.content.Context;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class SubtitleLanguageStoreTest {
    @Test
    public void persistenceRequiresFingerprintAndKeepsUnresolvedResults() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        String uri = "file:///store-test.mkv", fingerprint = new String(new char[64]).replace('\0', 'a');
        JSONObject result =
            new JSONObject()
                .put("fingerprint", fingerprint)
                .put("analyzedAt", 1)
                .put("tracks",
                    new JSONArray().put(
                        new JSONObject().put("uid", "18446744073709551615").put("status", "unresolved")));
        SubtitleLanguageStore.save(context, uri, result);
        assertNull(SubtitleLanguageStore.load(context, uri, fingerprint.replace('a', 'b')));
        JSONObject saved = SubtitleLanguageStore.load(context, uri, fingerprint);
        assertNotNull(saved);
        assertEquals(1, saved.getLong("analyzedAt"));
        assertEquals("unresolved", saved.getJSONArray("tracks").getJSONObject(0).getString("status"));
        SubtitleLanguageStore.journal(context, uri, result, "PENDING");
        assertEquals("PENDING", SubtitleLanguageStore.journal(context, uri).getString("state"));
        SubtitleLanguageStore.journal(context, uri, result, "UNDONE");
        assertEquals("UNDONE", SubtitleLanguageStore.journal(context, uri).getString("state"));
    }
}
