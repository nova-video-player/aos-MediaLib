package com.archos.medialib;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;

public class NovaExoRendererFactory  extends DefaultRenderersFactory {

    private final Context context;
    @ExtensionRendererMode private int extensionRendererMode;
    private long allowedVideoJoiningTimeMs;
    private boolean enableDecoderFallback;
    private MediaCodecSelector mediaCodecSelector;
    private boolean enableFloatOutput;
    private boolean enableAudioTrackPlaybackParams;
    private boolean enableOffload;

    public NovaExoRendererFactory(Context context) {
        super(context);
        this.context = context;
        allowedVideoJoiningTimeMs = DEFAULT_ALLOWED_VIDEO_JOINING_TIME_MS;
        mediaCodecSelector = MediaCodecSelector.DEFAULT;
    }

    @Override
    public Renderer[] createRenderers(
            Handler eventHandler,
            VideoRendererEventListener videoRendererEventListener,
            AudioRendererEventListener audioRendererEventListener,
            TextOutput textRendererOutput,
            MetadataOutput metadataRendererOutput) {
        ArrayList<Renderer> renderersList = new ArrayList<>();
        buildVideoRenderers(
                context,
                EXTENSION_RENDERER_MODE_ON,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                videoRendererEventListener,
                allowedVideoJoiningTimeMs,
                renderersList);
        @Nullable
        AudioSink audioSink =
                buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams, enableOffload);
        if (audioSink != null) {
            buildAudioRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    audioSink,
                    eventHandler,
                    audioRendererEventListener,
                    renderersList);
        }
        buildTextRenderers(context, textRendererOutput, eventHandler.getLooper(),
                extensionRendererMode, renderersList);
        buildMetadataRenderers(context, metadataRendererOutput, eventHandler.getLooper(),
                extensionRendererMode, renderersList);
        buildCameraMotionRenderers(context, extensionRendererMode, renderersList);
        buildMiscellaneousRenderers(context, eventHandler, extensionRendererMode, renderersList);
        return renderersList.toArray(new Renderer[0]);
    }

    @Override
    public DefaultRenderersFactory setExtensionRendererMode(
            @ExtensionRendererMode int extensionRendererMode) {
        super.setExtensionRendererMode(extensionRendererMode);
        this.extensionRendererMode = extensionRendererMode;
        return this;
    }
}
