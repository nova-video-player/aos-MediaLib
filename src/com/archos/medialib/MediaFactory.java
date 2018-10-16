// Copyright 2017 Archos SA
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

package com.archos.medialib;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;

import com.archos.mediacenter.utils.MediaUtils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

public class MediaFactory {
    private static final String TAG = "MediaFactory";

    private static final String KEY_FORCE_SW = "force_software_decoding";
    private static final String KEY_DEC_CHOICE = "dec_choice";
    private static final String KEY_CODEPAGE = "codepage";

    private static final Method AUDIO_MANAGER_GET_PROPERTY_METHOD;
    private static final String PROPERTY_OUTPUT_SAMPLE_RATE = "android.media.property.OUTPUT_SAMPLE_RATE";

    private static final HashMap<String, Integer> LANG_CODEPAGE_MAP = new HashMap<String, Integer>();
    private static final int DEFAULT_CODEPAGE = 1252; // West European (Default)
    static {
        LANG_CODEPAGE_MAP.put("ja_JP", 932);    // Japanese
        LANG_CODEPAGE_MAP.put("zh_CN", 936);    // Simplified Chinese
        LANG_CODEPAGE_MAP.put("ko_KR", 949);    // Korean
        LANG_CODEPAGE_MAP.put("zh_TW", 950);    // Traditional Chinese
        LANG_CODEPAGE_MAP.put("sl_SI", 1250);   // Slovenian
        LANG_CODEPAGE_MAP.put("hr_HR", 1250);   // Croatian
        LANG_CODEPAGE_MAP.put("sk_SK", 1250);   // Slovak
        LANG_CODEPAGE_MAP.put("ro_RO", 1250);   // Romanian
        LANG_CODEPAGE_MAP.put("hu_HU", 1250);   // Hungarian
        LANG_CODEPAGE_MAP.put("pl_PL", 1250);   // East European (Polish)
        LANG_CODEPAGE_MAP.put("cs_CZ", 1250);   // East European (Czech)
        LANG_CODEPAGE_MAP.put("mk_MK", 1251);   // Macedonia
        LANG_CODEPAGE_MAP.put("kk_KZ", 1251);   // Kazakh
        LANG_CODEPAGE_MAP.put("az_AZ", 1251);   // Azerbaijan
        LANG_CODEPAGE_MAP.put("bg_BG", 1251);   // Russian (Bulgarian)
        LANG_CODEPAGE_MAP.put("ru_RU", 1251);   // Russian
        LANG_CODEPAGE_MAP.put("uz_UZ", 1251);   // Uzbek
        LANG_CODEPAGE_MAP.put("sr_RS", 1251);   // Serbian
        LANG_CODEPAGE_MAP.put("uk_UA", 1251);   // Ukrainian
        LANG_CODEPAGE_MAP.put("el_GR", 1253);   // Greek
        LANG_CODEPAGE_MAP.put("tr_TR", 1254);   // Turkish
        LANG_CODEPAGE_MAP.put("iw_IL", 1255);   // Hebrew
        LANG_CODEPAGE_MAP.put("ar_EG", 1256);   // Arabic
        LANG_CODEPAGE_MAP.put("fa_IR", 1256);   // Persian
        LANG_CODEPAGE_MAP.put("et_EE", 1257);   // Estonia
        LANG_CODEPAGE_MAP.put("lv_LV", 1257);   // Latvian
        LANG_CODEPAGE_MAP.put("lt_LT", 1257);   // Lithuanian
        LANG_CODEPAGE_MAP.put("vi_VN", 1258);   // Vietnamese

        Method method = null;
        if (Build.VERSION.SDK_INT >= 17) {
            try {
                method = AudioManager.class.getMethod("getProperty", String.class);
            } catch (NoSuchMethodException e) {
            }
        }
        AUDIO_MANAGER_GET_PROPERTY_METHOD = method;
    }

    public static int getCodepage() {
        Integer cp = LANG_CODEPAGE_MAP.get(Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry());
        return cp == null ? DEFAULT_CODEPAGE : cp.intValue();
    }

    private static boolean preInit(Context ctx, boolean isPlayer, boolean forceSoftwareDecoding) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String subtitlePath = MediaUtils.getSubsDir(ctx).getPath()+"/";
        int decoder, codepage = 0;

        String str = prefs.getString(KEY_DEC_CHOICE, null);

        if (str != null&&!forceSoftwareDecoding) {
            decoder = Integer.parseInt(str);
            if (decoder == -1) {
                // user wants Android MediaPlayer for playback
                if (isPlayer) {

                    // so return false here in order to fallback to android player
                    return false;
                } else {
                    // but set default decoder for media retriver
                    decoder = LibAvos.MP_DECODER_ANY;
                }
            }
        } else {
            decoder = (prefs.getBoolean(KEY_FORCE_SW, false)||forceSoftwareDecoding) ? LibAvos.MP_DECODER_SW : LibAvos.MP_DECODER_ANY;
        }

        try {
            codepage = Integer.parseInt(prefs.getString(KEY_CODEPAGE, "0"));
        } catch (NumberFormatException e) {}
        if (codepage == 0)
            codepage = getCodepage();

        int outputSampleRate = -1;
        if (AUDIO_MANAGER_GET_PROPERTY_METHOD != null) {
            AudioManager am = (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
            try {
                String sampleRate = (String) AUDIO_MANAGER_GET_PROPERTY_METHOD.invoke(am, PROPERTY_OUTPUT_SAMPLE_RATE);
                if (sampleRate != null)
                    outputSampleRate = Integer.parseInt(sampleRate);
            } catch (IllegalArgumentException e) {
            } catch (IllegalAccessException e) {
            } catch (InvocationTargetException e) {
            }
        }
        LibAvos.init(ctx);
        if (LibAvos.isAvailable()) {
            LibAvos.setDecoder(decoder);
            LibAvos.setCodepage(codepage);
            LibAvos.setSubtitlePath(subtitlePath);
            if (outputSampleRate != -1)
                LibAvos.setOutputSampleRate(outputSampleRate);
            return true;
        }
        return false;
    }

    public static IMediaPlayer createPlayer(Context ctx, boolean useAvosPlayer, boolean forceSoftwareDecoding) {
        if (useAvosPlayer && preInit(ctx, true, forceSoftwareDecoding)) {
            Log.d(TAG, "new AvosMediaPlayer");
            return new AvosMediaPlayer();
        } else {
            Log.d(TAG, "new AndroidMediaPlayer");
            return new AndroidMediaPlayer(ctx);
        }
    }

    public static IMediaMetadataRetriever createMetadataRetriever(Context ctx) {
        if (preInit(ctx, false, false)) {
            Log.d(TAG, "new AvosMediaMetadataRetriever");
            return new AvosMediaMetadataRetriever();
        } else {
            Log.d(TAG, "new AndroidMediaMetadataRetriever");
            return new AndroidMediaMetadataRetriever();
        }
    }
}
