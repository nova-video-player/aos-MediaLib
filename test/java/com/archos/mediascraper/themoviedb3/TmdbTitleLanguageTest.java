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

package com.archos.mediascraper.themoviedb3;

import static org.junit.Assert.assertEquals;

import com.uwetrottmann.tmdb2.entities.Translations;
import com.uwetrottmann.tmdb2.entities.Translations.Translation;
import com.uwetrottmann.tmdb2.entities.Translations.Translation.Data;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TmdbTitleLanguageTest {

    private Translation createMovieTranslation(String iso6391, String title) {
        Translation t = new Translation();
        t.iso_639_1 = iso6391;
        t.data = new Data();
        t.data.title = title;
        return t;
    }

    private Translation createShowTranslation(String iso6391, String name) {
        Translation t = new Translation();
        t.iso_639_1 = iso6391;
        t.data = new Data();
        t.data.name = name;
        return t;
    }

    @Test
    public void testMoviePreservesOriginalLanguageWhenTitleMatchesOriginal() {
        Translations translations = new Translations();
        translations.translations = new ArrayList<>();
        translations.translations.add(createMovieTranslation("fr", "The Agency"));
        translations.translations.add(createMovieTranslation("en", "The Agency"));

        String lang = TmdbTitleLanguage.forMovie("The Agency", "fr", "The Agency", "en", translations);
        assertEquals("en", lang);
    }

    @Test
    public void testMovieDetectsEnglishWhenTitleMatchesEnglishTranslation() {
        // "The Assassin" (Original Chinese: 刺客聶隱娘)
        Translations translations = new Translations();
        translations.translations = new ArrayList<>();
        translations.translations.add(createMovieTranslation("fr", "The Assassin"));
        translations.translations.add(createMovieTranslation("en", "The Assassin"));
        translations.translations.add(createMovieTranslation("zh", "刺客聂隐娘"));

        String lang = TmdbTitleLanguage.forMovie("The Assassin", "fr", "刺客聶隱娘", "zh", translations);
        assertEquals("en", lang);
    }

    @Test
    public void testMovieDetectsEnglishWhenTitleStartsWithEnglishOriginalTitle() {
        // "The Amazing Spider-Man : Le Destin d'un héros" (Original English: "The Amazing Spider-Man 2")
        Translations translations = new Translations();
        translations.translations = new ArrayList<>();
        translations.translations.add(createMovieTranslation("fr", "The Amazing Spider-Man : Le Destin d'un héros"));
        translations.translations.add(createMovieTranslation("en", "The Amazing Spider-Man 2"));

        String lang = TmdbTitleLanguage.forMovie("The Amazing Spider-Man : Le Destin d'un héros", "fr",
                "The Amazing Spider-Man 2", "en", translations);
        assertEquals("en", lang);
    }

    @Test
    public void testMovieGenuineTranslationUsesRequestedLanguage() {
        Translations translations = new Translations();
        translations.translations = new ArrayList<>();
        translations.translations.add(createMovieTranslation("fr", "Les Misérables"));
        translations.translations.add(createMovieTranslation("en", "The Miserable Ones"));

        String lang = TmdbTitleLanguage.forMovie("Les Misérables", "fr", "The Miserable Ones", "en", translations);
        assertEquals("fr", lang);
    }
}
