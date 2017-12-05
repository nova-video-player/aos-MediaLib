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

import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;

/**
 * Created by alexandre on 29/04/16.
 */
public class ArchosUpnpServiceConfiguration extends AndroidUpnpServiceConfiguration {
    ArchosUpnpServiceConfiguration() {
        super();
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort) {
        return new ArchosNetworkAddressFactory(streamListenPort);
    }

}
