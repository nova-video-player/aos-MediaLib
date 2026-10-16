package com.archos.mediacenter.filecoreextension.upnp2;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.item.Item;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UpnpFile2Test {

    /**
     * The exact shape of a <res> element that Kodi sends: no `size` attribute,
     * only `duration` and `protocolInfo`. If this changes, our regression
     * coverage is stale.
     */
    private static final String KODI_DIDL =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
        "            xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "            xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
        "  <item id=\"1\" parentID=\"0\" restricted=\"1\">" +
        "    <dc:title>Trespasses - S01E01 : Episode 1</dc:title>" +
        "    <upnp:class>object.item.videoItem.videoBroadcast</upnp:class>" +
        "    <res duration=\"0:55:49.000\" " +
        "         protocolInfo=\"http-get:*:video/x-matroska:DLNA.ORG_OP=01;DLNA.ORG_CI=0\">" +
        "      http://192.168.1.4:1778/x/Trespasses%20-%20S01E01.mkv" +
        "    </res>" +
        "  </item>" +
        "</DIDL-Lite>";

    /**
     * Documents jUPnP's behaviour: when `size` is absent, getSize() returns
     * null rather than 0L. This is what UpnpFile2 trips over.
     */
    @Test
    public void jupnpReturnsNullSizeForKodiStyleRes() throws Exception {
        DIDLContent content = new DIDLParser().parse(KODI_DIDL);
        Item item = content.getItems().get(0);
        Res res = item.getFirstResource();
        assertNotNull(res);
        assertNull("Kodi omits size, so jUPnP returns null",
                   res.getSize());
    }

    /**
     * The actual regression test. Fails today with NullPointerException
     * inside UpnpFile2.<init> at the unboxing of getSize().
     */
    @Test
    public void upnpFile2ConstructsEvenWhenResHasNoSize() throws Exception {
        DIDLContent content = new DIDLParser().parse(KODI_DIDL);
        Item item = content.getItems().get(0);
        Res res = item.getFirstResource();

        UpnpFile2 file = new UpnpFile2(
                item,
                "video/x-matroska",
                Uri.parse("upnp://0/0"),
                res.getValue(),
                null);

        assertEquals("Trespasses - S01E01 : Episode 1", file.getName());
        assertEquals(-1L, file.length());       // sentinel for unknown size
        assertFalse(file.isDirectory());
        assertTrue(file.isFile());
    }
}
