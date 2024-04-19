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

import androidx.annotation.Nullable;
import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;

public class HttpDownloadWrapper implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(HttpDownloadWrapper.class);

    private HttpURLConnection mUrlConnection;
    private final String mUrl;

    /**
     * Creates an Object that can be used to download a single file via HttpGet. <br>
     * Example<br>
     * <code>
     * try {<br>
     *   HttpDownloadWrapper download = new HttpDownloadWrapper("http://www.google.com/search?q=archos", true);<br>
     *   String result = download.getContentAsString();<br>
     * } catch (IOException e) { Log.d(TAG, "error") }<br>
     * </code>
     *
     * @param url The complete url to get like
     *            "http://host.com/blub?more&evenmore"
     * @param useGzip unused.
     */
    public HttpDownloadWrapper(final String url, final boolean useGzip) {
        mUrl = url;
    }

    /**
     * Sets up the HttpRequest, executes it and returns the Stream Note that you
     * must {@link #close()} this yourself.
     *
     * @return Inputstream, auto unGzip'ed
     * @throws IOException
     */
    public InputStream getInputStream(@Nullable Map<String, String> extraHeaders) throws IOException {
        log.debug("getInputStream: Downloading [" + mUrl + "]");

        if (mUrlConnection != null) {
            throw new IllegalStateException("getInputStream: Cannot call getInputStream more than once.");
        }

        URL url = new URL(mUrl);
        // See https://github.com/nova-video-player/aos-AVP/issues/1154
        // on old Android versions (before 7.0) the ssl certification check is based on the platform one that is not anymore up to date leading to problems
        // thus let's trust image.tmdb.org to fix posters download
        TrustAllCertificates.trustAllCertificates();
        mUrlConnection = (HttpURLConnection) url.openConnection();
        mUrlConnection.setConnectTimeout(20000);
        mUrlConnection.setReadTimeout(40000);
        mUrlConnection.setInstanceFollowRedirects(true);
        if (extraHeaders != null) {
            for (Entry<String, String> header : extraHeaders.entrySet()) {
                mUrlConnection.addRequestProperty(header.getKey(), header.getValue());
            }
        }
        int status = mUrlConnection.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP   || status == HttpURLConnection.HTTP_MOVED_PERM)
        {
            String newUrl = mUrlConnection.getHeaderField("Location");
            mUrlConnection = (HttpURLConnection) new URL(newUrl).openConnection();
            mUrlConnection.connect();
        }
        return mUrlConnection.getInputStream();
    }

    /**
     * Call this after {@link #getInputStream()}, when you don't need the Stream
     * anymore.
     */
    @Override
    public void close() throws IOException {
        if (mUrlConnection != null)
            mUrlConnection.disconnect();
    }

    // ------------------------------------------------------------------------
    // helper Methods
    // ------------------------------------------------------------------------

    /**
     * Closes InputStreams etc without IOException, might even be null.
     */
    public static final void closeSilently(final Closeable in) {
        if (in == null) return;
        try {
            in.close();
        } catch (final IOException e) {
            // silence
        }
    }
}