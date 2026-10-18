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

package com.archos.mediacenter.filecoreextension.upnp2;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

import org.jupnp.android.AndroidNetworkAddressFactory;
import org.jupnp.transport.spi.InitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Network address factory that survives a failing network interface enumeration.
 * <p>
 * {@link AndroidNetworkAddressFactory#discoverNetworkInterfaces()} is called from the
 * {@code NetworkAddressFactoryImpl} constructor, before the factory's {@code logger} instance
 * field is initialized. When the first enumeration attempt throws, which happens on devices where
 * an interface disappears while it is enumerated (Android bug 33661), the override crashes with a
 * {@code NullPointerException} while logging the failure instead of running its retry. That takes
 * down the UPnP service and, since the service runs in the app process, the app itself.
 * {@code isUsableAddress} has the same defect in its own failure path. This class does that
 * bookkeeping with a static logger so the retry and the skip behave as intended.
 * </p>
 * <p>
 * Drop this class once jUPnP initializes these loggers statically
 * (see nova-video-player/aos-AVP#1993).
 * </p>
 */
public class NovaNetworkAddressFactory extends AndroidNetworkAddressFactory {

    private static final Logger logger = LoggerFactory.getLogger(NovaNetworkAddressFactory.class);

    public NovaNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        super(streamListenPort, multicastResponsePort);
    }

    /**
     * A failing per-interface check must skip that interface, not abort the whole enumeration.
     */
    @Override
    protected boolean isUsableNetworkInterface(NetworkInterface networkInterface) throws Exception {
        try {
            return super.isUsableNetworkInterface(networkInterface);
        } catch (Exception e) {
            logger.warn("Skipping network interface that failed while being enumerated", e);
            return false;
        }
    }

    /**
     * Same defect in the address check: its failure is caught by jUPnP, but logged through the
     * field that is still null inside the constructor, where the address discovery runs.
     */
    @Override
    protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
        try {
            return super.isUsableAddress(networkInterface, address);
        } catch (RuntimeException e) {
            logger.warn("Skipping network address for which the Android hostname workaround failed", e);
            return false;
        }
    }

    /**
     * Replaces jUPnP's retry, which can only log through the not yet initialized field while the
     * constructor is running.
     */
    @Override
    protected void discoverNetworkInterfaces() throws InitializationException {
        try {
            super.discoverNetworkInterfaces();
        } catch (RuntimeException e) {
            logger.warn("Network interface enumeration failed, retrying once", e);
            enumerateNetworkInterfaces();
        }
    }

    private void enumerateNetworkInterfaces() throws InitializationException {
        synchronized (networkInterfaces) {
            networkInterfaces.clear();
        }
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                logger.trace("Analyzing network interface: {}", networkInterface.getDisplayName());
                if (isUsableNetworkInterface(networkInterface)) {
                    synchronized (networkInterfaces) {
                        networkInterfaces.add(networkInterface);
                    }
                }
            }
        } catch (Exception e) {
            throw new InitializationException("Could not analyze local network interfaces", e);
        }
    }
}
