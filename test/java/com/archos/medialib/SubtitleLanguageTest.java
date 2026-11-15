// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.medialib;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 23)
public class SubtitleLanguageTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String ABC_SHA = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test public void onlyInferenceGetsAnAnnotation() {
        assertEquals("Français (AI 94%)", SubtitleLanguage.annotate("Français", SubtitleLanguage.FASTTEXT, 94));
        assertEquals("Français", SubtitleLanguage.annotate("Français", SubtitleLanguage.DESCRIPTION, 94));
        assertEquals("Français", SubtitleLanguage.annotate("Français", SubtitleLanguage.METADATA, 0));
        assertEquals("TEXT", SubtitleLanguage.annotate("TEXT", SubtitleLanguage.UNKNOWN, 0));
        assertEquals("French (AI 100%)", SubtitleLanguage.annotate("French", SubtitleLanguage.FASTTEXT, 101));
    }

    @Test public void modelInstallIsVerifiedAndAtomic() throws Exception {
        File model = new File(temporary.getRoot(), "model.ftz");
        SubtitleLanguageModel.install(new ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)), model, ABC_SHA);
        assertTrue(SubtitleLanguageModel.matches(model, ABC_SHA));
        try {
            SubtitleLanguageModel.install(new ByteArrayInputStream("corrupt".getBytes(StandardCharsets.UTF_8)), model, ABC_SHA);
            fail("A corrupt model must not be published");
        } catch (IOException expected) { }
        assertTrue("Failed replacement must preserve the verified file", SubtitleLanguageModel.matches(model, ABC_SHA));
    }
}
