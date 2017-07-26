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

package com.archos.mediaprovider.video;

import android.content.ContentValues;

import com.archos.mediaprovider.video.VideoStore.Video.VideoColumns;
import com.archos.mediascraper.preprocess.ParseUtils;

import java.util.HashMap;
import java.util.Map;

public class VideoNameProcessor {
    /* No space after "anaglyph" in order to match "anaglyphe" also */
    private static final String[] STRING_LIST_3D_ANAGLYPH = {
            " anaglyph"
    };
    /* No space after "top[ ]bot" in order to match "top[ ]bottom" also */
    private static final String[] STRING_LIST_3D_TB = {
            " tb ", " htb ", " top bot", " topbot", " tab ", " htab "
    };
    private static final String[] STRING_LIST_3D_SBS = {
            " sbs ", " hsbs ", " side by side ", " sidebyside "
    };
    private static final String[] STRING_LIST_3D = {
            " 3d "
    };
    private static final String[] STRING_LIST_720P = {" 720p "};
    private static final String[] STRING_LIST_1080P = {" 1080p "};
    private static final String[] STRING_LIST_4K = {" 2160p ", " 4K "};




    private static final String[] STRING_LIST_H264 = {" h264 ", " x264 ", " x.264 ", " h.264 "};
    private static final String[] STRING_LIST_HEVC = {" hevc "," h265 ", " x265 ", " x.265 ", " h.265 "};
    private static final String[] STRING_LIST_DIVX = {" divx "," xvid "};
    private static final String[] STRING_LIST_MPEG2 = {};
    private static final String[] STRING_LIST_MPEG1 = {};
    private static final String[] STRING_LIST_WMV7 = {};
    private static final String[] STRING_LIST_WMV8 = {};
    private static final String[] STRING_LIST_WMV9 = {};
    private static final String[] STRING_LIST_VC1 = {};
    private static final String[] STRING_LIST_VP6 = {};
    private static final String[] STRING_LIST_VP7 = {};
    private static final String[] STRING_LIST_VP8 = {};
    private static final String[] STRING_LIST_THEORA = {};
    private static final String[] STRING_LIST_SCREEN_VIDEO = {};
    private static final String[] STRING_LIST_H263 = {};
    private static final String[] STRING_LIST_MSVC = {};





    //link between string parsing and video format given by AVP metadata retriever
    static final Map<String[] , String> VIDEO_FORMAT_MAP = new HashMap<String[] , String>() {{
        put(STRING_LIST_MPEG2,	"MPEG-2");
        put(STRING_LIST_MPEG1,	"MPEG-1");
        put(STRING_LIST_H264,	"H.264");
        put(STRING_LIST_HEVC,	"HEVC/H.265");
        put(STRING_LIST_WMV7,	"WMV7");
        put(STRING_LIST_WMV8,	"WMV8");
        put(STRING_LIST_WMV9,	"WMV9");
        put(STRING_LIST_VC1,	"VC1");
        put(STRING_LIST_VP6,	"VP6");
        put(STRING_LIST_VP7,	"VP7");
        put(STRING_LIST_VP8,	"VP8");
        put(STRING_LIST_THEORA, "Theora");
        put(STRING_LIST_SCREEN_VIDEO,	"Screen video");
        put(STRING_LIST_H263,	"H263");
        put(STRING_LIST_MSVC,	"MSVC");
    }};


    private static final String[] STRING_LIST_PCM = {" pcm "};
    private static final String[] STRING_LIST_LAW = {" A-law"};
    private static final String[] STRING_LIST_MULAW ={" u-law "};
    private static final String[] STRING_LIST_IMA = {" adpcm "};
    private static final String[] STRING_LIST_IMA_QT = {" ima_qt "};
    private static final String[] STRING_LIST_MPEGLAYER3 = {" mp3 "};
    private static final String[] STRING_LIST_MPEG = {" mp2 "};
    private static final String[] STRING_LIST_MSAUDIO2 = {"wma"};
    private static final String[] STRING_LIST_AAC = {" aac "};
    private static final String[] STRING_LIST_AAC_LATM = {" aac_latm "};
    private static final String[] STRING_LIST_AC3 = {" ac3 "};
    private static final String[] STRING_LIST_DTS = {" dts "};
    private static final String[] STRING_LIST_ALAC = {"alac"};
    private static final String[] STRING_LIST_COOK	= {"RealAudio COOK"};
    private static final String[] STRING_LIST_NELLY_MOSER ={"NellyMoser"};
    private static final String[] STRING_LIST_FLV_ADPCM = {"Flash ADPCM"};
    private static final String[] STRING_LIST_MS_ADPCM = {"MS-ADPCM"};
    private static final String[] STRING_LIST_MSAUDIO1 = {"WMA v1"};
    private static final String[] STRING_LIST_MSAUDIO3 = {"WMA-Pro"};
    private static final String[] STRING_LIST_MSAUDIO_SPEECH = {"WMA-Voice"};
    private static final String[] STRING_LIST_MSAUDIO_LOSSLESS = {"WMA-Lossless"};
    private static final String[] STRING_LIST_VOICEAGE_AMR = {"AMR"};
    private static final String[] STRING_LIST_VOICEAGE_AMR_WB = {"AMR-WB"};
    private static final String[] STRING_LIST_FLAC = {" flac "};
    private static final String[] STRING_LIST_OGG1 ={"Vorbis 1"};
    private static final String[] STRING_LIST_OGG2 = {"Vorbis 2"};
    private static final String[] STRING_LIST_OGG3 =	{"Vorbis 3"};
    private static final String[] STRING_LIST_OGG1_PLUS = {"Vorbis 1+"};
    private static final String[] STRING_LIST_OGG2_PLUS = {"Vorbis 2+"};
    private static final String[] STRING_LIST_OGG3_PLUS = {"Vorbis 3+"};
    private static final String[] STRING_LIST_WAVPACK = {"Wavpack"};
    private static final String[] STRING_LIST_TTA = {"TTA True Audio"};
    private static final String[] STRING_LIST_ON2_AVC_AUDIO = {"ON2 AVC-Audio"};
    private static final String[] STRING_LIST_TRUEHD= {"TrueHD"};
    private static final String[] STRING_LIST_EAC3=	{" eac "};
    static final Map<String[] , String> AUDIO_FORMAT_MAP = new HashMap<String[] , String>() {{

        put(STRING_LIST_PCM,"PCM");
        put(STRING_LIST_LAW,"A-law");
        put(STRING_LIST_MULAW,"u-law");
        put(STRING_LIST_IMA,"ADPCM");
        put(STRING_LIST_IMA_QT,"IMA-QT");
        put(STRING_LIST_MPEGLAYER3,"MP3");
        put(STRING_LIST_MPEG,"MP2");
        put(STRING_LIST_MSAUDIO2,"WMA");
        put(STRING_LIST_AAC,"AAC");
        put(STRING_LIST_AAC_LATM,"AAC-LATM");
        put(STRING_LIST_AC3,"AC3");
        put(STRING_LIST_DTS,"Digital");
        put(STRING_LIST_ALAC,"ALAC");
        put(STRING_LIST_COOK,"RealAudio COOK");
        put(STRING_LIST_NELLY_MOSER,"NellyMoser");
        put(STRING_LIST_FLV_ADPCM,"Flash ADPCM");
        put(STRING_LIST_MS_ADPCM,"MS-ADPCM");
        put(STRING_LIST_MSAUDIO1,"WMA v1");
        put(STRING_LIST_MSAUDIO3,"WMA-Pro");
        put(STRING_LIST_MSAUDIO_SPEECH, "WMA-Voice");
        put(STRING_LIST_MSAUDIO_LOSSLESS,"WMA-Lossless");
        put(STRING_LIST_VOICEAGE_AMR, "AMR");
        put(STRING_LIST_VOICEAGE_AMR_WB, "AMR-WB");
        put(STRING_LIST_FLAC, "FLAC");
        put(STRING_LIST_OGG1, "Vorbis 1");
        put(STRING_LIST_OGG2, "Vorbis 2");
        put(STRING_LIST_OGG3, "Vorbis 3");
        put(STRING_LIST_OGG1_PLUS, "Vorbis 1+");
        put(STRING_LIST_OGG2_PLUS, "Vorbis 2+");
        put(STRING_LIST_OGG3_PLUS, "Vorbis 3+");
        put(STRING_LIST_WAVPACK, "Wavpack");
        put(STRING_LIST_TTA, "TTA True Audio");
        put(STRING_LIST_ON2_AVC_AUDIO, "ON2 AVC-Audio");
        put(STRING_LIST_TRUEHD,	"TrueHD");
        put(STRING_LIST_EAC3,"EAC3");
    }};
    public static ContentValues extractValuesFromPath(String path) {
        ContentValues values = new ContentValues(2);

        ExtractedInfo info = extractInfoFromPath(path);

        values.put(VideoColumns.ARCHOS_VIDEO_STEREO, Integer.toString(info.stereoType));
        values.put(VideoColumns.ARCHOS_VIDEO_DEFINITION, Integer.toString(info.definition));
        values.put(VideoColumns.ARCHOS_GUESSED_AUDIO_FORMAT, info.audioFormat);
        values.put(VideoColumns.ARCHOS_GUESSED_VIDEO_FORMAT, info.videoFormat);

        return values;
    }

    public static ExtractedInfo extractInfoFromPath(String path) {
        String name = path.substring(path.lastIndexOf("/"));
        ExtractedInfo info = new ExtractedInfo();

        /* Replace all whitespace & punctuation with a single space */
        name = ParseUtils.removeInnerAndOutterSeparatorJunk(name);
        name = name + " ";

        /* Find out if it is a 3D video (TB first, then SBS, then anaglyph, then 3D) */
        if (stringContainsOneOf(name, STRING_LIST_3D_TB)) {
            info.stereoType = VideoColumns.ARCHOS_STEREO_3D_TB;
        } else if (stringContainsOneOf(name, STRING_LIST_3D_SBS)) {
            info.stereoType = VideoColumns.ARCHOS_STEREO_3D_SBS;
        } else if (stringContainsOneOf(name, STRING_LIST_3D_ANAGLYPH)) {
            info.stereoType = VideoColumns.ARCHOS_STEREO_3D_ANAGLYPH;
        } else if (stringContainsOneOf(name, STRING_LIST_3D)) {
            info.stereoType = VideoColumns.ARCHOS_STEREO_3D_UNKNOWN;
        } else {
            info.stereoType = VideoColumns.ARCHOS_STEREO_2D;
        }

        /* Determine the video definition (checking for 1080p first) */
        if (stringContainsOneOf(name, STRING_LIST_1080P)) {
            info.definition = VideoColumns.ARCHOS_DEFINITION_1080P;
        } else if (stringContainsOneOf(name, STRING_LIST_720P)) {
            info.definition = VideoColumns.ARCHOS_DEFINITION_720P;
        }
        // Checking 4K after 1080p/720p because a lot of videos are like "...Remastered.in.4K.1080p.x264..."
        else if (stringContainsOneOf(name, STRING_LIST_4K)) {
            info.definition = VideoColumns.ARCHOS_DEFINITION_4K;
        }
        // Not checking SD because the SD tag is not frequent in filenames
        else {
            info.definition = VideoColumns.ARCHOS_DEFINITION_UNKNOWN;
        }

        //try to detect video format (hevc, h264, etc)
        if (stringContainsOneOf(name, STRING_LIST_1080P)) {
            info.definition = VideoColumns.ARCHOS_DEFINITION_1080P;
        }
        for(Map.Entry<String[], String> entry :VIDEO_FORMAT_MAP.entrySet()){
            if (stringContainsOneOf(name, entry.getKey())) {
                info.videoFormat = entry.getValue();
            }
        }

        for(Map.Entry<String[], String> entry :AUDIO_FORMAT_MAP.entrySet()){
            if (stringContainsOneOf(name, entry.getKey())) {
                info.audioFormat = entry.getValue();
            }
        }

        return info;
    }

    private static boolean stringContainsOneOf(String str, String[] list) {
        String strLower = str.toLowerCase();

        for(String e : list) {
            if (strLower.contains(e.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    public static class ExtractedInfo {
        public int stereoType;
        public int definition;
        public String videoFormat;
        public String audioFormat;
    }
}
