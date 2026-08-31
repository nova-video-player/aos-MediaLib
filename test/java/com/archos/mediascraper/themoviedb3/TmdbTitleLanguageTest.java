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

    @Test
    public void testMovieDetectsEnglishForFranchiseTitlesWithLocalizedSubtitles() {
        // "The Big Short : Le Casse du Siècle" (Original: "The Big Short")
        Translations translations1 = new Translations();
        translations1.translations = new ArrayList<>();
        translations1.translations.add(createMovieTranslation("fr", "The Big Short : Le Casse du Siècle"));
        translations1.translations.add(createMovieTranslation("en", "The Big Short"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The Big Short : Le Casse du Siècle", "fr", "The Big Short", "en", translations1));

        // "The Dark Knight : Le Chevalier noir" (Original: "The Dark Knight")
        Translations translations2 = new Translations();
        translations2.translations = new ArrayList<>();
        translations2.translations.add(createMovieTranslation("fr", "The Dark Knight : Le Chevalier noir"));
        translations2.translations.add(createMovieTranslation("en", "The Dark Knight"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The Dark Knight : Le Chevalier noir", "fr", "The Dark Knight", "en", translations2));

        // "The King's Man : Première Mission" (Original: "The King's Man")
        Translations translations3 = new Translations();
        translations3.translations = new ArrayList<>();
        translations3.translations.add(createMovieTranslation("fr", "The King's Man : Première Mission"));
        translations3.translations.add(createMovieTranslation("en", "The King's Man"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The King's Man : Première Mission", "fr", "The King's Man", "en", translations3));

        // "The Witcher : Les sirènes des abysses" (Original: "The Witcher: Sirens of the Deep")
        Translations translations4 = new Translations();
        translations4.translations = new ArrayList<>();
        translations4.translations.add(createMovieTranslation("fr", "The Witcher : Les sirènes des abysses"));
        translations4.translations.add(createMovieTranslation("en", "The Witcher: Sirens of the Deep"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The Witcher : Les sirènes des abysses", "fr", "The Witcher: Sirens of the Deep", "en", translations4));
    }

    @Test
    public void testMovieDetectsEnglishForAsianAndForeignFilmsWithEnglishInternationalTitles() {
        // "The Killer" (Original Cantonese: 喋血雙雄)
        Translations translations1 = new Translations();
        translations1.translations = new ArrayList<>();
        translations1.translations.add(createMovieTranslation("fr", "The Killer"));
        translations1.translations.add(createMovieTranslation("en", "The Killer"));
        translations1.translations.add(createMovieTranslation("zh", "喋血双雄"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The Killer", "fr", "喋血雙雄", "cn", translations1));

        // "The Raid" (Original Cantonese: 財叔之橫掃千軍)
        Translations translations2 = new Translations();
        translations2.translations = new ArrayList<>();
        translations2.translations.add(createMovieTranslation("fr", "The Raid"));
        translations2.translations.add(createMovieTranslation("en", "The Raid"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The Raid", "fr", "財叔之橫掃千軍", "cn", translations2));

        // "The Seven Deadly Sins : Prisoners of the Sky" (Original Japanese: 劇場版 七つの大罪 天空の囚われ人)
        Translations translations3 = new Translations();
        translations3.translations = new ArrayList<>();
        translations3.translations.add(createMovieTranslation("fr", "The Seven Deadly Sins : Prisoners of the Sky"));
        translations3.translations.add(createMovieTranslation("en", "The Seven Deadly Sins: Prisoners of the Sky"));
        assertEquals("en", TmdbTitleLanguage.forMovie("The Seven Deadly Sins : Prisoners of the Sky", "fr", "劇場版 七つの大罪 天空の囚われ人", "ja", translations3));
    }

    @Test
    public void testShowPreservesOriginalLanguageWhenTitleMatchesOriginal() {
        // "The Franchise 2024" / "The Franchise"
        Translations translations = new Translations();
        translations.translations = new ArrayList<>();
        translations.translations.add(createShowTranslation("fr", "The Franchise"));
        translations.translations.add(createShowTranslation("en", "The Franchise"));

        String lang = TmdbTitleLanguage.forShow("The Franchise", "fr", "The Franchise", "en", translations);
        assertEquals("en", lang);
    }
}
