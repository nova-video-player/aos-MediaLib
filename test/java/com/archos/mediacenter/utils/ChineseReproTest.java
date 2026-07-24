package com.archos.mediacenter.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ChineseReproTest {

    // Regression test for the audio track fixed on nova-video-player/aos-AVP#1786: favorite audio
    // language matching must compare normalized ISO 639 codes, not rendered/localized track names,
    // and must not depend on isLanguageInString()'s "trailing (Language)" regex.

    @Test
    public void favoriteChineseMatchesMandarinChiTrack() {
        assertTrue(ISO639codes.isFavoriteLanguageMatch("zh", "chi"));
    }

    @Test
    public void favoriteChineseMatchesCantoneseChiTrack() {
        assertTrue(ISO639codes.isFavoriteLanguageMatch("zh", "chi"));
    }

    @Test
    public void favoriteChineseDoesNotMatchEnglishTrack() {
        assertFalse(ISO639codes.isFavoriteLanguageMatch("zh", "eng"));
    }

    @Test
    public void favoriteChineseMainlandVariantMatchesMandarinTitle() {
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-cn", "Mandarin"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh-cn", "Cantonese"));
    }

    @Test
    public void favoriteChineseHkVariantMatchesCantoneseTitle() {
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-ca", "Cantonese"));
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-hk", "Cantonese"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh-ca", "Mandarin"));
    }

    @Test
    public void favoriteChineseTaiwanVariantMatchesTaiwanTitle() {
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-tw", "Taiwanese"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh-tw", "Mandarin"));
    }

    @Test
    public void nonChineseFavoriteHasNoVariantMatch() {
        assertFalse(ISO639codes.titleMatchesChineseVariant("fr", "Mandarin"));
    }

    @Test
    public void default3LetterFavoriteMatchesChiTrack() {
        // default favorite value is Locale.getDefault().getISO3Language(), e.g. "zho"
        assertTrue(ISO639codes.isFavoriteLanguageMatch("zho", "chi"));
    }

    // The audio favorite-language ListPreference (KEY_AUDIO_TRACK_FAV_LANG) is built from
    // VideoPreferencesCommon.OPENSUBTITLES_LANGUAGES, which only exposes "zh-cn"/"zh-tw"/"zh-ca"
    // as selectable Chinese entries (bare "zh" only appears when the system default locale itself
    // is Chinese). So the stored preference value is realistically one of these hyphenated
    // pseudo-codes, not a plain "zh" — confirm the code-match still normalizes them correctly.
    @Test
    public void mainlandFavoriteValueMatchesChiTrack() {
        assertTrue(ISO639codes.isFavoriteLanguageMatch("zh-cn", "chi"));
    }

    @Test
    public void taiwanFavoriteValueMatchesChiTrack() {
        assertTrue(ISO639codes.isFavoriteLanguageMatch("zh-tw", "chi"));
    }

    @Test
    public void cantoneseFavoriteValueMatchesChiTrack() {
        assertTrue(ISO639codes.isFavoriteLanguageMatch("zh-ca", "chi"));
    }
}
