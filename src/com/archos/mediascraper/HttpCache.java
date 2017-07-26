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


package com.archos.mediascraper;

import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Map;

/**
 * A Http File Cache, that downloads your file to the specified directory
 * and returns a {@link java.io.File}. <br>
 * Fully thread-safe if used on separate directories for each process.
 * @hide
 */
public class HttpCache {
    private static final String TAG = "ScraperHttpCache";
    private static final boolean DBG = false;
    private static final boolean DBG_SPEED = false;

    public static final long UNLIMITED = -1;
    public static final long ONE_SECOND = 1000L;
    public static final long ONE_MINUTE = ONE_SECOND * 60L;
    public static final long ONE_HOUR = ONE_MINUTE * 60L;
    public static final long ONE_DAY = ONE_HOUR * 24L;

    private static final Hashtable<File, HttpCache> sInstances = new Hashtable<File, HttpCache>();

    public static HttpCache getInstance(File directory, long maxAge) {
        return getInstance(directory, maxAge, null, null);
    }

    /**
     * Specialized FileCache that allows to inject data by providing two additional
     * directories that have to exists and have to contain files.<br><br>
     * if <b>preferredDirectory</b> is given than getFile will return files from
     * there without trying to actually download it. This allows to substitute
     * (e.g. non-existing) files on the Internet.<br><br>
     * if <b>fallbackDirectory</b> is given than getFile will return files from
     * there after trying to download a file and in case that failed. Allows to
     * have fake functionality e.g. without Internet access.<br><br>
     */
    public static synchronized HttpCache getInstance(File directory, long maxAge, File fallbackDirectory, File preferredDirectory) {
        HttpCache instance = sInstances.get(directory);
        if (instance == null) {
            instance = new HttpCache(directory, maxAge, fallbackDirectory, preferredDirectory);
            sInstances.put(directory, instance);
        }
        return instance;
    }

    /**
     * Gets you a File from either preferredDirectory or fallbackDirectory without trying to
     * access the Internet. May return null
     */
    public static File getStaticFile(String url, File fallbackDirectory, File preferredDirectory) {
        // first check if there is a file in preferredDirectory
        File file = null;
        if (preferredDirectory != null) {
            file = new File(preferredDirectory, url2Filename(url));
        }
        if (fileIsValid(file))
            return file;

        // then try for fallbackDirectory
        file = null;
        if (fallbackDirectory != null) {
            file = new File(fallbackDirectory, url2Filename(url));
        }
        if (fileIsValid(file))
            return file;

        // otherwise return null
        return null;
    }

    private final Hashtable<String, File> mFileMap;
    private final File mPreferredDirectory;
    private final File mCacheDirectory;
    private final File mFallbackDirectory;
    private final long mCacheTimeOut;
    private final MultiLock<String> mUrlLock = new MultiLock<String>();

    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int BUFFER_POOL = 2;

    private static class SingletonHolder {
        public static final BufferPool INSTANCE = new BufferPool(BUFFER_SIZE, BUFFER_POOL);
    }
    private static BufferPool getBufferPool() {
        return SingletonHolder.INSTANCE;
    }

    private HttpCache(File directory, long maxAge, File fallbackDirectory, File preferredDirectory) {
        mCacheDirectory = directory;
        mCacheTimeOut = maxAge;

        if (mCacheDirectory == null)
            throw new RuntimeException("You must specify a Directory");

        if (!mCacheDirectory.exists()) {
            if (!mCacheDirectory.mkdirs()) {
                throw new RuntimeException("Cannot create cache Directory");
            }
        }

        // since isDirectory returns false for non existing directories check again
        if (!mCacheDirectory.isDirectory())
            throw new RuntimeException("You must specify a Directory");

        mFileMap = new Hashtable<String, File>();

        File[] list = mCacheDirectory.listFiles();
        if (list != null) {
            for (File f : list) {
                if (fileNeedsRefresh(f)) {
                    if (!f.delete()) {
                        Log.d(TAG, "could not delete old file: " + f);
                    }
                } else if (f.isFile()) {
                    mFileMap.put(f.getName(), f);
                }
            }
        }

        if (fallbackDirectory != null) {
            if (fallbackDirectory.exists() && fallbackDirectory.isDirectory()) {
                mFallbackDirectory = fallbackDirectory;
            } else {
                Log.d(TAG, "FallbackDirectory must exist already");
                mFallbackDirectory = null;
            }
        } else {
            mFallbackDirectory = null;
        }

        if (preferredDirectory != null) {
            if (preferredDirectory.exists() && preferredDirectory.isDirectory()) {
                mPreferredDirectory = preferredDirectory;
            } else {
                Log.d(TAG, "PreferredDirectory must exist already");
                mPreferredDirectory = null;
            }
        } else {
            mPreferredDirectory = null;
        }
    }

    private static String ThreadInfo() {
        return Thread.currentThread().getName();
    }

    /**
     * Downloads a url to a File
     * @param url http://example.com/get?example=something
     * @param useGzip true if downloading should try to use gzipped transport
     * @return a File or null if the download failed
     */
    public File getFile(String url, boolean useGzip) {
        return getFile(url, useGzip, null);
    }

    private static void logSpeed(String url, long timeMs, long size) {
        double timeSec = timeMs / 1000.0;
        double sizeKb = size / 1024.0;
        double speedKbs = sizeKb / timeSec;
        String timeString = String.format("%.2f s", timeSec);
        String speedString = String.format("%.2f kB/s", speedKbs);
        String sizeString = String.format("%.2f kB", sizeKb);
        Log.d("XXSPEED", "[" + url + "] " + sizeString + " in " + timeString + " (~" + speedString + ")");
    }

    /**
     * Downloads a url to a File
     * @param url http://example.com/get?example=something
     * @param useGzip true if downloading should try to use gzipped transport
     * @param extraHeaders define extra headers here
     * @return a File or null if the download failed
     */
    public File getFile(String url, boolean useGzip, Map<String, String> extraHeaders) {
        if(DBG) Log.d(TAG, "request [" + url + "]");
        if (url == null) {
            Log.w(TAG, "getFile(null)");
            return null;
        }
        // first check if we have that file already in the preferred directory
        // if there is a file that seems to be valid we return that one instead.
        if (mPreferredDirectory != null) {
            File preferred = getResultingPreferredCacheFile(url);
            if (fileIsValid(preferred))
                return preferred;
        }
        // do not proceed if the file is already downloading..
        mUrlLock.lock(url);
        // only one thread can get here for a given url
        File ret = null;
        try {
            ret = mFileMap.get(url2Filename(url));
            if (ret == null || !ret.exists() || fileNeedsRefresh(ret)) {
                if (DBG) Log.d(TAG, "downloading " + ThreadInfo());
                // someone may have cleared the cache and we need to recreate the directory.
                if (!mCacheDirectory.exists()) {
                    if (!mCacheDirectory.mkdirs()) {
                        throw new RuntimeException("Cannot create cache Directory");
                    }
                }

                ret = null;
                File generatedFile = getResultingCacheFile(url);
                File inProgressFile = new File (generatedFile.getPath() + ".temp");
                HttpDownloadWrapper downloader = new HttpDownloadWrapper(url, useGzip);
                boolean downloadSuccess = false;
                OutputStream output = null;
                InputStream input = null;
                try {
                    long start;
                    if (DBG_SPEED) start = System.currentTimeMillis();

                    output = new FileOutputStream(inProgressFile);
                    input = downloader.getInputStream(extraHeaders);

                    BufferPool pool = getBufferPool();
                    byte[] buffer = pool.obtain();
                    streamCopy2(input, output, buffer);
                    pool.putBack(buffer);

                    output.close();
                    output = null;

                    if (DBG_SPEED) {
                        long end = System.currentTimeMillis();
                        long time = end - start;
                        long size = inProgressFile.exists() ? inProgressFile.length() : 0;
                        logSpeed(url, time, size);
                    }

                    if (inProgressFile.renameTo(generatedFile)) {
                        mFileMap.put(url2Filename(url), generatedFile);
                        ret = generatedFile;
                        downloadSuccess = true;
                    } else {
                        Log.d(TAG, "failed to rename " + inProgressFile + " to " +  generatedFile);
                        inProgressFile.delete();
                        generatedFile.delete();
                    }
                } catch (IOException e) {
                    Log.d(TAG, "Exception: " + e);
                } finally {
                    closeSilently(downloader);
                    // if stream are != null close them
                    closeSilently(input);
                    closeSilently(output);
                    // make sure that incomplete files are deleted
                    if (!downloadSuccess)
                        generatedFile.delete();
                }
                if (!downloadSuccess && mFallbackDirectory != null) {
                    if (DBG) Log.d(TAG, "checking for fallback file");
                    File fallbackFile = getResultingFallbackCacheFile(url);
                    if (fallbackFile != null && fallbackFile.exists()) {
                        ret = fallbackFile;
                        downloadSuccess = true;
                        if (DBG) Log.d(TAG, "using " + ret.getAbsolutePath() + " as fallback");
                    }
                }
            }
        } finally {
            mUrlLock.unlock(url);
            // ... and threads waiting so they can check the list again
            if (DBG) Log.d(TAG, "Exiting getFile() " + ThreadInfo());
        }

        return ret;
    }

    private boolean fileNeedsRefresh(File f) {
        if (f.length() < 1) return true;
        if (mCacheTimeOut > 0) {
            if ((f.lastModified() + mCacheTimeOut) < System.currentTimeMillis()) {
                if (DBG) {
                    DateFormat fmt = DateFormat.getInstance();
                    Log.d(TAG, "File " + f.getPath() + " too old f:" + fmt.format(new Date(f.lastModified())) + " now:" + fmt.format(new Date()));
                }
                return true;
            }
        }
        return false;
    }

    private File getResultingCacheFile(String url) {
        return new File(mCacheDirectory, url2Filename(url));
    }

    private File getResultingFallbackCacheFile(String url) {
        if (mFallbackDirectory != null)
            return new File(mFallbackDirectory, url2Filename(url));
        return null;
    }

    private File getResultingPreferredCacheFile(String url) {
        if (mPreferredDirectory != null)
            return new File(mPreferredDirectory, url2Filename(url));
        return null;
    }

    private static boolean fileIsValid (File f) {
        if (f == null || !f.exists() || !f.isFile()) return false;
        return true;
    }

    private static void streamCopy2(InputStream input, OutputStream output, byte[] buf) throws IOException {
        int i;
        while ((i = input.read(buf)) != -1) {
            output.write(buf, 0, i);
        }
    }

    private static void closeSilently(Closeable in) {
        if (in == null)
            return;
        try {
            in.close();
        } catch (IOException e) {
            // silence
        }
    }

    private static final String url2Filename(String url) {
        return HashGenerator.hash(url);
    }

    private static class BufferPool extends ObjectPool<byte[]> {
        private final int mBufferSize;

        public BufferPool(int byteSize, int poolSize) {
            super(poolSize);
            mBufferSize = byteSize;
        }

        @Override
        protected byte[] create() {
            return new byte[mBufferSize];
        }

        @Override
        protected void cleanup(byte[] object) {
            // need no cleanup a buffer
        }

    }
}
