package com.archos.mediacenter.utils;

import static org.junit.Assert.*;

import org.junit.Test;
import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.item.Item;

public class UpnpItemDataTest {

    /**
     * An item with no <res> element at all. getFirstResource() returns null for
     * such items (Kodi and other servers can send them), which NPE'd before the
     * null check was added.
     */
    private static final String ITEM_WITHOUT_RES =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
        "            xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
        "            xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
        "  <item id=\"1\" parentID=\"0\" restricted=\"1\">" +
        "    <dc:title>No resource</dc:title>" +
        "    <upnp:class>object.item.videoItem</upnp:class>" +
        "  </item>" +
        "</DIDL-Lite>";

    @Test
    public void getPathReturnsNullWhenItemHasNoResource() throws Exception {
        DIDLContent content = new DIDLParser().parse(ITEM_WITHOUT_RES);
        Item item = content.getItems().get(0);
        assertNull("jUPnP returns null for an item without <res>", item.getFirstResource());

        UpnpItemData data = new UpnpItemData(UpnpItemData.UPNP_ITEM, item, "No resource");
        assertNull(data.getPath());
    }
}
