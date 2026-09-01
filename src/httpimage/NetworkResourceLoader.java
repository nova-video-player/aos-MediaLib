//  Copyright 2012 Zonghai Li. All rights reserved.
//
//  Redistribution and use in binary and source forms, with or without modification,
//  are permitted for any project, commercial or otherwise, provided that the
//  following conditions are met:
//  
//  Redistributions in binary form must display the copyright notice in the About
//  view, website, and/or documentation.
//  
//  Redistributions of source code must retain the copyright notice, this list of
//  conditions, and the following disclaimer.
//
//  THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
//  INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
//  PARTICULAR PURPOSE AND NONINFRINGEMENT OF THIRD PARTY RIGHTS. IN NO EVENT SHALL THE
//  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
//  WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
//  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THIS SOFTWARE.

package httpimage;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.net.Uri;
import android.util.Log;

/**
 * Resource loader using standard Android HttpURLConnection. Supports HTTP and HTTPS requests.
 * 
 * @author zonghai@gmail.com
 */
public class NetworkResourceLoader {
    public static final String TAG = "NetworkResourceLoader";
    public static final boolean DEBUG = false;

    public static class Response implements AutoCloseable {
        private final HttpURLConnection mConnection;
        private final InputStream mInputStream;

        public Response(HttpURLConnection connection) throws IOException {
            mConnection = connection;
            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                throw new IOException("HTTP error code: " + responseCode);
            }
            mInputStream = connection.getInputStream();
        }

        public String getContentType() {
            return mConnection.getContentType();
        }

        public String getContentEncoding() {
            return mConnection.getContentEncoding();
        }

        public long getContentLength() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                return mConnection.getContentLengthLong();
            }
            return mConnection.getContentLength();
        }

        public InputStream getInputStream() {
            return mInputStream;
        }

        @Override
        public void close() {
            if (mInputStream != null) {
                try {
                    mInputStream.close();
                } catch (IOException ignored) {}
            }
            if (mConnection != null) {
                mConnection.disconnect();
            }
        }
    }

    public Response load(Uri uri) throws IOException {
        if (DEBUG) Log.d(TAG, "Requesting: " + uri);
        URL url = new URL(uri.toString());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("Accept-Encoding", "gzip");
        conn.setInstanceFollowRedirects(true);
        return new Response(conn);
    }
}
