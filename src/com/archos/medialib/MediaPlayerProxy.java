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
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import android.media.MediaPlayer;
import android.util.Log;

public class MediaPlayerProxy {
    private static final String TAG = "MediaPlayerProxy";
    public static class Track {
        public final String lang;
        public final int id;

        public Track(int id, String lang) {
            this.id = id;
            this.lang = lang;
        }
    }
    public static class Tracks {
        public final Track video;
        public final Track[] audios;
        public final Track[] timedTexts;

        public Tracks(Track video, Track[] audios, Track[] timedTexts) {
            this.video = video;
            this.audios = audios;
            this.timedTexts = timedTexts;
        }
    }

    public interface OnTimedTextListener {
        public void onTimedText(MediaPlayer mp, String text);
    }

    private static class OnTimedTextProxyListener implements java.lang.reflect.InvocationHandler {
        private final OnTimedTextListener mOnTimedTextListener;

        public OnTimedTextProxyListener(OnTimedTextListener listener) {
            mOnTimedTextListener = listener;
        }
        @Override
        public Object invoke(Object obj, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("onTimedText") && args.length == 2) {
                String text = args[1] != null ? text = (String) sTimedTextGetText.invoke(args[1]) : null;
                mOnTimedTextListener.onTimedText((MediaPlayer) args[0], text);
            }
            return null;
        }
    }

    private static Class<?> sOnTimedTextListener = null;

    private static Method sGetTrackInfoMethod = null;
    private static Method sSelectTrackMethod = null;
    private static Method sDeselectTrackMethod = null;
    private static Method sAddTimedTextSourceMethod = null;
    private static Method sSetOnTimedTextListener = null;

    private static Method sTrackInfoGetLanguageMethod = null;
    private static Method sTrackInfoGetTrackTypeMethod = null;

    private static Method sTimedTextGetText = null;

    private static final int MEDIA_TRACK_TYPE_UNKNOWN = 0;
    private static final int MEDIA_TRACK_TYPE_VIDEO = 1;
    private static final int MEDIA_TRACK_TYPE_AUDIO = 2;
    private static final int MEDIA_TRACK_TYPE_TIMEDTEXT = 3;

    private static final String MEDIA_MIMETYPE_TEXT_SUBRIP = "application/x-subrip";

    private static boolean sValid = false;

    static {
        try {
            sGetTrackInfoMethod = MediaPlayer.class.getMethod("getTrackInfo");

            Class<?>[] selectTrackParams = {
                    int.class,    // index
            };
            sSelectTrackMethod = MediaPlayer.class.getMethod("selectTrack", selectTrackParams);
            sDeselectTrackMethod = MediaPlayer.class.getMethod("deselectTrack", selectTrackParams);

            Class<?>[] addTimedTextSourceParams = {
                String.class, // path
                String.class  // mimetype
            };
            sAddTimedTextSourceMethod = MediaPlayer.class.getMethod("addTimedTextSource", addTimedTextSourceParams);

            sOnTimedTextListener = Class.forName(MediaPlayer.class.getCanonicalName()+"$OnTimedTextListener");
            Class<?>[] setOnTimedTextListenerParams = {
                    sOnTimedTextListener, // listener
                };
            sSetOnTimedTextListener = MediaPlayer.class.getMethod("setOnTimedTextListener", setOnTimedTextListenerParams);

            Class<?> clazz = Class.forName(MediaPlayer.class.getCanonicalName()+"$TrackInfo");
            sTrackInfoGetLanguageMethod = clazz.getMethod("getLanguage");
            sTrackInfoGetTrackTypeMethod = clazz.getMethod("getTrackType");

            clazz = Class.forName("android.media.TimedText");
            sTimedTextGetText = clazz.getMethod("getText");
            sValid = true;
        } catch (ClassNotFoundException e) {
            Log.v(TAG, e.toString(), e);
        } catch (NoSuchMethodException e) {
            Log.v(TAG, e.toString(), e);
        }
    }

    public static Tracks getTracks(MediaPlayer mp) {
        if (!sValid)
            return null;
        Object[] objs = null;
        try {
            objs = (Object[]) sGetTrackInfoMethod.invoke(mp);
            Log.d(TAG, "Tracks: nbTrack: " + objs.length);
            Track video = null;
            ArrayList<Track> audioList = new ArrayList<Track>();
            ArrayList<Track> subList = new ArrayList<Track>();

            for (int i = 0; i < objs.length; ++i) {
                final int trackType = (Integer) sTrackInfoGetTrackTypeMethod.invoke(objs[i]);
                final String lang = (String) sTrackInfoGetLanguageMethod.invoke(objs[i]);
                switch (trackType) {
                    case MEDIA_TRACK_TYPE_UNKNOWN:
                        break;
                    case MEDIA_TRACK_TYPE_VIDEO:
                        if (video == null) {
                            video = new Track(i, lang);
                        } else {
                            Log.w(TAG, "Warning: more than 2 video tracks !");
                        }
                        break;
                    case MEDIA_TRACK_TYPE_AUDIO:
                        audioList.add(new Track(i, lang));
                        break;
                    case MEDIA_TRACK_TYPE_TIMEDTEXT:
                        subList.add(new Track(i, lang));
                        break;
                }
            }
            return new Tracks(video,
                    audioList.toArray(new Track[audioList.size()]),
                    subList.toArray(new Track[subList.size()]));
        } catch (IllegalArgumentException e) {
            Log.v(TAG, e.toString(), e);
        } catch (IllegalAccessException e) {
            Log.v(TAG, e.toString(), e);
        } catch (InvocationTargetException e) {
            Log.v(TAG, e.toString(), e);
        }

        return null;
    }

    public static boolean selectTrack(MediaPlayer mp, Track track) {
        if (sValid) {
            try {
                sSelectTrackMethod.invoke(mp, track.id);
                return true;
            } catch (IllegalArgumentException e) {
                Log.v(TAG, e.toString(), e);
            } catch (IllegalAccessException e) {
                Log.v(TAG, e.toString(), e);
            } catch (InvocationTargetException e) {
                Log.v(TAG, e.toString(), e);
            }
        }
        return false;
    }

    public static boolean deselectTrack(MediaPlayer mp, Track track) {
        if (sValid) {
            try {
                sDeselectTrackMethod.invoke(mp, track.id);
                return true;
            } catch (IllegalArgumentException e) {
                Log.v(TAG, e.toString(), e);
            } catch (IllegalAccessException e) {
                Log.v(TAG, e.toString(), e);
            } catch (InvocationTargetException e) {
                Log.v(TAG, e.toString(), e);
            }
        }
        return false;
    }

    public static void addTimedTextSource(MediaPlayer mp, String path) {
        if (!sValid)
            return;
        try {
            sAddTimedTextSourceMethod.invoke(mp, path, MEDIA_MIMETYPE_TEXT_SUBRIP);
        } catch (IllegalArgumentException e) {
            Log.v(TAG, e.toString(), e);
        } catch (IllegalAccessException e) {
            Log.v(TAG, e.toString(), e);
        } catch (InvocationTargetException e) {
            Log.v(TAG, e.toString(), e);
        }
    }

    public static void setOnTimedTextListener(MediaPlayer mp, OnTimedTextListener listener) {
        if (!sValid)
            return;
        try {
            Object obj = null;
            if (listener != null) {
                Class<?>[] interfaces = {
                        sOnTimedTextListener,
                };
                obj = Proxy.newProxyInstance(sOnTimedTextListener.getClassLoader(), interfaces, new OnTimedTextProxyListener(listener));
            }
            sSetOnTimedTextListener.invoke(mp, obj);
        } catch (IllegalAccessException e) {
            Log.v(TAG, e.toString(), e);
        } catch (IllegalArgumentException e) {
            Log.v(TAG, e.toString(), e);
        } catch (InvocationTargetException e) {
            Log.v(TAG, e.toString(), e);
        }
    }

    public static boolean isValid() {
        return sValid;
    }
}