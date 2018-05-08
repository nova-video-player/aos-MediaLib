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

package com.archos.mediacenter;

import com.archos.medialib.IMediaMetadataRetriever;
import com.archos.medialib.LibAvos;
import com.archos.medialib.MediaFactory;
import com.archos.medialib.MediaMetadata;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class LibAvosReceiver extends BroadcastReceiver {

    private interface RetrieverDelegate {
        String get(int key);
    }

    private void displayKey(RetrieverDelegate r, String name, int key) {
        Log.d("IMetadataRetriever", name +": " + r.get(key));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals("com.archos.mediacenter.NEW_PLUGINS")) {
            Log.d("LibAvosReceiver", "NEW_PLUGINS: relaunching");
            System.exit(0);
        } else if (action.equals("com.archos.mediacenter.DEBUG")) {
            LibAvos.init(context);
            LibAvos.debugInit();
        } else if (action.equals("com.archos.mediacenter.AVSH")) {
            LibAvos.init(context);
            LibAvos.avsh(intent.getStringExtra("cmd"));
        } else if (action.equals("com.archos.mediacenter.DEBUG_SCAN")) {
            Log.d("IMetadataRetriever", intent.getData().getPath());
            final IMediaMetadataRetriever retriever = MediaFactory.createMetadataRetriever(context);
            retriever.setDataSource(context, intent.getData());
            final MediaMetadata metadata = retriever.getMediaMetadata();

            for (int i = 0; i < 2; ++i) {
                RetrieverDelegate r;
                if (i == 0) {
                    Log.d("IMetadataRetriever", "IMediaMetadataRetriever.extractMetadata():");
                    r = new RetrieverDelegate() {
                        public String get(int key) {
                            return retriever.extractMetadata(key);
                        }
                    };
                } else {
                    Log.d("IMetadataRetriever", "MediaMetadata.getString():");
                    r = new RetrieverDelegate() {
                        public String get(int key) {
                            if (metadata.has(key))
                                return metadata.getString(key);
                            else
                                return null;
                        }
                    };
                }

                displayKey(r, "METADATA_KEY_CD_TRACK_NUMBER", IMediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
                displayKey(r, "METADATA_KEY_ALBUM", IMediaMetadataRetriever.METADATA_KEY_ALBUM);
                displayKey(r, "METADATA_KEY_ARTIST", IMediaMetadataRetriever.METADATA_KEY_ARTIST);
                displayKey(r, "METADATA_KEY_AUTHOR", IMediaMetadataRetriever.METADATA_KEY_AUTHOR);
                displayKey(r, "METADATA_KEY_COMPOSER", IMediaMetadataRetriever.METADATA_KEY_COMPOSER);
                displayKey(r, "METADATA_KEY_DATE", IMediaMetadataRetriever.METADATA_KEY_DATE);
                displayKey(r, "METADATA_KEY_GENRE", IMediaMetadataRetriever.METADATA_KEY_GENRE);
                displayKey(r, "METADATA_KEY_TITLE", IMediaMetadataRetriever.METADATA_KEY_TITLE);
                displayKey(r, "METADATA_KEY_YEAR", IMediaMetadataRetriever.METADATA_KEY_YEAR);
                displayKey(r, "METADATA_KEY_DURATION", IMediaMetadataRetriever.METADATA_KEY_DURATION);
                displayKey(r, "METADATA_KEY_NUM_TRACKS", IMediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
                displayKey(r, "METADATA_KEY_WRITER", IMediaMetadataRetriever.METADATA_KEY_WRITER);
                displayKey(r, "METADATA_KEY_MIMETYPE", IMediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                displayKey(r, "METADATA_KEY_ALBUMARTIST", IMediaMetadataRetriever.METADATA_KEY_ALBUMARTIST);
                displayKey(r, "METADATA_KEY_DISC_NUMBER", IMediaMetadataRetriever.METADATA_KEY_DISC_NUMBER);
                displayKey(r, "METADATA_KEY_COMPILATION", IMediaMetadataRetriever.METADATA_KEY_COMPILATION);
                displayKey(r, "METADATA_KEY_HAS_AUDIO", IMediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
                displayKey(r, "METADATA_KEY_HAS_VIDEO", IMediaMetadataRetriever.METADATA_KEY_HAS_VIDEO);
                displayKey(r, "METADATA_KEY_VIDEO_WIDTH", IMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                displayKey(r, "METADATA_KEY_VIDEO_HEIGHT", IMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                displayKey(r, "METADATA_KEY_BITRATE", IMediaMetadataRetriever.METADATA_KEY_BITRATE);
                displayKey(r, "METADATA_KEY_TIMED_TEXT_LANGUAGES", IMediaMetadataRetriever.METADATA_KEY_TIMED_TEXT_LANGUAGES);
                displayKey(r, "METADATA_KEY_IS_DRM", IMediaMetadataRetriever.METADATA_KEY_IS_DRM);
                displayKey(r, "METADATA_KEY_LOCATION", IMediaMetadataRetriever.METADATA_KEY_LOCATION);
                displayKey(r, "METADATA_KEY_SAMPLE_RATE", IMediaMetadataRetriever.METADATA_KEY_SAMPLE_RATE);
                displayKey(r, "METADATA_KEY_NUMBER_OF_CHANNELS", IMediaMetadataRetriever.METADATA_KEY_NUMBER_OF_CHANNELS);
                displayKey(r, "METADATA_KEY_AUDIO_WAVE_CODEC", IMediaMetadataRetriever.METADATA_KEY_AUDIO_WAVE_CODEC);
                displayKey(r, "METADATA_KEY_AUDIO_BITRATE", IMediaMetadataRetriever.METADATA_KEY_AUDIO_BITRATE);
                displayKey(r, "METADATA_KEY_VIDEO_FOURCC_CODEC", IMediaMetadataRetriever.METADATA_KEY_VIDEO_FOURCC_CODEC);
                displayKey(r, "METADATA_KEY_VIDEO_BITRATE", IMediaMetadataRetriever.METADATA_KEY_VIDEO_BITRATE);
                displayKey(r, "METADATA_KEY_FRAMES_PER_THOUSAND_SECONDS", IMediaMetadataRetriever.METADATA_KEY_FRAMES_PER_THOUSAND_SECONDS);
                displayKey(r, "METADATA_KEY_ENCODING_PROFILE", IMediaMetadataRetriever.METADATA_KEY_ENCODING_PROFILE);
                displayKey(r, "METADATA_KEY_NB_VIDEO_TRACK", IMediaMetadataRetriever.METADATA_KEY_NB_VIDEO_TRACK);
                displayKey(r, "METADATA_KEY_NB_AUDIO_TRACK", IMediaMetadataRetriever.METADATA_KEY_NB_AUDIO_TRACK);
                displayKey(r, "METADATA_KEY_NB_SUBTITLE_TRACK", IMediaMetadataRetriever.METADATA_KEY_NB_SUBTITLE_TRACK);
            }
            retriever.release();
        }
    }
}
