// Copyright 2025 Courville Software
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

package com.archos.mediacenter.filecoreextension.upnp2;

import android.os.Build;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.ServerClientTokens;
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl;
import org.jupnp.transport.impl.ServletStreamServerImpl;
import org.jupnp.transport.impl.jetty.JettyStreamClientImpl;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

/**
 * Custom jUPnP service implementation that uses Nova-specific configuration
 * with a proper User-Agent for DLNA/UPnP server identification.
 */
public class NovaUpnpService extends AndroidUpnpServiceImpl {

    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        final String version = getVersionName();
        return new AndroidUpnpServiceConfiguration() {
            @Override
            public StreamClient createStreamClient() {
                return new JettyStreamClientImpl(
                        new StreamClientConfigurationImpl(getSyncProtocolExecutorService()) {
                            @Override
                            public String getUserAgentValue(int majorVersion, int minorVersion) {
                                ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                                tokens.setOsName("Android");
                                tokens.setOsVersion(Build.VERSION.RELEASE);
                                tokens.setProductName("NovaVideoPlayer");
                                tokens.setProductVersion(version);
                                return tokens.toString();
                            }
                        });
            }

            @Override
            public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
                return new ServletStreamServerImpl(new ServletStreamServerConfigurationImpl(
                        NovaJettyServletContainer.INSTANCE,
                        networkAddressFactory.getStreamListenPort()));
            }
        };
    }

    private String getVersionName() {
        try {
            String packageVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (packageVersion != null && packageVersion.startsWith("v")) {
                packageVersion = packageVersion.substring(1);
            }
            return packageVersion != null ? packageVersion : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
