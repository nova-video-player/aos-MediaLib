package com.archos.mediacenter.filecoreextension.upnp2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;

import static org.robolectric.Shadows.shadowOf;

import org.jupnp.UpnpService;
import org.jupnp.android.AndroidUpnpService;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.meta.Action;
import org.jupnp.model.meta.Device;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.RemoteService;
import org.jupnp.model.types.UDAServiceId;
import org.jupnp.registry.Registry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 29, manifest = Config.NONE, application = Application.class)
public class UpnpServiceManagerRegistryReplayTest {
    private UpnpServiceManager manager;
    private Registry registry;
    private IBinder binder;
    private final List<List<Device>> publications = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        Constructor<UpnpServiceManager> constructor =
                UpnpServiceManager.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        manager = constructor.newInstance(RuntimeEnvironment.getApplication());
        manager.addListener(devices -> publications.add(new ArrayList<>(devices)));
        registry = mock(Registry.class);
        when(registry.getDevices()).thenReturn(Collections.emptyList());
        when(registry.getRemoteDevices()).thenReturn(Collections.emptyList());
        UpnpService service = mock(UpnpService.class);
        when(service.getRegistry()).thenReturn(registry);
        ControlPoint controlPoint = mock(ControlPoint.class);
        binder = mock(IBinder.class, withSettings().extraInterfaces(AndroidUpnpService.class));
        AndroidUpnpService androidService = (AndroidUpnpService) binder;
        when(androidService.get()).thenReturn(service);
        when(androidService.getRegistry()).thenReturn(registry);
        when(androidService.getControlPoint()).thenReturn(controlPoint);
    }

    @Test
    public void listsServerDiscoveredBeforeListenerAttachment() {
        RemoteDevice server = server();
        when(registry.getDevices()).thenReturn(Collections.<Device>singletonList(server));
        connect();
        assertSame(server, manager.getDeviceByKey(server.hashCode()));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Collections.singletonList(server), publications.get(publications.size() - 1));
    }

    @Test
    public void callbackOverlappingSnapshotDoesNotDuplicateServer() {
        RemoteDevice server = server();
        when(registry.getDevices()).thenAnswer(invocation -> {
            manager.mRegistryListener.remoteDeviceAdded(registry, server);
            return Collections.<Device>singletonList(server);
        });
        connect();
        manager.mRegistryListener.remoteDeviceAdded(registry, server);
        assertEquals(1, manager.getDevices().size());
        assertSame(server, manager.getDeviceByKey(server.hashCode()));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Collections.singletonList(server), publications.get(publications.size() - 1));
    }

    @Test
    public void normalDiscoveryStillAddsServerAfterBinding() {
        connect();
        RemoteDevice server = server();
        manager.mRegistryListener.remoteDeviceAdded(registry, server);
        assertSame(server, manager.getDeviceByKey(server.hashCode()));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Collections.singletonList(server), publications.get(publications.size() - 1));
    }

    @Test
    public void replayPublishesOneListForMultipleServers() {
        shadowOf(Looper.getMainLooper()).idle();
        publications.clear();
        RemoteDevice first = server();
        RemoteDevice second = server();
        when(registry.getDevices()).thenReturn(Arrays.<Device>asList(first, second));
        connect();
        assertEquals(2, manager.getDevices().size());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(1, publications.size());
        assertEquals(new HashSet<>(Arrays.asList(first, second)), new HashSet<>(publications.get(0)));
    }

    @Test
    public void replayPreservesExistingEligibilityFilter() {
        RemoteDevice unnamed = server();
        when(unnamed.getDisplayString()).thenReturn(null);
        RemoteDevice noContent = mock(RemoteDevice.class);
        when(noContent.getDisplayString()).thenReturn("No content service");
        RemoteDevice noBrowse = server();
        when(noBrowse.findService(any(UDAServiceId.class)).getAction("Browse"))
                .thenReturn(null);
        when(registry.getDevices()).thenReturn(Arrays.<Device>asList(unnamed, noContent, noBrowse));
        connect();
        assertEquals(0, manager.getDevices().size());
    }

    @Test
    public void removesServerReplayedFromRegistry() {
        RemoteDevice server = server();
        when(registry.getDevices()).thenReturn(Collections.<Device>singletonList(server));
        connect();
        assertSame(server, manager.getDeviceByKey(server.hashCode()));
        manager.mRegistryListener.remoteDeviceRemoved(registry, server);
        assertNull(manager.getDeviceByKey(server.hashCode()));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(publications.get(publications.size() - 1).isEmpty());
    }

    @Test
    public void concurrentRemovalCannotBeUndoneBySnapshotReplay() throws Exception {
        RemoteDevice server = server();
        CountDownLatch removing = new CountDownLatch(1);
        CountDownLatch removed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread removal = new Thread(() -> {
            removing.countDown();
            try {
                manager.mRegistryListener.remoteDeviceRemoved(registry, server);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                removed.countDown();
            }
        }, "registry-removal-test");
        when(registry.getDevices()).thenAnswer(invocation -> {
            removal.start();
            assertTrue(removing.await(2, TimeUnit.SECONDS));
            // The snapshot is not yet replayed: removal must remain ordered after it.
            assertFalse(removed.await(100, TimeUnit.MILLISECONDS));
            return Collections.<Device>singletonList(server);
        });
        connect();
        assertTrue(removed.await(2, TimeUnit.SECONDS));
        removal.join(2000);
        assertNull(failure.get());
        assertNull(manager.getDeviceByKey(server.hashCode()));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(publications.get(publications.size() - 1).isEmpty());
    }

    private void connect() {
        manager.mServiceConnection.onServiceConnected(
                new ComponentName("test", "test.Service"), binder);
    }

    private RemoteDevice server() {
        RemoteDevice device = mock(RemoteDevice.class, RETURNS_DEEP_STUBS);
        when(device.getDisplayString()).thenReturn("Test server");
        RemoteService content = mock(RemoteService.class);
        when(device.findService(any(UDAServiceId.class))).thenReturn(content);
        when(content.getAction("Browse")).thenReturn(mock(Action.class));
        return device;
    }
}
