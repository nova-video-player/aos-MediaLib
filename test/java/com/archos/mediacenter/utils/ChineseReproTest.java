package com.archos.mediacenter.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class ChineseReproTest {

    private Locale defaultLocale;

    @Before
    public void saveDefaultLocale() {
        defaultLocale = Locale.getDefault();
    }

    @After
    public void restoreDefaultLocale() {
        Locale.setDefault(defaultLocale);
    }

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

    // Regression test for the subtitle track selection fixed on nova-video-player/aos-AVP#1832:
    // subtitle tracks tagged with the same "chi" language but distinguished only by a
    // Simplified/Traditional title must be disambiguated the same way audio tracks are.

    @Test
    public void mainlandFavoriteVariantMatchesSimplifiedSubtitleTitle() {
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-cn", "Simplified"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh-cn", "Traditional"));
    }

    @Test
    public void taiwanFavoriteVariantMatchesTraditionalSubtitleTitle() {
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-tw", "Traditional"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh-tw", "Simplified"));
    }

    @Test
    public void hkFavoriteVariantMatchesTraditionalSubtitleTitle() {
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-hk", "Traditional"));
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh-ca", "Traditional"));
    }

    // Regression test: mAudioTrackFavoriteLanguage/mSubsFavoriteLanguage default to
    // Locale.getDefault().getISO3Language() (e.g. "zho") when the user never set an explicit
    // preference. getISO3Language() drops the country, so a zh_CN, zh_TW or zh_HK system locale
    // all yield the same bare "zho" favorite value with no variant info by itself: the variant
    // heuristic must fall back to the system locale's country in that case.

    @Test
    public void bareZhoFavoriteFallsBackToMainlandForChinaLocale() {
        Locale.setDefault(Locale.forLanguageTag("zh-CN"));
        assertTrue(ISO639codes.titleMatchesChineseVariant("zho", "Simplified"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zho", "Traditional"));
    }

    @Test
    public void bareZhFavoriteFallsBackToTaiwanForTaiwanLocale() {
        Locale.setDefault(Locale.forLanguageTag("zh-TW"));
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh", "Traditional"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh", "Simplified"));
    }

    @Test
    public void bareZhFavoriteFallsBackToHkForHongKongLocale() {
        Locale.setDefault(Locale.forLanguageTag("zh-HK"));
        assertTrue(ISO639codes.titleMatchesChineseVariant("zh", "Cantonese"));
    }

    @Test
    public void bareZhFavoriteHasNoVariantMatchForNonChineseLocale() {
        Locale.setDefault(Locale.forLanguageTag("fr-FR"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh", "Mandarin"));
        assertFalse(ISO639codes.titleMatchesChineseVariant("zh", "Simplified"));
    }

    // Regression test for the shared audio/subtitle default-track selection priority used by
    // PlayerService.onAudioMetadataUpdated()/onSubtitleMetadataUpdated(): a Chinese-variant title
    // match wins over the container "default" flag, which wins over the first language match.

    @Test
    public void selectPreferredTrackReturnsNullWhenNoCandidate() {
        assertNull(ISO639codes.selectPreferredTrack(null, null, null));
    }

    @Test
    public void selectPreferredTrackFallsBackToFirstLanguageMatch() {
        assertTrue(ISO639codes.selectPreferredTrack(null, null, 2) == 2);
    }

    @Test
    public void selectPreferredTrackPrefersContainerDefaultOverFirstMatch() {
        assertTrue(ISO639codes.selectPreferredTrack(null, 1, 2) == 1);
    }

    @Test
    public void selectPreferredTrackPrefersVariantMatchOverDefaultAndFirstMatch() {
        assertTrue(ISO639codes.selectPreferredTrack(0, 1, 2) == 0);
    }
}
