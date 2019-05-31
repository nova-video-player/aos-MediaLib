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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.nio.channels.FileLock;

public class LibAvos {
    private static final String TAG = "LibAvos";
    private static final boolean DBG = false;
    private static boolean sIsAvailable = false;
    private static boolean sIsPluginAvailable = false;
    private static boolean sPluginUpdateNeeded = false;
    private static int sInitState = 0;
    private static final String NO_NEON_SUFFIX = "_no_neon";

    static void installExtLibs(Context ctx, boolean armHasNeon) {
        File extFilesDir = ctx.getExternalFilesDir(null);
        if (extFilesDir == null)
            return;
        String pluginsPath = extFilesDir.getPath() + "/plugins/11";

        /*
         * replace com.archos.mediacenter.video[aw|ti|rk|free] with com.archos.mediacenter.video
         * concatenate strings because of the mighty sed.
         */
        pluginsPath = pluginsPath.replaceAll("/com.archos.mediacenter."+"video[a-zA-Z0-9]*/", "/com.archos.mediacenter.video/");

        File extLibsDir = new File(pluginsPath);
        if (DBG) Log.d(TAG, "installExtLibs: check folder: " + extLibsDir);


        if (!(extLibsDir.isDirectory() && extLibsDir.canWrite()))
            return;

        for (File file : extLibsDir.listFiles()) {
            if (!file.getPath().endsWith(".so"))
                continue;
            if ((armHasNeon && file.getPath().endsWith(NO_NEON_SUFFIX + ".so")) ||
                    (!armHasNeon && !file.getPath().endsWith(NO_NEON_SUFFIX + ".so"))) {
                file.delete();
                continue;
            }

            File outDir = new File(ctx.getFilesDir(), "plugins_11");
            if (!outDir.exists())
                outDir.mkdir();
            String outFileName = outDir.getPath() +"/"+file.getName();
            /* We need a temporary file otherwise, opening and closing the FileOutputStream will result in an empty file in case the input file has already been deeted */
            File outFileTmp = new File(outFileName+"_tmp");
            boolean canDeleteFile = false;
            try {
                FileOutputStream out_tmp = new FileOutputStream(outFileTmp);
                try {
                    FileLock lockOut = out_tmp.getChannel().lock();
                    try {
                        if (file.exists()) {
                            if (DBG) Log.d(TAG, "Try to import plugins lib "+file.getPath());
                            FileInputStream in = new FileInputStream(file);
                            File outFile = new File(outFileName);
                            FileOutputStream out = new FileOutputStream(outFile);
                            try {
                                byte[] buffer = new byte[16*1024];
                                int read;
                                if (DBG) Log.d(TAG, "Importing plugins lib "+file.getPath());
                                while ((read = in.read(buffer)) != -1)
                                    out.write(buffer, 0, read);
                                    canDeleteFile = true;
                            } catch (Exception e) {
                                if (DBG) Log.d(TAG, "Error Copy");
                            } finally {
                                in.close();
                                out.flush();
                                out.close();
                            }
                        } else {
                            if (DBG) Log.d(TAG, "plugins lib "+file.getPath()+" is not there anymore");
                        }
                    } catch (Exception e) {
                        if (DBG) Log.d(TAG, "Error file in");
                    } finally {
                        if (canDeleteFile) file.delete();
                        lockOut.release();
                    }
                } catch (Exception e) {
                    if (DBG) Log.d(TAG, "Error lock out");
                    e.printStackTrace();
                } finally {
                    out_tmp.close();
                }
            } catch (Exception e) {
                if (DBG) Log.d(TAG, "Error file out");
                e.printStackTrace();
            } finally {
                if (outFileTmp.exists()) outFileTmp.delete();
            }
        }
    }

    static boolean loadLibrary(Context ctx, String lib, boolean hasNeon, boolean tryExternLib) {
        if (!hasNeon)
            lib = lib + NO_NEON_SUFFIX;
        boolean loaded = false;
        if (tryExternLib) {
            try {
                String path = ctx.getFilesDir()+ "/plugins_11/lib" + lib + ".so";
                File libFile = new File(path);
                if (libFile.exists()) {
                    System.load(path);
                    loaded = true;
                    sIsPluginAvailable = true;
                }
            } catch (UnsatisfiedLinkError ule) {
                sPluginUpdateNeeded = true;
            } catch (SecurityException se) {
            }
        }
        if (!loaded) {
            try {
                System.loadLibrary(lib);
                loaded = true;
            } catch (UnsatisfiedLinkError ule) {
                    Log.v(TAG, "Can't load library: " + ule);
            } catch (SecurityException se) {
                    Log.v(TAG, "Encountered a security issue when loading avosjni library: " + se);
            }
        }
        return loaded;
    }

    public static boolean pluginNeedUpdate(Context ctx) {
        File oldDir = new File(ctx.getFilesDir(), "plugins");
        File newDir = new File(ctx.getFilesDir(), "plugins_11");
	return sPluginUpdateNeeded || !newDir.isDirectory() && oldDir.isDirectory();
    }

    public static boolean isAvailable() {
        return sIsAvailable;
    }

    private static void init(Context ctx, boolean async) {
        boolean armHasNeon = true;

        synchronized (ctx) {
            if (sInitState > 0) {
                if (!async) {
                    while (sInitState != 2) {
                        try {
                            ctx.wait();
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                return;
            }
            sInitState = 1;
        }

        try {
            if (CpuTest.isArm() && !CpuTest.hasNeon()) {
                Log.d(TAG, "That shit does not have neon!!!");
                armHasNeon = false;
            }
        } catch (Exception ule) {};

        //We do not need to use any external codec packs
        //installExtLibs(ctx, armHasNeon);

        loadLibrary(ctx, "dav1d", armHasNeon, false);
        loadLibrary(ctx, "avutil", armHasNeon, false);
        loadLibrary(ctx, "swresample", armHasNeon, false);
        loadLibrary(ctx, "avcodec", armHasNeon, true);
        loadLibrary(ctx, "avformat", armHasNeon, false);

        // not used anymore
	    //loadLibrary(ctx, "cryptocompat", true, false);
	    //loadLibrary(ctx, "sslcompat", true, false);
        //loadLibrary(ctx, "curl", armHasNeon, false);
        loadLibrary(ctx, "deinterlace", armHasNeon, false);
        loadLibrary(ctx, "audiocompress", armHasNeon, false);

        String api = "21";

        loadLibrary(ctx, "sfdec", armHasNeon, false);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Build.VERSION.PREVIEW_SDK_INT == 0))  {
            loadLibrary(ctx, "stagefright", true, false);
            loadLibrary(ctx, "media", true, false);
            loadLibrary(ctx, "cutils", true, false);
            loadLibrary(ctx, "utils", true, false);
            loadLibrary(ctx, "binder", true, false);
            loadLibrary(ctx, "ui", true, false);
            loadLibrary(ctx, "hardware", true, false);
        }
        loadLibrary(ctx, "avos_android", armHasNeon, false);
        loadLibrary(ctx, "avos", armHasNeon, false);
        if (loadLibrary(ctx, "avosjni", armHasNeon, false)) {
            nativeInit(ctx.getPackageName(), sIsPluginAvailable);
            nativeLoadLibraryRTLDGlobal("libsfdec.core.21.so");
            sIsAvailable = true;
        } else {
            sIsAvailable = false;
        }
        synchronized (ctx) {
            sInitState = 2;
            ctx.notifyAll();
        }
    }

    public static void initAsync(Context ctx) {
        init(ctx.getApplicationContext(), true);
    }

    public static void init(Context ctx) {
        init(ctx.getApplicationContext(), false);
    }

    public static void debugInit() {
        nativeDebugInit();
    }

    public static void avsh(String cmd) {
        nativeAvsh(cmd);
    }

    public static void setSubtitlePath(String path) {
        nativeSetSubtitlePath(path);
    }

    public static void setDecoder(int decoder) {
        nativeSetDecoder(decoder);
    }

    public static void setCodepage(int codepage) {
        nativeSetCodepage(codepage);
    }

    public static void setOutputSampleRate(int sampleRate) {
        nativeSetOutputSampleRate(sampleRate);
    }

    public static void setPassthrough(int forcePassthrough) {
        nativeSetPassthrough(forcePassthrough);
    }

    public static void setDownmix(int downmix) {
        nativeSetDownmix(downmix);
    }

    private static native void nativeInit(String pkgName, boolean isPluginAvailable);

    private static native void nativeDebugInit();

    private static native void nativeLoadLibraryRTLDGlobal(String lib);

    private static native void nativeAvsh(String cmd);

    private static native void nativeSetSubtitlePath(String path);

    private static native void nativeSetDecoder(int decoder);

    private static native void nativeSetCodepage(int codepage);

    private static native void nativeSetOutputSampleRate(int sampleRate);

    private static native void nativeSetPassthrough(int forcePassthrough);

    private static native void nativeSetDownmix(int downmix);

    // mp decoder
    public static final int MP_DECODER_ANY = 0;
    public static final int MP_DECODER_SW = 1;
    public static final int MP_DECODER_HW_OMX = 2;
    public static final int MP_DECODER_HW_OMXCODEC = 3;
    public static final int MP_DECODER_HW_MEDIACODEC = 4;
    public static final int MP_DECODER_HW_OMXPLUS = 5;

}
