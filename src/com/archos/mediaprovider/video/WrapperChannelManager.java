package com.archos.mediaprovider.video;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

public class WrapperChannelManager {

    public static void refreshChannels(Context context){
        try
        {
            Class c = Class.forName("com.archos.mediacenter.video.leanback.channels.ChannelManager");
            Method m = c.getDeclaredMethod("refreshChannels", Context.class);
            m.invoke(null, context);
        }
        catch (Exception e)
        {
            Log.e("WrapperChannelManager","error ",e);
        }
    }
}
