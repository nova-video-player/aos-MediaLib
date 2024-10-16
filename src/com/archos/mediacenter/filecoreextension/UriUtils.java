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

package com.archos.mediacenter.filecoreextension;

import static org.apache.hc.core5.net.InetAddressUtils.isIPv4Address;
import static org.apache.hc.core5.net.InetAddressUtils.isIPv6Address;

import android.net.Uri;

import com.archos.filecorelibrary.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by alexandre on 25/06/15.
 */
public class UriUtils {

    private static final Logger log = LoggerFactory.getLogger(UriUtils.class);

    public final static List<String> networkSharesTypes = List.of("ftp", "sftp", "ftps", "sshj", "smb", "smbj", "webdav", "webdavs", "upnp");
    private final static int maxUriType = networkSharesTypes.size();

    /*
      index only implemented schemes
   */
    public static List<String> sImplementedByFileCore = new ArrayList<>();
    public static List<String> sIndexableSchemes = new ArrayList<>();
    static{
        sImplementedByFileCore.addAll(networkSharesTypes);
        sImplementedByFileCore.add("content");
        sIndexableSchemes.addAll(sImplementedByFileCore);
        sIndexableSchemes.add("http");
        sIndexableSchemes.add("https");
    }

    public static int getNumberUriTypes() {
        return maxUriType;
    }

    public static boolean isValidUriType(int type) {
        return (type > -1 && type < getNumberUriTypes());
    }

    public static boolean isFtpBasedProtocol(int type) {
        if (type > maxUriType || type < 0) return false;
        return networkSharesTypes.get(type).contains("ftp");
    }

    // FQDN criteria:
    // - FQDN is limited to 255 chars
    // - DNS labels should contain be a-z | A-Z | 0-9 and hyphen(-)
    // - DNS label should be between 1 and 63 characters long
    // - TLD must be at least two characters, and a maximum of 6 characters
    // - DNS label should not start or end with hyphen (-) (e.g. -google.com or google-.com)
    // - DNS label can be a subdomain (e.g. mkyong.blogspot.com)
    private static final String FQDN_PATTERN = "^(?=.{1,255}$)((?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,63}$";

    private static final String SMB_SERVER_PATTERN = "^(?=.{1,15}$)[A-Za-z0-9-]{1,15}$";

    public static boolean isValidFqdn(String hostname) {
        if (hostname == null) return false;
        Pattern pattern = Pattern.compile(FQDN_PATTERN);
        Matcher matcher = pattern.matcher(hostname);
        return matcher.matches();
    }

    public static boolean isValidSmbServer(String hostname) {
        if (hostname == null) return false;
        Pattern pattern = Pattern.compile(SMB_SERVER_PATTERN);
        Matcher matcher = pattern.matcher(hostname);
        return matcher.matches();
    }

    public static boolean isValidHost(String hostname) {
        if (hostname == null) return false;
        return isValidFqdn(hostname) || isIPv4Address(hostname) || isIPv6Address(hostname) || isValidSmbServer(hostname);
    }

    public static boolean isValidPath(String pathname) {
        if (pathname == null) return false;
        if ("/".equals(pathname)) return true;
        if (pathname.endsWith("/"))
            pathname = pathname.substring(0, pathname.length() - 1);
        File file = new File(pathname);
        try {
            String canonicalPath = file.getCanonicalPath();
            log.debug("isValidPath: input=" + pathname + " -> canonicalPath=" + canonicalPath);
            return canonicalPath.equals(pathname);
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isValidStringUri(String uriString) {
        if (uriString == null) return false;
        try {
            URI uri = new URI(uriString);
            String scheme = uri.getScheme();
            if (sIndexableSchemes.contains(scheme)) {
                String path = uri.getPath();
                int port = uri.getPort();
                String host = uri.getHost();
                log.debug("isValidStringUri: input=" + uriString + " -> scheme=" + scheme + " host=" + host + " port=" + port + " path=" + path);
                return isValidPath(path) && isValidHost(host) && isValidPort(port);
            }
            log.warn("isValidStringUri: input=" + uriString + " -> scheme=" + scheme + " not supported");
            return false;
        } catch (URISyntaxException e) {
            log.error("isValidStringUri: input=" + uriString + " -> URISyntaxException", e);
            return false;
        }
    }

    public static boolean isValidPort(String input) {
        try {
            int port = Integer.parseInt(input);
            // Check if the port is within the valid range (0-65535)
            return port >= 0 && port <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidPort(int port) {
        return port >= -1 && port <= 65535;
    }

    public static boolean allowsEmptyCredential(String scheme) {
        return "webdav".equals(scheme)||"webdavs".equals(scheme)||
                "smb".equals(scheme)||"smbj".equals(scheme);
    }

    // sftp requires username
    public static boolean emptyCredentialMeansAnonymous(String scheme) {
        return "webdav".equals(scheme)||"webdavs".equals(scheme)||"ftp".equals(scheme)||"ftps".equals(scheme);
    }

    public static boolean requiresDomain(String scheme) {
        if (scheme == null) return false;
        return scheme.startsWith("smb");
    }

    public static boolean requiresDomain(int position) {
        if (position > maxUriType || position < 0) return false;
        return networkSharesTypes.get(position).startsWith("smb");
    }

    public static boolean isIndexable(Uri uri){
        // allows only indexing for shares as in smb://[user:pass@]server/share/
        String path = uri != null ? uri.toString() : null;
        if (path == null) return false;
        // valid paths contain at least 3x (not 4!) '/' e.g. "smb://server/share"
        int len = path.length();
        int slashCount = 0;
        for (int i = 0; i < len; i++) {
            if (path.charAt(i) == '/') {
                slashCount++;
            }
        }
        return (isImplementedByFileCore(uri)||isWebUri(uri)) && (slashCount >= 3);
    }

    public static boolean isWebUri(Uri uri){
        if (uri == null || uri.getScheme() == null) return false;
        return uri.getScheme().equals("https")||
                uri.getScheme().equals("http");
    }

    /**
     * returns if it has been implemented by filecore
     * @param uri
     * @return
     */
    public static boolean isImplementedByFileCore(Uri uri){
        if (uri == null) return false;
        if (FileUtils.isLocal(uri)) return true;
        if (uri.getScheme() == null) return false;
        return sImplementedByFileCore.contains(uri.getScheme());
    }

    public static Integer getUriType(Uri uri){
        // -1 if not found
        return networkSharesTypes.indexOf(uri.getScheme());
    }

    public static String getTypeUri(Integer type) throws IllegalArgumentException {
        if (type < 0 || type > maxUriType)
            throw new IllegalArgumentException("Invalid network type " + type);
        return networkSharesTypes.get(type);
    }

    public static boolean isCompatibleWithRemoteDB(Uri uri) {
        return isImplementedByFileCore(uri)&&!"upnp".equals(uri.getScheme());
    }

    public static boolean isContentUri(Uri uri) {
        return "content".equals(uri.getScheme());
    }
}
