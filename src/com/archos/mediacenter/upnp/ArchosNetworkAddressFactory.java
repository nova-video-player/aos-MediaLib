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

package com.archos.mediacenter.upnp;

import org.fourthline.cling.android.AndroidNetworkAddressFactory;

import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * Created by alexandre on 29/04/16.
 */
public class ArchosNetworkAddressFactory extends AndroidNetworkAddressFactory {
    public ArchosNetworkAddressFactory(int streamListenPort) {
        super(streamListenPort);
    }

    @Override
    protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
        boolean result = super.isUsableAddress(networkInterface, address);
        if(result) //was ok, no need to check field
            return result;
        //check if field exists
        boolean fieldExists=false;
        try {
            InetAddress.class.getDeclaredField("hostName");
            fieldExists = true;
        } catch (NoSuchFieldException e) {

        }
        if (!fieldExists) {
            //recheck as super super method
            if (!(address instanceof Inet4Address)) {
                return false;
            }
            if (address.isLoopbackAddress()) {
                return false;
            }
            if (useAddresses.size() > 0 && !useAddresses.contains(address.getHostAddress())) {
                return false;
            }
            String hostName = address.getHostAddress();
            try {

                Class<?> innerClass = Class.forName("java.net.InetAddress$InetAddressHolder");
                Field field = InetAddress.class.getDeclaredField("holder");
                field.setAccessible(true);
                Object obj = field.get(address);

                Field field2 = innerClass.getDeclaredField("hostName");
                field2.setAccessible(true);
                field2.set(obj, hostName);
                return true;
            } catch (Exception ex) {
                return false;
            }
        }
        return result;
    }

}
