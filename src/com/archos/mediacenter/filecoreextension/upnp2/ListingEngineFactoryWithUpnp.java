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

package com.archos.mediacenter.filecoreextension.upnp2;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.ListingEngineFactory;

/**
 * Add support for UPnP listing to the ListingEngineFactory from FileCoreLibrary
 */
public class ListingEngineFactoryWithUpnp {

    public static ListingEngine getListingEngineForUrl(Context context, Uri uri) {

        if ("upnp".equals(uri.getScheme())) {
            return new UpnpListingEngine(context, uri);
        }
        else {
            return ListingEngineFactory.getListingEngineForUrl(context, uri);
        }
    }
}
