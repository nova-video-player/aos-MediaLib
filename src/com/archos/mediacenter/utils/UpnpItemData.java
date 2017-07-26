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

package com.archos.mediacenter.utils;

import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.support.model.container.Container;
import org.fourthline.cling.support.model.item.Item;


public class UpnpItemData implements Comparable<UpnpItemData> {
    // Add here all the available item types (each item type can have its own layout)
    public static final int ITEM_VIEW_TYPE_FILE = 0;        // File or folder
    public static final int ITEM_VIEW_TYPE_SERVER = 1;      // Media server on the network
    public static final int ITEM_VIEW_TYPE_SHORTCUT = 2;    // Network share shortcut
    public static final int ITEM_VIEW_TYPE_TITLE = 3;       // Title (a single line of text aligned to the left)
    public static final int ITEM_VIEW_TYPE_TEXT = 4;        // Text (a single line of text shifted to the right)
    public static final int ITEM_VIEW_TYPE_LONG_TEXT = 5;   // Long text (several lines of centered text displayed in a much higher tile)
    public static final int ITEM_VIEW_TYPE_COUNT = 6;

    public static final int UPNP_ITEM = 0;
    public static final int UPNP_CONTAINER = 1;
    public static final int UPNP_DEVICE = 2;
    public static final int UPNP_TITLE = 3;
    public static final int UPNP_TEXT = 4;
    public static final int UPNP_SHORTCUT = 5;
    
    private int type;
    private int upnpType;
    private Object data;
    private String name;

    public UpnpItemData(int upnpType, String path) {
        this(upnpType, path, path);
    }

    public UpnpItemData(int upnpType, Object data, String name) {
        super();
        this.upnpType = upnpType;
        this.data = data;
        this.name = name;
        switch(upnpType){
            case UPNP_CONTAINER:
            case UPNP_ITEM:
                this.type = ITEM_VIEW_TYPE_FILE;
                break;
            case UPNP_DEVICE:
                this.type = ITEM_VIEW_TYPE_SERVER;
                break;
            case UPNP_TITLE:
                this.type = ITEM_VIEW_TYPE_TITLE;
                break;
            case UPNP_TEXT:
                this.type = ITEM_VIEW_TYPE_TEXT;
                break;
            case UPNP_SHORTCUT:
                this.type = ITEM_VIEW_TYPE_SHORTCUT;
        }
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getUpnpType() {
        return upnpType;
    }

    public void setUpnpType(int upnpType) {
        this.upnpType = upnpType;
    }

    public Object getData() {
        return data;
    }

    public String getTextData() {
        if (data instanceof String) {
            return (String)data;
        }
        return null;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    public String getPath(){
        if (data instanceof String)
            return (String)data;
        else if (upnpType == UPNP_ITEM){
            return ((Item)data).getFirstResource().getValue();
        }else if (upnpType == UPNP_CONTAINER || upnpType == UPNP_ITEM){
            return ((Container)data).getId();
        } else
            return null;
    }

    public boolean isDirectory(){
        return upnpType == UPNP_CONTAINER;
    }

    public boolean isFile(){
        return upnpType == UPNP_ITEM;
    }

    public boolean isTextItem(){
        return data instanceof String;
    }

    public int compareTo(UpnpItemData another) {
        return name.toLowerCase().compareTo(another.getName().toLowerCase());
    }

    public boolean isDevice() {
        // TODO Auto-generated method stub
        return data instanceof Device;
    }
}
