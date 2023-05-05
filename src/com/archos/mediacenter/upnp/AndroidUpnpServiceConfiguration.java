/*
 * Copyright (C) 2013 4th Line GmbH, Switzerland
 *
 * The contents of this file are subject to the terms of either the GNU
 * Lesser General Public License Version 2 or later ("LGPL") or the
 * Common Development and Distribution License Version 1 or later
 * ("CDDL") (collectively, the "License"). You may not use this file
 * except in compliance with the License. See LICENSE.txt for more
 * information.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.archos.mediacenter.upnp;

import org.jupnp.DefaultUpnpServiceConfiguration;
import org.jupnp.binding.xml.RecoveringUDA10DeviceDescriptorBinderImpl;
import org.jupnp.model.Namespace;

import org.jupnp.transport.impl.jetty.JettyServletContainer;
import org.jupnp.transport.impl.jetty.JettyStreamClientImpl;
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl;
import org.jupnp.transport.spi.GENAEventProcessor;

import org.jupnp.transport.impl.NetworkAddressFactoryImpl;
import org.jupnp.transport.spi.NetworkAddressFactory;

import org.jupnp.transport.spi.SOAPActionProcessor;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

public class AndroidUpnpServiceConfiguration extends DefaultUpnpServiceConfiguration {

    public AndroidUpnpServiceConfiguration() {
        this(0); // Ephemeral port
    }

    public AndroidUpnpServiceConfiguration(int streamListenPort) {
        //super(streamListenPort, false);
        super(streamListenPort, NetworkAddressFactoryImpl.DEFAULT_MULTICAST_RESPONSE_LISTEN_PORT, false);

        // This should be the default on Android 2.1 but it's not set by default
        //System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver");
    }


    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        return new AndroidNetworkAddressFactory(streamListenPort, multicastResponsePort);
    }

    /*
    @Override
    protected Namespace createNamespace() {
        // For the Jetty server, this is the servlet context path
        return new Namespace("/upnp");
    }
     */


    /*
    @Override
    public StreamClient createStreamClient() {
        // Use Jetty
        return new JettyStreamClientImpl(
            new StreamClientConfigurationImpl(
                getSyncProtocolExecutorService()
            ) {
                @Override
                public String getUserAgentValue(int majorVersion, int minorVersion) {
                    // TODO: UPNP VIOLATION: Synology NAS requires User-Agent to contain
                    // "Android" to return DLNA protocolInfo required to stream to Samsung TV
			        // see: http://two-play.com/forums/viewtopic.php?f=6&t=81
                    ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                    tokens.setOsName("Android");
                    tokens.setOsVersion(Build.VERSION.RELEASE);
                    return tokens.toString();
                }
            }
        );
    }
     */

    /*
    @Override
    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        // Use Jetty, start/stop a new shared instance of JettyServletContainer
        return new AsyncServletStreamServerImpl(
            new AsyncServletStreamServerConfigurationImpl(
                JettyServletContainer.INSTANCE,
                networkAddressFactory.getStreamListenPort()
            )
        );
    }
     */

    @Override
    public int getRegistryMaintenanceIntervalMillis() {
        return 3000; // Preserve battery on Android, only run every 3 seconds
    }

}
