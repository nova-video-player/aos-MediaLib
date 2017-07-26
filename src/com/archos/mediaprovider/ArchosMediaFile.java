/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.archos.mediaprovider;

import com.archos.filecorelibrary.MetaFile;
import com.archos.filecorelibrary.MetaFile2;

import java.util.HashMap;

/**
 * MediaScanner helper class. Archos Specific version of
 * MediaFile.java in Framework.
 */
public class ArchosMediaFile {

    // Audio file types
    public static final int FILE_TYPE_MP3     = 1;
    public static final int FILE_TYPE_M4A     = 2;
    public static final int FILE_TYPE_WAV     = 3;
    public static final int FILE_TYPE_AMR     = 4;
    public static final int FILE_TYPE_AWB     = 5;
    public static final int FILE_TYPE_WMA     = 6;
    public static final int FILE_TYPE_OGG     = 7;
    public static final int FILE_TYPE_AAC     = 8;
    public static final int FILE_TYPE_MKA     = 9;
    public static final int FILE_TYPE_FLAC    = 10;
    private static final int FIRST_AUDIO_FILE_TYPE = FILE_TYPE_MP3;
    private static final int LAST_AUDIO_FILE_TYPE = FILE_TYPE_FLAC;

    // MIDI file types
    public static final int FILE_TYPE_MID     = 11;
    public static final int FILE_TYPE_SMF     = 12;
    public static final int FILE_TYPE_IMY     = 13;
    private static final int FIRST_MIDI_FILE_TYPE = FILE_TYPE_MID;
    private static final int LAST_MIDI_FILE_TYPE = FILE_TYPE_IMY;
   
    // Video file types
    public static final int FILE_TYPE_MP4     = 21;
    public static final int FILE_TYPE_M4V     = 22;
    public static final int FILE_TYPE_3GPP    = 23;
    public static final int FILE_TYPE_3GPP2   = 24;
    public static final int FILE_TYPE_WMV     = 25;
    public static final int FILE_TYPE_ASF     = 26;
    public static final int FILE_TYPE_MKV     = 27;
    public static final int FILE_TYPE_MP2TS   = 28;
    public static final int FILE_TYPE_AVI     = 29;
    public static final int FILE_TYPE_WEBM    = 30;
    private static final int FIRST_VIDEO_FILE_TYPE = FILE_TYPE_MP4;
    private static final int LAST_VIDEO_FILE_TYPE = FILE_TYPE_WEBM;
    // DO NOT ADD HERE, images start at 31

    // More video file types
    public static final int FILE_TYPE_MP2PS   = 200;
    public static final int FILE_TYPE_WTV     = 201;
    public static final int FILE_TYPE_OGV     = 202;
    private static final int FIRST_VIDEO_FILE_TYPE2 = FILE_TYPE_MP2PS;
    private static final int LAST_VIDEO_FILE_TYPE2 = FILE_TYPE_OGV;

    // Image file types
    public static final int FILE_TYPE_JPEG    = 31;
    public static final int FILE_TYPE_GIF     = 32;
    public static final int FILE_TYPE_PNG     = 33;
    public static final int FILE_TYPE_BMP     = 34;
    public static final int FILE_TYPE_WBMP    = 35;
    public static final int FILE_TYPE_WEBP    = 36;
    private static final int FIRST_IMAGE_FILE_TYPE = FILE_TYPE_JPEG;
    private static final int LAST_IMAGE_FILE_TYPE = FILE_TYPE_WEBP;
   
    // Playlist file types
    public static final int FILE_TYPE_M3U      = 41;
    public static final int FILE_TYPE_PLS      = 42;
    public static final int FILE_TYPE_WPL      = 43;
    public static final int FILE_TYPE_HTTPLIVE = 44;

    private static final int FIRST_PLAYLIST_FILE_TYPE = FILE_TYPE_M3U;
    private static final int LAST_PLAYLIST_FILE_TYPE = FILE_TYPE_HTTPLIVE;

    // Drm file types
    public static final int FILE_TYPE_FL      = 51;
    private static final int FIRST_DRM_FILE_TYPE = FILE_TYPE_FL;
    private static final int LAST_DRM_FILE_TYPE = FILE_TYPE_FL;

    // Other popular file types
    public static final int FILE_TYPE_TEXT          = 100;
    public static final int FILE_TYPE_HTML          = 101;
    public static final int FILE_TYPE_PDF           = 102;
    public static final int FILE_TYPE_XML           = 103;
    public static final int FILE_TYPE_MS_WORD       = 104;
    public static final int FILE_TYPE_MS_EXCEL      = 105;
    public static final int FILE_TYPE_MS_POWERPOINT = 106;
    public static final int FILE_TYPE_ZIP           = 107;

    // Archos file types
    public static final int FILE_TYPE_WAVPACK       = 1001;
    public static final int FILE_TYPE_TTA           = 1002;
    public static final int FILE_TYPE_3GPPA         = 1003;
    private static final int FIRST_AUDIO_FILE_TYPEA = FILE_TYPE_WAVPACK;
    private static final int LAST_AUDIO_FILE_TYPEA  = FILE_TYPE_3GPPA;

    public static final int FILE_TYPE_FLV           = 1021;
    public static final int FILE_TYPE_RM            = 1022;
    public static final int FILE_TYPE_F4V           = 1023;
    private static final int FIRST_VIDEO_FILE_TYPEA = FILE_TYPE_FLV;
    private static final int LAST_VIDEO_FILE_TYPEA  = FILE_TYPE_F4V;

    public static final int FILE_TYPE_JPS           = 1031;
    public static final int FILE_TYPE_MPO           = 1032;
    private static final int FIRST_IMAGE_FILE_TYPEA = FILE_TYPE_JPS;
    private static final int LAST_IMAGE_FILE_TYPEA  = FILE_TYPE_MPO;

    public static final int FILE_TYPE_AOS           = 1101;

    public static final int FILE_TYPE_SRT           = 1201;
    public static final int FILE_TYPE_SUB           = 1202;
    public static final int FILE_TYPE_IDX           = 1203;
    public static final int FILE_TYPE_SMI           = 1204;
    public static final int FILE_TYPE_ASS           = 1205;
    public static final int FILE_TYPE_SSA           = 1206;
    public static final int FILE_TYPE_SRR           = 1207;
    public static final int FILE_TYPE_MPL           = 1208;
    public static final int FILE_TYPE_TXT           = 1209;

    // keep in sync with com.archos.mediacenter.video.utils.VideoUtils.SUBTITLES_ARRAY
    private static final int FIRST_SUBTITLE_FILE_TYPE = FILE_TYPE_SRT;
    private static final int LAST_SUBTITLE_FILE_TYPE = FILE_TYPE_TXT;

    private static final int FILE_TYPE_ANY_VIDEO = 1300;

    public static class MediaFileType {
        public final int fileType;
        public final String mimeType;
        
        MediaFileType(int fileType, String mimeType) {
            this.fileType = fileType;
            this.mimeType = mimeType;
        }
    }
    
    private static HashMap<String, MediaFileType> sFileTypeMap
            = new HashMap<String, MediaFileType>();

    static void addFileType(String extension, int fileType, String mimeType) {
        sFileTypeMap.put(extension, new MediaFileType(fileType, mimeType));
    }

    static {
        addFileType("MP3", FILE_TYPE_MP3, "audio/mpeg");
        addFileType("MP2", FILE_TYPE_MP3, "audio/mpeg");
        addFileType("MPGA", FILE_TYPE_MP3, "audio/mpeg");

        addFileType("M4A", FILE_TYPE_M4A, "audio/mp4");
        addFileType("WAV", FILE_TYPE_WAV, "audio/x-wav");
        addFileType("AMR", FILE_TYPE_AMR, "audio/amr");
        addFileType("AWB", FILE_TYPE_AWB, "audio/amr-wb");

        addFileType("OGG", FILE_TYPE_OGG, "application/ogg");
        addFileType("OGA", FILE_TYPE_OGG, "application/ogg");
        addFileType("AAC", FILE_TYPE_AAC, "audio/aac");
        addFileType("AAC", FILE_TYPE_AAC, "audio/aac-adts");
        addFileType("MKA", FILE_TYPE_MKA, "audio/x-matroska");
 
        addFileType("MID", FILE_TYPE_MID, "audio/midi");
        addFileType("MIDI", FILE_TYPE_MID, "audio/midi");
        addFileType("XMF", FILE_TYPE_MID, "audio/midi");
        addFileType("RTTTL", FILE_TYPE_MID, "audio/midi");
        addFileType("SMF", FILE_TYPE_SMF, "audio/sp-midi");
        addFileType("IMY", FILE_TYPE_IMY, "audio/imelody");
        addFileType("RTX", FILE_TYPE_MID, "audio/midi");
        addFileType("OTA", FILE_TYPE_MID, "audio/midi");
        addFileType("MXMF", FILE_TYPE_MID, "audio/midi");
        
        addFileType("MPEG", FILE_TYPE_MP4, "video/mpeg");
        addFileType("MPG", FILE_TYPE_MP4, "video/mpeg");
        addFileType("MP4", FILE_TYPE_MP4, "video/mp4");
        addFileType("M4V", FILE_TYPE_M4V, "video/mp4");
        addFileType("3GP", FILE_TYPE_3GPP, "video/3gpp");
        addFileType("3GPP", FILE_TYPE_3GPP, "video/3gpp");
        addFileType("3G2", FILE_TYPE_3GPP2, "video/3gpp2");
        addFileType("3GPP2", FILE_TYPE_3GPP2, "video/3gpp2");
        addFileType("MKV", FILE_TYPE_MKV, "video/x-matroska");
        addFileType("WEBM", FILE_TYPE_WEBM, "video/webm");
        addFileType("TS", FILE_TYPE_MP2TS, "video/mp2ts");
        addFileType("AVI", FILE_TYPE_AVI, "video/avi");
//      addFileType("OGV", FILE_TYPE_OGV, "video/ogg");
//      addFileType("OGM", FILE_TYPE_OGV, "video/ogg");

        addFileType("JPG", FILE_TYPE_JPEG, "image/jpeg");
        addFileType("JPEG", FILE_TYPE_JPEG, "image/jpeg");
        addFileType("GIF", FILE_TYPE_GIF, "image/gif");
        addFileType("PNG", FILE_TYPE_PNG, "image/png");
        addFileType("BMP", FILE_TYPE_BMP, "image/x-ms-bmp");
        addFileType("WBMP", FILE_TYPE_WBMP, "image/vnd.wap.wbmp");
        addFileType("WEBP", FILE_TYPE_WEBP, "image/webp");
 
        addFileType("M3U", FILE_TYPE_M3U, "audio/x-mpegurl");
        addFileType("M3U", FILE_TYPE_M3U, "application/x-mpegurl");
        addFileType("PLS", FILE_TYPE_PLS, "audio/x-scpls");
        addFileType("WPL", FILE_TYPE_WPL, "application/vnd.ms-wpl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "application/vnd.apple.mpegurl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "audio/mpegurl");
        addFileType("M3U8", FILE_TYPE_HTTPLIVE, "audio/x-mpegurl");

        addFileType("FL", FILE_TYPE_FL, "application/x-android-drm-fl");

        // addFileType("TXT", FILE_TYPE_TEXT, "text/plain"); // is a potential subtitle
        addFileType("HTM", FILE_TYPE_HTML, "text/html");
        addFileType("HTML", FILE_TYPE_HTML, "text/html");
        addFileType("PDF", FILE_TYPE_PDF, "application/pdf");
        addFileType("DOC", FILE_TYPE_MS_WORD, "application/msword");
        addFileType("XLS", FILE_TYPE_MS_EXCEL, "application/vnd.ms-excel");
        addFileType("PPT", FILE_TYPE_MS_POWERPOINT, "application/mspowerpoint");
        addFileType("FLAC", FILE_TYPE_FLAC, "audio/flac");
        addFileType("ZIP", FILE_TYPE_ZIP, "application/zip");

        addFileType("MPG", FILE_TYPE_MP2PS, "video/mp2p");
        addFileType("MPEG", FILE_TYPE_MP2PS, "video/mp2p");

        addFileType("AOS", FILE_TYPE_AOS, "application/aos-update");

        // extensions handled by avos
        addFileType("AVI", FILE_TYPE_AVI, "video/x-msvideo");
        addFileType("GVI", FILE_TYPE_AVI, "video/avos-gvi");
        addFileType("DIVX", FILE_TYPE_AVI, "video/x-msvideo");

        addFileType("WMV", FILE_TYPE_WMV, "video/asf");
        addFileType("ASF", FILE_TYPE_ASF, "video/asf");

        addFileType("WMA", FILE_TYPE_WMA, "audio/x-ms-wma");

        addFileType("M4B", FILE_TYPE_AAC, "audio/mp4");
        addFileType("WV",  FILE_TYPE_WAVPACK, "audio/x-wavpack");
        addFileType("TTA", FILE_TYPE_TTA, "audio/x-tta");

        addFileType("DVR-MS", FILE_TYPE_WMV, "video/x-ms-wmv");

        addFileType("MOV", FILE_TYPE_MP4, "video/quicktime");
        addFileType("QT", FILE_TYPE_MP4, "video/quicktime");

        //addFileType("MPG", FILE_TYPE_MP2PS, "video/mpeg");
        addFileType("MPE", FILE_TYPE_MP2PS, "video/mpeg");
        //addFileType("MPEG", FILE_TYPE_MP2PS, "video/mpeg");
        addFileType("PS", FILE_TYPE_MP2PS, "video/mp2p");
        addFileType("VOB", FILE_TYPE_MP2PS, "video/mpeg");
        addFileType("VRO", FILE_TYPE_MP2PS, "video/avos-vro");
        addFileType("DVR0", FILE_TYPE_MP2PS, "video/avos-vro");

        addFileType("TS", FILE_TYPE_MP2TS, "video/mp2t");
        addFileType("M2TS", FILE_TYPE_MP2TS, "video/mp2t");
        addFileType("TRP", FILE_TYPE_MP2TS, "video/avos-trp");
        addFileType("MTS", FILE_TYPE_MP2TS, "video/avos-mts");

        addFileType("FLV", FILE_TYPE_FLV, "video/x-flv");
	addFileType("F4V", FILE_TYPE_F4V, "video/x-f4v");

        addFileType("RM", FILE_TYPE_RM, "video/vnd.rn-realvideo");
        addFileType("RMVB", FILE_TYPE_RM, "video/vnd.rn-realvideo");
        addFileType("RA", FILE_TYPE_RM, "video/vnd.rn-realvideo");
        addFileType("RAM", FILE_TYPE_RM, "video/vnd.rn-realvideo");

        addFileType("SRT", FILE_TYPE_SRT, "text/plain");
        addFileType("IDX", FILE_TYPE_IDX, "text/plain");
        addFileType("SUB", FILE_TYPE_SUB, "text/plain");
        addFileType("SMI", FILE_TYPE_SMI, "text/plain");
        addFileType("SSA", FILE_TYPE_SSA, "text/plain");
        addFileType("ASS", FILE_TYPE_ASS, "text/plain");
        addFileType("MPL", FILE_TYPE_MPL, "text/plain");
        addFileType("SRR", FILE_TYPE_SRR, "text/plain");
        addFileType("TXT", FILE_TYPE_TXT, "text/plain");

        addFileType("WTV", FILE_TYPE_WTV, "video/wtv");

        addFileType("264"  , FILE_TYPE_ANY_VIDEO, "video/avos-264");
        addFileType("AC3"  , FILE_TYPE_ANY_VIDEO, "video/avos-ac3");
        addFileType("AMV"  , FILE_TYPE_ANY_VIDEO, "video/avos-amv");
        addFileType("H264" , FILE_TYPE_ANY_VIDEO, "video/avos-h264");
        addFileType("M2V"  , FILE_TYPE_ANY_VIDEO, "video/avos-m2v");
        addFileType("MPG4" , FILE_TYPE_ANY_VIDEO, "video/avos-mpg4");
        addFileType("MPEG4", FILE_TYPE_ANY_VIDEO, "video/avos-mpeg4");
        addFileType("WVX"  , FILE_TYPE_ANY_VIDEO, "video/x-ms-wvx");
        addFileType("WMX"  , FILE_TYPE_ANY_VIDEO, "video/x-ms-wmx");
        addFileType("ASX"  , FILE_TYPE_ANY_VIDEO, "video/x-ms-asf");
    }

    public static boolean isAudioFileType(int fileType) {
        return ((fileType >= FIRST_AUDIO_FILE_TYPE &&
                fileType <= LAST_AUDIO_FILE_TYPE) ||
                (fileType >= FIRST_MIDI_FILE_TYPE &&
                fileType <= LAST_MIDI_FILE_TYPE) ||
                (fileType >= FIRST_AUDIO_FILE_TYPEA &&
                fileType <= LAST_AUDIO_FILE_TYPEA));
    }

    public static boolean isVideoFileType(int fileType) {
        return fileType == FILE_TYPE_ANY_VIDEO ||
                (fileType >= FIRST_VIDEO_FILE_TYPE &&
                fileType <= LAST_VIDEO_FILE_TYPE)
            || (fileType >= FIRST_VIDEO_FILE_TYPE2 &&
                fileType <= LAST_VIDEO_FILE_TYPE2)
            || (fileType >= FIRST_VIDEO_FILE_TYPEA &&
                fileType <= LAST_VIDEO_FILE_TYPEA);
    }

    public static boolean isSubtitleFileType(int fileType) {
        return (fileType >= FIRST_SUBTITLE_FILE_TYPE &&
                fileType <= LAST_SUBTITLE_FILE_TYPE);
    }

    public static boolean isImageFileType(int fileType) {
        return (fileType >= FIRST_IMAGE_FILE_TYPE &&
                fileType <= LAST_IMAGE_FILE_TYPE)
            || (fileType >= FIRST_IMAGE_FILE_TYPEA &&
                fileType <= LAST_IMAGE_FILE_TYPEA);
    }

    public static boolean isPlayListFileType(int fileType) {
        return (fileType >= FIRST_PLAYLIST_FILE_TYPE &&
                fileType <= LAST_PLAYLIST_FILE_TYPE);
    }

    public static boolean isDrmFileType(int fileType) {
        return (fileType >= FIRST_DRM_FILE_TYPE &&
                fileType <= LAST_DRM_FILE_TYPE);
    }

    public static MediaFileType getFileType(String path) {
        if(path==null)
            return null;
        int lastDot = path.lastIndexOf(".");
        return sFileTypeMap.get(path.substring(lastDot + 1).toUpperCase());
    }

    // generates a title based on file name
    public static String getFileTitle(String path) {
        // extract file name after last slash
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash >= 0) {
            lastSlash++;
            if (lastSlash < path.length()) {
                path = path.substring(lastSlash);
            }
        }
        // truncate the file extension (if any)
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            path = path.substring(0, lastDot);
        }
        return path;
    }

    public static String stripFileExtension(String fileName) {
        // truncate the file extension (if any)
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            return fileName.substring(0, lastDot);
        }
        return fileName;
    }

    public static int getSimpleFileType(String path) {
        MediaFileType mediaFileType = getFileType(path);
        return mediaFileType == null ? 0 : mediaFileType.fileType;
    }

    public static String getMimeTypeForFile(String path) {
        MediaFileType mediaFileType = getFileType(path);
        return (mediaFileType == null ? null : mediaFileType.mimeType);
    }

    public static boolean isHiddenFile(MetaFile2 f) {
        // null is hidden
        String name = f != null ? f.getName() : ".";
        return name.startsWith(".");
    }
}
