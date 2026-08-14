// Copyright 2023 Courville Software
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

package com.archos.mediacenter.utils;

import static com.archos.mediascraper.StringUtils.capitalizeFirstLetter;

import android.content.Context;
import android.content.res.Resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ISO639codes {

    private static final Logger log = LoggerFactory.getLogger(ISO639codes.class);

    // check https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes

    static private HashMap<String, String> missingISO6391ToISO6393 = new HashMap<>();
    static {
        // https://www.opensubtitles.org/addons/export_languages.php
        missingISO6391ToISO6393.put("at", "ast"); // Asturian (at used for opensubtitles)
        // pob does not exist as ISO639-3 code: use string exception
        missingISO6391ToISO6393.put("pb", "s_brazilian"); // Brazilian Portuguese (pb used for opensubtitles), pb=pob
        missingISO6391ToISO6393.put("zt", "s_traditional_chinese"); // take yue = Cantonese (zt is used for opensubtitles and should be Traditional Chinese which ISO 639-3 code does not exist e.g. cmn)

        // https://api.opensubtitles.com/api/v1/infos/languages
        // opensubtitles REST API language code exceptions (2 letters that are 4 letters)
        missingISO6391ToISO6393.put("pt-br", "s_brazilian"); // Brazilian Portuguese (pb used for opensubtitles), pb=pob
        missingISO6391ToISO6393.put("pt-pt", "por"); // Portuguese
        missingISO6391ToISO6393.put("zh-cn", "s_chinese_mainland"); // Mandarin, Chinese Mainland, Chinese Simplified (most of the time)
        missingISO6391ToISO6393.put("zh-ca", "s_chinese_hk"); // Cantonese, Chinese Hong Kong
        missingISO6391ToISO6393.put("zh-tw", "s_chinese_tw"); // Min, Chinese Taiwan, Chinese Traditional (most of the time)
        missingISO6391ToISO6393.put("zh-hk", "s_chinese_hk"); // Cantonese, Chinese Hong Kong
        // opensubtitles REST API not recognized languages ea=Spanish (LA)|ex=Extremaduran|me=Montenegrin|ma=Manipuri|pr=Dari|pm=Portuguese (MZ)|sp=Spanish (EU)|sx=Santali|sy=Syriac|tp=Toki Pona|ze=Chinese bilingual
        missingISO6391ToISO6393.put("ea", "s_spanish_la"); // Spanish (LA)
        missingISO6391ToISO6393.put("ex", "s_extremaduran"); // Extremaduran
        missingISO6391ToISO6393.put("me", "s_montenegrin"); // Montenegrin
        missingISO6391ToISO6393.put("ma", "s_manipuri"); // Manipuri
        missingISO6391ToISO6393.put("pr", "s_dari"); // Dari
        missingISO6391ToISO6393.put("pm", "s_portuguese_mz"); // Portuguese (MZ)
        missingISO6391ToISO6393.put("sp", "s_spanish_eu"); // Spanish (EU)
        missingISO6391ToISO6393.put("sx", "s_santali"); // Santali
        missingISO6391ToISO6393.put("sy", "s_syriac"); // Syriac
        missingISO6391ToISO6393.put("tp", "s_toki_pona"); // Toki Pona
        missingISO6391ToISO6393.put("ze", "s_chinese_bilingual"); // Chinese bilingual

        // cf. https://github.com/nova-video-player/aos-AVP/issues/1129
        //missingISO6391ToISO6393.put("cn", "yue"); // take yue = Cantonese (cn is used for tmdb)
        // zh = Mandarin -> Chinese Simplified (zh-cn) or Chinese
        // cn = Cantonese -> Chinese Traditional (zh-hk)
        missingISO6391ToISO6393.put("cn", "s_chinese_hk"); // cn = Cantonese, Chinese Hong Kong
        missingISO6391ToISO6393.put("zh", "s_chinese_mainland"); // zh = Mandarin, Chinese Mainland, Chinese Simplified (most of the time)
        // Moldavian is now officially Romanian since 202303 mo -> ron
        // {"iso_639_1":"mo","english_name":"Moldavian","name":""}
        missingISO6391ToISO6393.put("mo", "ron"); // Moldovian != Romanian it is an issue (mo is used for tmdb)
    }

    static private HashMap<String, String> missingISO6393ToISO6391 = new HashMap<>();
    static {
        missingISO6393ToISO6391.put("ast", "at");
        //missingISO6393ToISO6391.put("pob", "pb"); // pob is not a valid ISO 639-3 code
        missingISO6393ToISO6391.put("yue", "zt");
        // /!\ cannot have two maps with the same key -> not used thus prioritize opensubtitles
        //missingISO6391ToISO6393.put("yue", "cn");
        //missingISO6391ToISO6393.put("ron", "mo"); // cannot make the reverse since Romanian exists
    }

    static private HashMap<String, String> iso63923ToIso6392b = new HashMap<>();
    static {
        // existing codes in opensubtitles
        iso63923ToIso6392b.put("fra", "fre");
        iso63923ToIso6392b.put("deu", "ger");
        iso63923ToIso6392b.put("zho", "chi");
        iso63923ToIso6392b.put("ces", "cze");
        iso63923ToIso6392b.put("fas", "per");
        iso63923ToIso6392b.put("nld", "dut");
        iso63923ToIso6392b.put("ron", "rum");
        iso63923ToIso6392b.put("slk", "slo");
        iso63923ToIso6392b.put("srp", "scc");
        // non existing codes
        iso63923ToIso6392b.put("hye", "arm");
        iso63923ToIso6392b.put("eus", "baq");
        iso63923ToIso6392b.put("mya", "bur");
        iso63923ToIso6392b.put("kat", "geo");
        iso63923ToIso6392b.put("ell", "gre");
        iso63923ToIso6392b.put("isl", "ice");
        iso63923ToIso6392b.put("kmb", "kim");
        iso63923ToIso6392b.put("mkd", "mac");
        iso63923ToIso6392b.put("mri", "mao");
        iso63923ToIso6392b.put("msa", "may");
        iso63923ToIso6392b.put("s_brazilian", "pob"); // specific to opensubtitles
        iso63923ToIso6392b.put("s_traditional_chinese", "zht"); // specific to opensubtitles
    }

    // use with iso63922bToIso6393.get(code)
    static private HashMap<String, String> iso63922bToIso6393 = new HashMap<>();
    static {
        // existing codes in opensubtitles
        iso63922bToIso6393.put("fre", "fra");
        iso63922bToIso6393.put("ger", "deu");
        iso63922bToIso6393.put("chi", "zho");
        iso63922bToIso6393.put("cze", "ces");
        iso63922bToIso6393.put("per", "fas");
        iso63922bToIso6393.put("dut", "nld");
        iso63922bToIso6393.put("rum", "ron");
        iso63922bToIso6393.put("slo", "slk");
        iso63922bToIso6393.put("scc", "srp");
        // non existing codes
        iso63922bToIso6393.put("arm", "hye");
        iso63922bToIso6393.put("baq", "eus");
        iso63922bToIso6393.put("bur", "mya");
        iso63922bToIso6393.put("geo", "kat");
        iso63922bToIso6393.put("gre", "ell");
        iso63922bToIso6393.put("ice", "isl");
        iso63922bToIso6393.put("kim", "kmb");
        iso63922bToIso6393.put("mac", "mkd");
        iso63922bToIso6393.put("mao", "mri");
        iso63922bToIso6393.put("may", "msa");
        // ISO 639-3 extlang of zho not natively resolved by Locale, normalize to macrolanguage code
        iso63922bToIso6393.put("cmn", "zho"); // Mandarin Chinese
    }

    static public String getLanguageNameForLetterCode(String code) {
        if (code == null) {
            log.error("getLanguageNameForLetterCode: null code!");
            return "unknown";
        }
        if (code.length() == 2) {
            return getLanguageNameFor2LetterCode(code);
        } else if (code.length() == 3) {
            return getLanguageNameFor3LetterCode(code);
        } else {
            if (log.isDebugEnabled()) log.debug("getLanguageNameForLetterCode: Invalid code {}", code);
            return code;
        }
    }

    public static String getEnglishLanguageNameForLetterCode(String code) {
        if (code == null || code.isEmpty()) return "";
        String iso1 = getISO6391ForLetterCode(code);
        if (iso1.length() == 2) {
            return Locale.forLanguageTag(iso1).getDisplayLanguage(Locale.ENGLISH);
        }

        Locale locale;
        if (code.length() == 2) {
            locale = Locale.forLanguageTag(code);
        } else if (code.length() == 3) {
            String iso3 = convertIso6392bToIso6393(code);
            locale = new Locale(iso3);
        } else {
            return "";
        }
        return locale.getDisplayLanguage(Locale.ENGLISH);
    }

    @SuppressWarnings("deprecation")
    static public String getLanguageNameFor3LetterCode(String code) {
        // handles ISO 639-3 and ISO 639-2b (e.g. fra/fre, deu/ger)
        Locale locale = new Locale(code);
        String languageName = locale.getDisplayLanguage();
        if (languageName.equals(code)) {
            // it has not been found thus perhaps it is ISO 639-2b and conversion is needed
            String iso6393Code = convertIso6392bToIso6393(code);
            if (iso6393Code != null && !iso6393Code.equals(code)) {
                locale = new Locale(iso6393Code);
                languageName = locale.getDisplayLanguage();
            }
            if (languageName.equals(code) || (iso6393Code != null && languageName.equals(iso6393Code))) {
                // If still not found, try converting ISO 639-3/2 code to ISO 639-1 (e.g. zho -> zh)
                String iso1 = convertISO6393ToISO6391(iso6393Code != null ? iso6393Code : code);
                if (iso1 != null && !iso1.equals(code) && !iso1.equals(iso6393Code)) {
                    languageName = convertISO6391ToLanguageName(iso1);
                } else {
                    // there is something missing make it obvious and fallback to original 3 letter code
                    languageName = code;
                    if (log.isDebugEnabled()) log.debug("getLanguageNameFor3LetterCode: No language name found for code {}", code);
                }
            }
        }
        return languageName;
    }

    @SuppressWarnings("deprecation")
    static public String getLanguageNameFor2LetterCode(String code) {
        // handles ISO 639-1 with exceptions; also handles non-standard OpenSubtitles codes like pt-br,
        // zh-cn, zh-tw — new Locale(code) returns the raw code as display language for unrecognised
        // values, triggering the missingISO6391ToISO6393 fallback (e.g. pt-br → s_brazilian).
        Locale locale = new Locale(code);
        String languageName = locale.getDisplayLanguage();
        if (languageName.equals(code)) {
            // it has not been found thus perhaps it is a missing ISO 639-1 and conversion to ISO 639-3 is needed
            String iso6393Code = convertISO6391ToISO6393(code);
            if (iso6393Code.startsWith("s_")) {
                return iso6393Code; // return exception string (cannot be translated in MediaLib)
            } else {
                if (iso6393Code != null) {
                    locale = new Locale(iso6393Code);
                    languageName = locale.getDisplayLanguage();
                } else {
                    // there is something missing make it obvious and fallback to original 3 letter code
                    languageName = code;
                    log.error("getLanguageNameFor2LetterCode: No language name found for code {}", code);
                }
            }
        }
        return languageName;
    }

    static public String getISO6393ForLetterCode(String code) {
        if (code == null) {
            log.error("getISO6393ForLetterCode: null code!");
            return "unknown";
        }
        if (code.length() == 2) {
            return convertISO6391ToISO6393(code);
        } else if (code.length() == 3) {
            return convertIso6392bToIso6393(code);
        } else {
            log.error("getISO6393ForLetterCode: Invalid code {}", code);
            return code;
        }
    }

    static public String getISO6391ForLetterCode(String code) {
        String result = "";
        if (code == null) {
            log.error("getISO6391ForLetterCode: null code!");
            return result;
        }
        if (code.length() == 2) result = Locale.forLanguageTag(code).getLanguage();
        if (code.length() == 3) {
            String iso3 = convertIso6392bToIso6393(code);
            result = missingISO6393ToISO6391.get(iso3);
            if (result == null) {
                result = convertISO6393ToISO6391(iso3);
            }
        }
        if (result.length() == 2) {
            if (log.isDebugEnabled()) log.debug("getISO6391ForLetterCode: code={} result={}", code, result);
            return result;
        } else {
            if (log.isDebugEnabled()) log.debug("getISO6391ForLetterCode: Invalid code {}", code);
            return "";
        }
    }

    static public boolean is3letterCode(String code) {
        String result = getLanguageNameFor3LetterCode(code);
        return !code.equals(result);
    }

    static public boolean isletterCode(String code) {
        String result = getLanguageNameForLetterCode(code);
        if (log.isDebugEnabled()) log.debug("isletterCode: code={} result={}", code, result);
        return !code.equals(result);
    }

    static public String convertISO6391ToISO6393(String code) {
        String result = missingISO6391ToISO6393.get(code);
        if (result == null) {
            Locale locale = Locale.forLanguageTag(code);
            try {
                result = locale.getISO3Language();
            } catch (MissingResourceException e) {
                log.error("convertISO6391ToISO6393: No ISO3 found for code {}", code);
                result = code;
            }
        }
        return result;
    }

    static public String convertISO6391ToISO6392(String code) {
        String result = missingISO6391ToISO6393.get(code);
        if (result == null) {
            Locale locale = Locale.forLanguageTag(code);
            try {
                result = locale.getISO3Language();
            } catch (MissingResourceException e) {
                log.error("convertISO6391ToISO6393: No ISO3 found for code {}", code);
                result = code;
            }
        }
        return convertIso6393ToIso6392b(result);
    }

    @SuppressWarnings("deprecation")
    static public String convertISO6393ToISO6391(String code) {
        String result = missingISO6393ToISO6391.get(code);
        if (result == null) {
            for (String iso1 : Locale.getISOLanguages()) {
                Locale locale = Locale.forLanguageTag(iso1);
                try {
                    if (locale.getISO3Language().equals(code)) {
                        result = iso1;
                        break;
                    }
                } catch (MissingResourceException ignored) {
                    // Continue scanning the system language table.
                }
            }
            if (result == null) {
                if (log.isDebugEnabled()) log.debug("convertISO6393ToISO6391: No ISO1 found for code {}", code);
                result = code;
            }
        }
        return result;
    }

    static public String convertIso6392bToIso6393(String code) {
        String result = iso63922bToIso6393.get(code);
        if (result == null) return code;
        else return result;
    }

    static public String convertIso6393ToIso6392b(String code) {
        if (code.equals("system"))
            return convertIso6393ToIso6392b(Locale.getDefault().getISO3Language());
        String result = iso63923ToIso6392b.get(code);
        if (result == null) return code;
        else return result;
    }

    @SuppressWarnings("deprecation")
    public static String convertISO6393ToLanguageName(String iso6393Code) {
        Locale locale = new Locale(iso6393Code);
        return locale.getDisplayLanguage();
    }

    public static String convertISO6391ToLanguageName(String iso6391Code) {
        if (iso6391Code.equals("system"))
            return Locale.getDefault().getDisplayLanguage();
        Locale locale = Locale.forLanguageTag(iso6391Code);
        return locale.getDisplayLanguage();
    }

    public static String[] convertISO6391ToLanguageNames(String languageCodes) {
        String[] languageCodeArray = languageCodes.split("\\|");
        String[] languageNames = new String[languageCodeArray.length];

        for (int i = 0; i < languageCodeArray.length; i++) {
            Locale locale = Locale.forLanguageTag(languageCodeArray[i]);
            languageNames[i] = locale.getDisplayLanguage();
        }
        return languageNames;
    }

    static public CharSequence getLanguageString(Context context, CharSequence name) {
        final Resources resources = context.getResources();
        CharSequence lang;
        if (name == null)
            return "Unknown";
        int resId = resources.getIdentifier((String) name, "string", context.getPackageName());
        try {
            lang = resources.getText(resId);
        } catch (Resources.NotFoundException e) {
            lang = name;
        }
        return lang;
    }

    public static String findLanguageInString(String string) {
        // treat strings being "l_XYZ" or "l_XY" or "XYZ" or "XY" or "title (l_XYZ)" or "title (l_XY)"
        // and return locale language corresponding to XY or XYZ letter code
        if (string == null) return null;
        String result = null;
        //String pattern = "(?:^l_([A-Za-z]{2,3}$)|\\(l_([A-Za-z]{2,3})\\)$)"; // 2 or 3 letters code
        String pattern = "(?:^l_([A-Za-z]{2,3}\\b)|\\(l_([A-Za-z]{2,3})\\)$)"; // 2 or 3 letters code
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(string);
        String languageCode = "";
        if (matcher.find()) {
            String languageCode1 = matcher.group(1);
            String languageCode2 = matcher.group(2);
            if (log.isDebugEnabled()) log.debug("findLanguageInString: languageCode1={} languageCode2={}", languageCode1, languageCode2);
            if (languageCode1 != null) {
                languageCode = languageCode1;
                if (languageCode1.equals("und") || languageCode1.equals("Unknown")) result = ""; // und = undefined
                else result = ISO639codes.getLanguageNameForLetterCode(languageCode);
            } else if (languageCode2 != null) {
                languageCode = languageCode2;
                result = ISO639codes.getLanguageNameForLetterCode(languageCode);
            }
        } else {
            pattern = "^[A-Za-z]{2,3}$"; // 2 or 3 letters code
            regexPattern = Pattern.compile(pattern);
            matcher = regexPattern.matcher(string);
            if (matcher.find()) {
                languageCode = matcher.group();
                if (log.isDebugEnabled()) log.debug("findLanguageInString: languageCode={}", languageCode);
                if (languageCode.equals("und") || languageCode.equals("Unknown")) result = ""; // und = undefined
                else result = ISO639codes.getLanguageNameForLetterCode(languageCode);
            } else result = "";
        }
        if (log.isDebugEnabled()) log.debug("findLanguageInString: input={} -> result={}", string, result);
        return result;
    }

    public static boolean isLanguageInString(String language, String string) {
        // check if string is of the form "language" or "description (language)"
        String pattern = "^" + language + "$|^.* \\(" + language + "\\)$";
        return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(string).matches();
    }

    // Chinese sub-variant favorite codes (see missingISO6391ToISO6393) and the free-text title
    // keywords (English + Chinese) used to best-effort disambiguate Mandarin/Cantonese/Taiwan
    // audio tracks, or Simplified/Traditional subtitle tracks, since ISO 639 has no distinct
    // code for these variants (all commonly tagged "chi"/"zho").
    private static final String[] CHINESE_MAINLAND_KEYWORDS = {"mandarin", "mainland", "simplified", "putonghua", "国语", "普通话", "简体", "大陆"};
    private static final String[] CHINESE_HK_KEYWORDS = {"cantonese", "hong kong", "hongkong", "traditional", "粤语", "廣東話", "广东话", "香港", "繁體", "繁体"};
    private static final String[] CHINESE_TW_KEYWORDS = {"taiwan", "taiwanese", "traditional", "台灣", "台湾", "繁體", "繁体"};

    private static String[] chineseVariantKeywordsFor(String favoriteLanguageCode) {
        if (favoriteLanguageCode == null) return null;
        switch (favoriteLanguageCode) {
            case "zh-cn": return CHINESE_MAINLAND_KEYWORDS;
            case "zh-ca":
            case "zh-hk": return CHINESE_HK_KEYWORDS;
            case "zh-tw": return CHINESE_TW_KEYWORDS;
        }
        // A bare "zh"/"zho"/"chi" favorite (e.g. the default value derived from
        // Locale.getDefault().getISO3Language() when the user never set an explicit preference)
        // carries no sub-variant info by itself since getISO3Language() drops the country: a
        // zh_CN, zh_TW or zh_HK system locale all yield the same "zho". Fall back to the system
        // locale's country to infer the intended variant in that case.
        if ("zh".equals(baseISO6391Code(favoriteLanguageCode))) {
            String country = Locale.getDefault().getCountry();
            if (country != null) {
                switch (country) {
                    case "TW": return CHINESE_TW_KEYWORDS;
                    case "HK":
                    case "MO": return CHINESE_HK_KEYWORDS;
                    case "CN":
                    case "SG": return CHINESE_MAINLAND_KEYWORDS;
                }
            }
        }
        return null;
    }

    // Normalizes any favorite/track language code (2-letter, 3-letter, or OpenSubtitles-style
    // pseudo-code like "zh-cn") to a comparable ISO 639-1 code, ignoring any variant suffix.
    private static String baseISO6391Code(String code) {
        if (code == null || code.isEmpty()) return "";
        String base = code.contains("-") ? code.substring(0, code.indexOf('-')) : code;
        if (base.length() == 2 || base.length() == 3) {
            return getISO6391ForLetterCode(base);
        }
        return "";
    }

    /**
     * Returns true if the track's raw ISO 639 language code matches the user's favorite audio
     * language, comparing normalized ISO 639-1 codes instead of rendered/localized display
     * strings (which vary with the naming rules and UI locale, unlike codes).
     */
    public static boolean isFavoriteLanguageMatch(String favoriteLanguageCode, String trackLanguageCode) {
        if (favoriteLanguageCode == null || trackLanguageCode == null) return false;
        String favBase = baseISO6391Code(favoriteLanguageCode);
        String trackBase = baseISO6391Code(trackLanguageCode);
        return !favBase.isEmpty() && favBase.equals(trackBase);
    }

    /**
     * Best-effort heuristic: for Chinese sub-variant favorites (Mainland/Hong Kong/Taiwan, which
     * have no dedicated ISO 639 code), checks whether the track's free-text title contains a
     * known keyword for that variant. Used both for audio tracks (Mandarin/Cantonese/Taiwan
     * spoken-language hints) and subtitle tracks (Simplified/Traditional script hints).
     */
    public static boolean titleMatchesChineseVariant(String favoriteLanguageCode, String trackTitle) {
        String[] keywords = chineseVariantKeywordsFor(favoriteLanguageCode);
        if (keywords == null || trackTitle == null || trackTitle.isEmpty()) return false;
        String title = trackTitle.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (title.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /**
     * Shared priority rule for audio/subtitle default-track auto-selection among tracks matching
     * the favorite language: a Chinese-variant title match wins, else the track flagged "default"
     * by the container, else the first language-matching track. Any argument may be null if no
     * such track was found while scanning the candidates. Returns null if all three are null.
     */
    public static Integer selectPreferredTrack(Integer variantMatchTrack, Integer defaultMatchTrack, Integer languageMatchTrack) {
        if (variantMatchTrack != null) return variantMatchTrack;
        if (defaultMatchTrack != null) return defaultMatchTrack;
        return languageMatchTrack;
    }

    public static String removeStartingSpacesAndSurroundingParenthesis(String string) {
        String result = string.replaceAll("^\\s+", ""); // Remove starting spaces
        if (!result.contains(")(")) { // no nesting parenthesis
            result = result.replaceAll("^\\(([^)]*)\\)$", "$1"); // Remove surrounding parenthesis if the content does not contain a closing parenthesis
        }
        return result;
    }

    public static String removeStartingSpaces(String string) {
        return string.replaceAll("^\\s+", ""); // Remove starting spaces
    }

    public static String replaceLanguageCodeInString(String string) {
        // treat strings being "l_XYZ" or "l_XY" or "XYZ" or "XY" or "title (l_XYZ)" or "title (l_XY)"
        // and replace it with locale language corresponding to XY or XYZ letter code
        if (log.isDebugEnabled()) log.debug("replaceLanguageCodeInString: input string={}", string);
        if (string == null) return "";
        // avoid name=" (l_eng)" with starting space seen in Modern Family show
        String cleanString = removeStartingSpacesAndSurroundingParenthesis(string);
        String pattern = "(?:^l_([A-Za-z]{2,3}\\b)|\\(l_([A-Za-z]{2,3})\\)$)"; // 2 or 3 letters code
        String languageCode = "";
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(cleanString);
        String result = "";
        if (matcher.find()) {
            String languageCode1 = matcher.group(1);
            String languageCode2 = matcher.group(2);
            if (log.isDebugEnabled()) log.debug("replaceLanguageCodeInString: languageCode1={} languageCode2={}", languageCode1, languageCode2);
            if (languageCode1 != null) {
                languageCode = languageCode1;
                if (languageCode1.equals("und") || languageCode1.equals("Unknown")) result = ""; // und = undefined
                else result = capitalizeFirstLetter(cleanString.replaceAll(pattern, ISO639codes.getLanguageNameForLetterCode(languageCode)));
            } else if (languageCode2 != null) {
                languageCode = languageCode2;
                result = cleanString.replaceAll(pattern, "(" + ISO639codes.getLanguageNameForLetterCode(languageCode) + ")");
            }
        } else {
            // could be that it is a 2|3 letter code alone: treat this case too
            pattern = "^[A-Za-z]{2,3}$"; // 2 or 3 letters code
            regexPattern = Pattern.compile(pattern);
            matcher = regexPattern.matcher(cleanString);
            if (matcher.find()) {
                languageCode = matcher.group();
                if (log.isDebugEnabled()) log.debug("replaceLanguageCodeInString: languageCode={}", languageCode);
                if (languageCode.equals("und") || languageCode.equals("Unknown")) result = ""; // und = undefined
                else result = capitalizeFirstLetter(cleanString.replaceAll(pattern, ISO639codes.getLanguageNameForLetterCode(languageCode)));
            } else {
                log.error("replaceLanguageCodeInString: no languageCode in {}", cleanString);
                result = cleanString;
            }
        }
        if (log.isDebugEnabled()) log.debug("replaceLanguageCodeInString: input={} -> result={}", string, result);
        return result;
    }

    public static String generateTrackName(String string, String lang, String langName, String format, int disposition, boolean titleFirst, String dispLabel, String unknownTrackName) {
        String title = (string != null) ? removeStartingSpacesAndSurroundingParenthesis(string) : "";

        // Handle "undefined" language
        if (lang != null && (lang.equals("und") || lang.equalsIgnoreCase("unknown"))) {
            langName = "";
        }

        // Redundancy Filtering for Primary Selection
        if (!title.isEmpty()) {
            String lowerTitle = title.toLowerCase();

            // 1. Check if title is a language code resolving to the same language
            boolean titleIsLang = false;
            if (title.length() == 2 || title.length() == 3) {
                String titleAsLangName = getLanguageNameForLetterCode(title);
                titleIsLang = titleAsLangName.equalsIgnoreCase(langName);
            }

            // 2. Check against English name
            if (!titleIsLang && lang != null) {
                String engLang = getEnglishLanguageNameForLetterCode(lang);
                titleIsLang = lowerTitle.equals(engLang.toLowerCase());
            }

            // 3. Check against raw codes
            if (!titleIsLang && lang != null) {
                titleIsLang = lowerTitle.equals(lang.toLowerCase());
            }

            // 4. Check if title is only language + disposition, e.g. "English (SDH)" or "French (Forced)".
            boolean titleIsLangWithDisposition = false;
            if (!titleIsLang && lang != null && dispLabel != null && !dispLabel.isEmpty()) {
                String lowerDisp = dispLabel.toLowerCase();
                String lowerLangName = (langName != null) ? langName.toLowerCase() : "";
                String lowerEngLang = getEnglishLanguageNameForLetterCode(lang).toLowerCase();
                titleIsLangWithDisposition =
                        titleMatchesLanguageDisposition(lowerTitle, lowerLangName, lowerDisp) ||
                        titleMatchesLanguageDisposition(lowerTitle, lowerEngLang, lowerDisp) ||
                        titleMatchesLanguageDisposition(lowerTitle, lang.toLowerCase(), lowerDisp);
            }

            // 5. Check for "SDH" tag redundancy
            boolean titleIsTag = lowerTitle.equals("sdh");

            if (titleIsLang || titleIsLangWithDisposition || titleIsTag) title = ""; // Let Language or Disposition be primary instead of redundant title
        }

        String primary = "";

        // 1. Primary Label Selection: Title > Language > Disposition > Format
        if (!title.isEmpty()) {
            primary = capitalizeFirstLetter(title);
        } else if (langName != null && !langName.isEmpty()) {
            primary = langName.startsWith("s_") ? langName : capitalizeFirstLetter(langName);
        } else if (dispLabel != null && !dispLabel.isEmpty()) {
            primary = capitalizeFirstLetter(dispLabel);
        } else {
            primary = (format != null && !format.isEmpty()) ? format : "";
        }

        if (primary.isEmpty()) {
            return unknownTrackName;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(primary);

        // 2. Secondary Info Construction
        java.util.ArrayList<String> secondary = new java.util.ArrayList<>();
        String lowerPrimary = primary.toLowerCase();

        // Add Language if not redundant with Primary
        if (langName != null && !langName.isEmpty() && !primary.equalsIgnoreCase(langName)) {
            String lowerLangEng = getEnglishLanguageNameForLetterCode(lang).toLowerCase();
            String lang1 = getISO6391ForLetterCode(lang).toLowerCase();
            String lang3 = getISO6393ForLetterCode(lang).toLowerCase();

            boolean redundant = lowerPrimary.contains(langName.toLowerCase()) ||
                               (!lowerLangEng.isEmpty() && lowerPrimary.contains(lowerLangEng)) ||
                               lowerPrimary.equals(lang1) ||
                               lowerPrimary.equals(lang3);

            if (!redundant) {
                String l = langName.startsWith("s_") ? langName : capitalizeFirstLetter(langName);
                secondary.add(l);
            }
        }

        // Add Disposition if not redundant with Primary
        if (dispLabel != null && !dispLabel.isEmpty() && !primary.equalsIgnoreCase(dispLabel)) {
            String lowerDisp = dispLabel.toLowerCase();
            boolean redundant = lowerPrimary.contains(lowerDisp) ||
                               (isDubDisposition(disposition) && langName != null && !langName.isEmpty()) ||
                               (lowerDisp.contains("malentendants") && lowerPrimary.contains("(sdh)"));
            if (!redundant) {
                secondary.add(capitalizeFirstLetter(dispLabel));
            }
        }

        // Add Format if not redundant with Primary
        if (format != null && !format.isEmpty() && !primary.equalsIgnoreCase(format)) {
            if (!lowerPrimary.contains(format.toLowerCase())) {
                secondary.add(format);
            }
        }

        // 3. Final Formatting
        if (secondary.isEmpty()) {
            return sb.toString();
        }

        StringBuilder sec = new StringBuilder();
        for (int i = 0; i < secondary.size(); i++) {
            if (i > 0) sec.append(" ");
            if (i == 0) sec.append(secondary.get(i));
            else sec.append("(").append(secondary.get(i)).append(")");
        }
        String secStr = sec.toString();
        boolean formatOnlySecondary = secondary.size() == 1 &&
                format != null &&
                !format.isEmpty() &&
                secondary.get(0).equals(format);
        boolean useDashSeparator = !formatOnlySecondary;

        if (titleFirst) { // Audio
            if (useDashSeparator) {
                return sb.append(" - ").append(secStr).toString();
            }
            return sb.append(" (").append(secStr).append(")").toString();
        } else { // Subtitles
            if (useDashSeparator) {
                return sb.append(" - <small>").append(secStr).append("</small>").toString();
            }
            return sb.append(" <small>(").append(secStr).append(")</small>").toString();
        }
    }

    public static String generateTrackName(String string, String lang, String format, int disposition, boolean titleFirst, String dispLabel, String unknownTrackName) {
        String langName = (lang != null && !lang.isEmpty()) ? getLanguageNameForLetterCode(lang) : "";
        return generateTrackName(string, lang, langName, format, disposition, titleFirst, dispLabel, unknownTrackName);
    }

    private static boolean titleMatchesLanguageDisposition(String lowerTitle, String lowerLanguage, String lowerDisposition) {
        if (lowerLanguage == null || lowerLanguage.isEmpty() || lowerDisposition == null || lowerDisposition.isEmpty()) return false;
        if (lowerTitle.equals(lowerLanguage + " (" + lowerDisposition + ")")) return true;
        if (lowerTitle.equals(lowerLanguage + " " + lowerDisposition)) return true;
        if (lowerTitle.equals(lowerLanguage + " - " + lowerDisposition)) return true;
        return lowerDisposition.contains("malentendants") && lowerTitle.equals(lowerLanguage + " (sdh)");
    }

    private static boolean isDubDisposition(int disposition) {
        return (disposition & 0x0002) != 0;
    }

    public static String generateTrackName(String string, String lang, String format, boolean titleFirst) {
        // generate track name as "title (language)" from lang XYZ or XY letter code
        // avoid name=" " with starting space seen in Modern Family show
        String cleanString;
        if (string == null) cleanString = "";
        else cleanString = removeStartingSpacesAndSurroundingParenthesis(string);
        String language =  "";
        String result = ""; // will be interpreted by Video as R.string.unknown_track_name
        if (lang != null && ! lang.isEmpty()) {
            if (lang.equals("und") || lang.equals("Unknown")) language = ""; // und = undefined
            else language = ISO639codes.getLanguageNameForLetterCode(lang);
        }
        if (titleFirst) {
            if (!cleanString.isEmpty()) {
                result = capitalizeFirstLetter(cleanString);
                if (language != null && !language.isEmpty()) {
                    result += " (" + language + ")";
                }
            } else {
                if (language != null && !language.isEmpty()) {
                    result = capitalizeFirstLetter(language);
                } else {
                    result = ""; // will be interpreted by Video as R.string.unknown_track_name
                }
            }
        } else {
            if (language != null && !language.isEmpty()) {
                result = capitalizeFirstLetter(language);
                if (format != null && !format.isEmpty()) {
                    result += "<small> (" + format + ")</small>";
                }
                if (!cleanString.isEmpty()) {
                    result += "<small>: " + cleanString + "</small>";
                }
            } else {
                if (!cleanString.isEmpty()) {
                    result = "<small>" + cleanString + "</small>";
                }
                if (format != null && !format.isEmpty()) {
                    if (result.isEmpty()) result = format;
                    else result += "<small> (" + format + ")</small>";
                }
            }
        }
        if (log.isDebugEnabled()) log.debug("generateTrackName: input={}, lang={}, format{} -> cleanString={}, language={} -> result={}", string, lang, format, cleanString, language,result);
        return result;
    }

}
