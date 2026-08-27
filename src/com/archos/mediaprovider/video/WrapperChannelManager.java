// Copyright 2026 Courville Software
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

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;

public class WrapperChannelManager {

    public static void refreshChannels(Context context){
        try
        {
            Class<?> c = Class.forName("com.archos.mediacenter.video.leanback.channels.ChannelManager");
            Method m = c.getDeclaredMethod("refreshChannels", Context.class);
            m.invoke(null, context);
        }
        catch (Exception e)
        {
            Log.e("WrapperChannelManager","error ",e);
        }
    }
}
