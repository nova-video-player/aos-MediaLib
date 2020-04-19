package com.archos.medialib;

import android.os.Parcel;

import com.google.android.exoplayer2.Format;

import java.util.Date;
import java.util.HashMap;
import java.util.Set;

import static com.archos.medialib.IMediaPlayer.*;
import static com.archos.medialib.LibAvos.MP_DECODER_EXOPLAYER;

class MetadataDelegate extends MediaMetadata {
    private final Object mMetadata;
    private final MetadataDelegate.Custom mCustom = new MetadataDelegate.Custom();

    MetadataDelegate() {
        mMetadata = null;
    }

    MetadataDelegate(Object data, int videoWidth, int videoHeight, MediaPlayerProxy.Tracks tracks, String[] timedTextLangs) {
        mMetadata = data;
        if (tracks != null) {
            mCustom.addInt(METADATA_KEY_NB_VIDEO_TRACK, tracks.video == null ? 0 : 1);
            mCustom.addInt(METADATA_KEY_NB_AUDIO_TRACK, tracks.audios.length);
            mCustom.addInt(METADATA_KEY_NB_SUBTITLE_TRACK, tracks.timedTexts.length);

            if (tracks.video != null) {
                int gapKey = METADATA_KEY_VIDEO_TRACK;
                mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_WIDTH, videoWidth);
                mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_HEIGHT, videoHeight);
            }

            for (int i = 0; i < tracks.audios.length; ++i) {
                int gapKey = METADATA_KEY_AUDIO_TRACK + (i * METADATA_KEY_AUDIO_TRACK_MAX);
                mCustom.addString(gapKey + METADATA_KEY_AUDIO_TRACK_NAME, tracks.audios[i].lang);
                mCustom.addBoolean(gapKey + METADATA_KEY_AUDIO_TRACK_SUPPORTED, true);
            }
            for (int i = 0; i < tracks.timedTexts.length; ++i) {
                int gapKey = METADATA_KEY_SUBTITLE_TRACK + (i * METADATA_KEY_SUBTITLE_TRACK_MAX);
                String lang = null;
                if (tracks.timedTexts[i].lang.equals("und") && timedTextLangs != null && timedTextLangs.length > i)
                    lang = timedTextLangs[i];
                else
                    lang = tracks.timedTexts[i].lang;
                mCustom.addString(gapKey + METADATA_KEY_SUBTITLE_TRACK_NAME, lang);
            }
        }
    }

    void addExoVideo(Format format) {
        int gapKey = METADATA_KEY_VIDEO_TRACK;
        mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_WIDTH, format.width);
        mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_HEIGHT, format.height);
        mCustom.addString(gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_FORMAT, format.sampleMimeType);
        mCustom.addInt(gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_BIT_RATE, format.bitrate);
        mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_FPS_RATE, (int) (format.frameRate * 1000.f));
        mCustom.addInt(gapKey + METADATA_KEY_VIDEO_TRACK_FPS_SCALE, 1000);
        mCustom.addInt(gapKey + IMediaPlayer.METADATA_KEY_VIDEO_TRACK_DECODER, MP_DECODER_EXOPLAYER);
    }

    void addExoAudio(Format format, int i) {
        int gapKey = METADATA_KEY_AUDIO_TRACK + (i * METADATA_KEY_AUDIO_TRACK_MAX);
        mCustom.addString(gapKey + METADATA_KEY_AUDIO_TRACK_NAME, format.language);
        mCustom.addString(gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_FORMAT, format.sampleMimeType);
        mCustom.addInt(gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_BIT_RATE, format.bitrate);
        mCustom.addInt(gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_SAMPLE_RATE, format.sampleRate);
        String channels;
        switch(format.channelCount) {
            case 8:
                channels = "7.1";
                break;
            case  6:
                channels = "5.1";
                break;
            case 2:
                channels = "Stereo";
                break;
            default:
                channels = Integer.toString(format.channelCount);
        }
        mCustom.addString(gapKey + IMediaPlayer.METADATA_KEY_AUDIO_TRACK_CHANNELS, channels);
        mCustom.addBoolean(gapKey + METADATA_KEY_AUDIO_TRACK_SUPPORTED, true);
    }

    void addExoSubs(Format format, int i) {
        int gapKey = METADATA_KEY_SUBTITLE_TRACK + (i * METADATA_KEY_SUBTITLE_TRACK_MAX);
        mCustom.addString(gapKey + METADATA_KEY_SUBTITLE_TRACK_NAME, format.language);

    }



    void addString(int key, String val) {
        mCustom.addString(key, val);
    }
    void addInt(int key, int val) {
        mCustom.addInt(key, val);
    }
    void addBoolean(int key, boolean val) {
        mCustom.addBoolean(key, val);
    }
    void addLong(int key, long val) {
        mCustom.addLong(key, val);
    }
    void addDouble(int key, double val) {
        mCustom.addDouble(key, val);
    }
    void addByteArray(int key, byte val[]) {
        mCustom.addByteArray(key, val);
    }
    void addDate(int key, Date val) {
        mCustom.addDate(key, val);
    }

    @Override
    public boolean equals(Object o) {
        return mMetadata.equals(o);
    }

    @Override
    public int hashCode() {
        return mMetadata.hashCode();
    }

    @Override
    public boolean parse(Parcel parcel) {
        Class paramsType[] = new Class[1];
        paramsType[0] = Parcel.class;
        try {
            return (Boolean) mMetadata.getClass().getMethod("parse", paramsType).invoke(mMetadata, parcel);
        } catch (Exception e) {
            return false;
        }
        //return mMetadata.parse(parcel);
    }

    @Override
    public String toString() {
        return mMetadata.toString();
    }

    @Override
    public Set<Integer> keySet() {
        Class paramsType[] = new Class[0];
        try {
            return (Set<Integer>) mMetadata.getClass().getMethod("keySet", paramsType).invoke(mMetadata);
        } catch (Exception e) {
            return null;
        }
        //return mMetadata.keySet();
    }

    private boolean isCustom(int key) {
        return key >= IMediaPlayer.METADATA_FIRST_CUSTOM;
    }

    @Override
    public boolean has(int metadataId) {
        if (isCustom(metadataId)) {
            return mCustom.has(metadataId);
        } else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (Boolean) mMetadata.getClass().getMethod("has", paramsType).invoke(mMetadata, metadataId);
            } catch (Exception e) {
                return false;
            }
        }
    }

    @Override
    public String getString(int key) {
        if (isCustom(key)) return mCustom.getString(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (String) mMetadata.getClass().getMethod("getString", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return null;
            }
            //return mMetadata.getString(key);
        }
    }

    @Override
    public int getInt(int key) {
        if (isCustom(key)) return mCustom.getInt(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (Integer) mMetadata.getClass().getMethod("getInt", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return 0;
            }
            //return mMetadata.getInt(key);
        }
    }

    @Override
    public boolean getBoolean(int key) {
        if (isCustom(key)) return mCustom.getBoolean(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (Boolean) mMetadata.getClass().getMethod("getBoolean", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return false;
            }//return mMetadata.getBoolean(key);
        }
    }

    @Override
    public long getLong(int key) {
        if (isCustom(key)) return mCustom.getLong(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (Long) mMetadata.getClass().getMethod("getLong", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return 0L;
            }
            //return mMetadata.getLong(key);
        }
    }

    @Override
    public double getDouble(int key) {
        if (isCustom(key)) return mCustom.getDouble(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (Double) mMetadata.getClass().getMethod("getDouble", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return 0.;
            }
            //return mMetadata.getDouble(key);
        }
    }

    @Override
    public byte[] getByteArray(int key) {
        if (isCustom(key)) return mCustom.getByteArray(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (byte[]) mMetadata.getClass().getMethod("getByteArray", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return null;
            }
            //return mMetadata.getByteArray(key);
        }
    }

    @Override
    public Date getDate(int key) {
        if (isCustom(key)) return mCustom.getDate(key);
        else {
            try {
                Class paramsType[] = new Class[1];
                paramsType[0] = int.class;
                return (Date) mMetadata.getClass().getMethod("getDate", paramsType).invoke(mMetadata, key);
            } catch (Exception e) {
                return null;
            }//return mMetadata.getDate(key);
        }
    }

    private static class Custom {
        private static class Elm {
            private static final int TYPE_STRING = 1;
            private static final int TYPE_INT = 2;
            private static final int TYPE_BOOLEAN = 3;
            private static final int TYPE_LONG = 4;
            private static final int TYPE_DOUBLE = 5;
            private static final int TYPE_BYTEARRAY = 6;
            private static final int TYPE_DATE = 7;

            private final int type;
            private final Object obj;
            private Elm(int type, Object obj) {
                this.type = type;
                this.obj = obj;
            }
        }

        private final HashMap<Integer, MetadataDelegate.Custom.Elm> mHash = new HashMap<>();

        void addString(int key, String val) {
            mHash.put(key, new Elm(Elm.TYPE_STRING, val));
        }
        void addInt(int key, int val) {
            mHash.put(key, new Elm(Elm.TYPE_INT, val));
        }
        void addBoolean(int key, boolean val) {
            mHash.put(key, new Elm(Elm.TYPE_BOOLEAN, val));
        }
        void addLong(int key, long val) {
            mHash.put(key, new Elm(Elm.TYPE_LONG, val));
        }
        void addDouble(int key, double val) {
            mHash.put(key, new Elm(Elm.TYPE_DOUBLE, val));
        }
        void addByteArray(int key, byte val[]) {
            mHash.put(key, new Elm(Elm.TYPE_BYTEARRAY, val));
        }
        void addDate(int key, Date val) {
            mHash.put(key, new Elm(Elm.TYPE_DATE, val));
        }
        private Object getElm(int key, int type) {
            Elm elm = mHash.get(key);
            if (elm != null && elm.type == type)
                return elm.obj;
            return null;
        }
        String getString(int key) {
            return (String) getElm(key, Elm.TYPE_STRING);
        }
        int getInt(int key) {
            return (Integer) getElm(key, Elm.TYPE_INT);
        }
        boolean getBoolean(int key) {
            return (Boolean) getElm(key, Elm.TYPE_BOOLEAN);
        }
        long getLong(int key) {
            return (Long) getElm(key, Elm.TYPE_LONG);
        }
        double getDouble(int key) {
            return (Double) getElm(key, Elm.TYPE_DOUBLE);
        }
        byte[] getByteArray(int key) {
            return (byte[]) getElm(key, Elm.TYPE_BYTEARRAY);
        }
        Date getDate(int key) {
            return (Date) getElm(key, Elm.TYPE_DATE);
        }
        boolean has(int key) {
            return mHash.get(key) != null;
        }
    }
}
