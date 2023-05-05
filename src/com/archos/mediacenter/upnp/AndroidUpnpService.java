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

import org.jupnp.UpnpService;
import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.protocol.ProtocolFactory;
import org.jupnp.registry.Registry;
import org.jupnp.transport.Router;

/**
 * Interface of the Android UPnP application service component.
 * <p>
 * Usage example in an Android activity:
 * </p>
 * <pre>{@code
 *AndroidUpnpService upnpService;
 *
 *ServiceConnection serviceConnection = new ServiceConnection() {
 *     public void onServiceConnected(ComponentName className, IBinder service) {
 *         upnpService = (AndroidUpnpService) service;
 *     }
 *     public void onServiceDisconnected(ComponentName className) {
 *         upnpService = null;
 *     }
 *};
 *
 *public void onCreate(...) {
 * ...
 *     getApplicationContext().bindService(
 *         new Intent(this, AndroidUpnpServiceImpl.class),
 *         serviceConnection,
 *         Context.BIND_AUTO_CREATE
 *     );
 *}}</pre>
 *<p>
 * The default implementation requires permissions in <code>AndroidManifest.xml</code>:
 * </p>
 * <pre>{@code
 *<uses-permission android:name="android.permission.INTERNET"/>
 *<uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
 *<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE"/>
 *<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
 *<uses-permission android:name="android.permission.WAKE_LOCK"/>
 *}</pre>
 * <p>
 * You also have to add the application service component:
 * </p>
 * <pre>{@code
 *<application ...>
 *  ...
 *  <service android:name="org.jupnp.android.AndroidUpnpServiceImpl"/>
 *</application>
 * }</pre>
 *
 * @author Christian Bauer
 */
// DOC:CLASS
public interface AndroidUpnpService {

    public UpnpServiceConfiguration getConfiguration();

    public ControlPoint getControlPoint();

    public ProtocolFactory getProtocolFactory();

    public Registry getRegistry();

    public Router getRouter();

    /**
     * Stopping the UPnP stack.
     * <p>
     * Clients are required to stop the UPnP stack properly. Notifications for
     * disappearing devices will be multicast'ed, existing event subscriptions cancelled.
     * </p>
     */
    public void shutdown();

    static public class Start {}

    static public class Shutdown {}

    public void startup();
}
// DOC:CLASS
