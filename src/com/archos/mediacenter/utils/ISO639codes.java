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
        missingISO6391ToISO6393.put("zh-cn", "s_chinese"); // Chinese (simplified) -> Chinese in strings.xml
        missingISO6391ToISO6393.put("zh-tw", "s_traditional_chinese"); // Chinese (traditional)
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

        missingISO6391ToISO6393.put("cn", "yue"); // take yue = Cantonese (cn is used for tmdb)
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
            log.error("getLanguageNameForLetterCode: Invalid code {}", code);
            return code;
        }
    }

    static public String getLanguageNameFor3LetterCode(String code) {
        // handles ISO 639-3 and ISO 639-2b (e.g. fra/fre, deu/ger)
        Locale locale = new Locale(code);
        String languageName = locale.getDisplayLanguage();
        if (languageName.equals(code)) {
            // it has not been found thus perhaps it is ISO 639-2b and conversion is needed
            String iso6393Code = convertIso6392bToIso6393(code);
            if (iso6393Code != null) {
                locale = new Locale(iso6393Code);
                languageName = locale.getDisplayLanguage();
            } else {
                // there is something missing make it obvious and fallback to original 3 letter code
                languageName = code;
                log.error("getLanguageNameFor3LetterCode: No language name found for code {}", code);
            }
        }
        return languageName;
    }

    static public String getLanguageNameFor2LetterCode(String code) {
        // handles ISO 639-1 with exceptions
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
        // TODO: not working "eng" returns "eng" instead of "en"
        String result = "";
        if (code == null) {
            log.error("getISO6391ForLetterCode: null code!");
            return result;
        }
        if (code.length() == 2) result = (new Locale(code)).getLanguage();
        if (code.length() == 3) result = convertIso6392bToIso6393(code);
        if (result.length() == 3) result = convertISO6393ToISO6391(result);
        if (result.length() == 2) {
            log.debug("getISO6391ForLetterCode: code={} result={}", code, result);
            return result;
        } else {
            log.error("getISO6391ForLetterCode: Invalid code {}", code);
            return "";
        }
    }

    static public boolean is3letterCode(String code) {
        String result = getLanguageNameFor3LetterCode(code);
        return !code.equals(result);
    }

    static public boolean isletterCode(String code) {
        String result = getLanguageNameForLetterCode(code);
        log.debug("isletterCode: code={} result={}", code, result);
        return !code.equals(result);
    }

    static public String convertISO6391ToISO6393(String code) {
        String result = missingISO6391ToISO6393.get(code);
        if (result == null) {
            Locale locale = new Locale(code);
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
            Locale locale = new Locale(code);
            try {
                result = locale.getISO3Language();
            } catch (MissingResourceException e) {
                log.error("convertISO6391ToISO6393: No ISO3 found for code {}", code);
                result = code;
            }
        }
        return convertIso6393ToIso6392b(result);
    }

    static public String convertISO6393ToISO6391(String code) {
        // TODO: not working "eng" returns "eng" instead of "en"
        String result = missingISO6393ToISO6391.get(code);
        if (result == null) {
            Locale locale = new Locale(code);
            try {
                result = locale.getLanguage();
            } catch (MissingResourceException e) {
                log.error("convertISO6393ToISO6391: No ISO1 found for code {}", code);
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

    public static String convertISO6393ToLanguageName(String iso6393Code) {
        Locale locale = new Locale(iso6393Code);
        return locale.getDisplayLanguage();
    }

    public static String convertISO6391ToLanguageName(String iso6391Code) {
        if (iso6391Code.equals("system"))
            return Locale.getDefault().getDisplayLanguage();
        Locale locale = new Locale(iso6391Code);
        return locale.getDisplayLanguage();
    }

    public static String[] convertISO6391ToLanguageNames(String languageCodes) {
        String[] languageCodeArray = languageCodes.split("\\|");
        String[] languageNames = new String[languageCodeArray.length];

        for (int i = 0; i < languageCodeArray.length; i++) {
            Locale locale = new Locale(languageCodeArray[i]);
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
        String pattern = "(?:^l_([A-Za-z]{2,3}$)|\\(l_([A-Za-z]{2,3})\\)$)"; // 2 or 3 letters code
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(string);
        String languageCode = "";
        if (matcher.find()) {
            String languageCode1 = matcher.group(1);
            String languageCode2 = matcher.group(2);
            log.debug("findLanguageInString: languageCode1=" + languageCode1 + " languageCode2=" + languageCode2);
            if (languageCode1 != null) {
                languageCode = languageCode1;
                if (languageCode1.equals("und") || languageCode1.equals("Unknown")) return null; // und = undefined
            } else if (languageCode2 != null) {
                languageCode = languageCode2;
            }
            return ISO639codes.getLanguageNameForLetterCode(languageCode);
        } else {
            pattern = "^[A-Za-z]{2,3}$"; // 2 or 3 letters code
            regexPattern = Pattern.compile(pattern);
            matcher = regexPattern.matcher(string);
            if (matcher.find()) {
                languageCode = matcher.group();
                log.debug("findLanguageInString: languageCode=" + languageCode);
                if (languageCode.equals("und") || languageCode.equals("Unknown")) return ""; // und = undefined
                return ISO639codes.getLanguageNameForLetterCode(languageCode);
            }
            return null;
        }
    }

    public static String replaceLanguageCodeInString(String string) {
        // treat strings being "l_XYZ" or "l_XY" or "XYZ" or "XY" or "title (l_XYZ)" or "title (l_XY)"
        // and replace it with locale language corresponding to XY or XYZ letter code
        log.debug("replaceLanguageCodeInString: string=" + string);
        if (string == null) return "";
        String pattern = "(?:^l_([A-Za-z]{2,3}$)|\\(l_([A-Za-z]{2,3})\\)$)"; // 2 or 3 letters code
        String languageCode = "";
        Pattern regexPattern = Pattern.compile(pattern);
        Matcher matcher = regexPattern.matcher(string);
        if (matcher.find()) {
            String languageCode1 = matcher.group(1);
            String languageCode2 = matcher.group(2);
            log.debug("replaceLanguageCodeInString: languageCode1=" + languageCode1 + " languageCode2=" + languageCode2);
            if (languageCode1 != null) {
                languageCode = languageCode1;
                if (languageCode1.equals("und") || languageCode1.equals("Unknown")) return ""; // und = undefined
                return capitalizeFirstLetter(string.replaceAll(pattern, ISO639codes.getLanguageNameForLetterCode(languageCode)));
            } else if (languageCode2 != null) {
                languageCode = languageCode2;
                return string.replaceAll(pattern, "(" + ISO639codes.getLanguageNameForLetterCode(languageCode) + ")");
            }
            return string; // this never happens?
        } else {
            // could be that it is a 2|3 letter code alone: treat this case too
            pattern = "^[A-Za-z]{2,3}$"; // 2 or 3 letters code
            regexPattern = Pattern.compile(pattern);
            matcher = regexPattern.matcher(string);
            if (matcher.find()) {
                languageCode = matcher.group();
                log.debug("replaceLanguageCodeInString: languageCode=" + languageCode);
                if (languageCode.equals("und") || languageCode.equals("Unknown")) return ""; // und = undefined
                return capitalizeFirstLetter(string.replaceAll(pattern, ISO639codes.getLanguageNameForLetterCode(languageCode)));
            }
            log.error("replaceLanguageCodeInString: no languageCode in " + string);
            return string;
        }
    }

}
