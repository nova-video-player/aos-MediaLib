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
import java.net.ProxySelector;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.routing.SystemDefaultRoutePlanner;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.config.Registry;
import org.apache.hc.core5.http.config.RegistryBuilder;

import android.net.Uri;
import android.util.Log;


/**
 * resource loader using apache HTTP client. Support HTTP and HTTPS request.
 * 
 * @author zonghai@gmail.com
 */
public class NetworkResourceLoader {
    public static final String TAG = "NetworkResourceLoader";
    public static final boolean DEBUG = true;

    private CloseableHttpClient mHttpClient = createHttpClient();

    
    /**
     * Gets the input stream from a response entity. If the entity is gzipped then this will get a
     * stream over the uncompressed data.
     *
     * @param entity the entity whose content should be read
     * @return the input stream to read from
     * @throws IOException
     */
    public CloseableHttpResponse load (Uri uri) throws IOException{
        if (DEBUG) Log.d(TAG, "Requesting: " + uri);
        HttpGet httpGet = new HttpGet(uri.toString());
        httpGet.addHeader("Accept-Encoding", "gzip");
        
        return mHttpClient.execute(httpGet);

    }

    
    /**
     * Create a thread-safe client. This client does not do redirecting, to allow us to capture
     * correct "error" codes.
     *
     * @return HttpClient
     */
    //public final HttpClient createHttpClient() {
    public final CloseableHttpClient createHttpClient() {

        if (DEBUG) Log.d(TAG, "createHttpClient");


        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", SSLConnectionSocketFactory.getSocketFactory())
                .register("http", new PlainConnectionSocketFactory())
                .build();
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        HttpClients.custom().setConnectionManager(connectionManager);
        CloseableHttpClient httpClient = HttpClients.custom().setConnectionManager(connectionManager)
                .setRoutePlanner(new SystemDefaultRoutePlanner(ProxySelector.getDefault()))
                .build();

        return httpClient;

        /*
        HttpParams params = new BasicHttpParams();
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);  
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);  
        HttpProtocolParams.setUseExpectContinue(params, true);  
        // Turn off stale checking. Our connections break all the time anyway,
        // and it's not worth it to pay the penalty of checking every time.
        HttpConnectionParams.setStaleCheckingEnabled(params, false);

        // Default connection and socket timeout of 30 seconds. Tweak to taste.
        HttpConnectionParams.setConnectionTimeout(params, 10*1000);
        HttpConnectionParams.setSoTimeout(params, 20*1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        
        ConnManagerParams.setTimeout(params, 5 * 1000);
        ConnManagerParams.setMaxConnectionsPerRoute(params, new ConnPerRouteBean(50));
        ConnManagerParams.setMaxTotalConnections(params, 200);
        
        // Sets up the http part of the service.
        final SchemeRegistry supportedSchemes = new SchemeRegistry();

        // Register the "http" protocol scheme, it is required
        // by the default operator to look up socket factories.
        final SocketFactory sf = PlainSocketFactory.getSocketFactory();
        supportedSchemes.register(new Scheme("http", sf, 80));
        supportedSchemes.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));  
        final ThreadSafeClientConnManager ccm = new ThreadSafeClientConnManager(params,
                supportedSchemes);
        
        DefaultHttpClient httpClient = new DefaultHttpClient(ccm, params);
        
        httpClient.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(3, true));
        
        return httpClient;
         */
    }

}
