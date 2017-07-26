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

import android.util.Log;

public class CpuTest {
    private static final String TAG = "CpuTest";
    private static final boolean sLoaded;

    static {
        boolean loaded = false;
        try {
            System.loadLibrary("cputest");
            loaded = true;
        } catch (UnsatisfiedLinkError ule) {
            Log.e(TAG, "Can't load library: " + ule);
        } catch (SecurityException se) {
            Log.e(TAG, "Encountered a security issue when loading avosjni library: " + se);
        }
        sLoaded = loaded;
    }

    public static boolean isArm() {
        return sLoaded ? nativeIsArm() : true;
    }
    private static native boolean nativeIsArm();

    public static boolean isX86() {
        return sLoaded ? nativeIsX86() : false;
    }
    private static native boolean nativeIsX86();

    public static boolean hasNeon() {
        return sLoaded ? nativeHasNeon() : false;
    }
    private static native boolean nativeHasNeon();

    public static boolean isArmV7a() {
        return sLoaded ? nativeIsArmV7a() : true;
    }
    private static native boolean nativeIsArmV7a();
}
