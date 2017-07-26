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

import android.os.AsyncTask;

import org.fourthline.cling.UpnpService;
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration;
import org.fourthline.cling.android.AndroidUpnpServiceImpl;

public class MediacenterUpnpService extends AndroidUpnpServiceImpl {

    protected AndroidUpnpServiceConfiguration createConfiguration(Object manager) {
        return new AndroidUpnpServiceConfiguration() {

            /* This is optimization for Android device
             * 1.set registry maintenance interval time
             * 2.If you are not writing a control point but a server application, you can 
             *   return null in the getExclusiveServiceTypes() method. This will disable 
             *   discovery completely, now all device and service advertisements are dropped 
             *   as soon as they are received.
             * 3.This is a control point.So,we need selective discovery of UPnP devices.
             *    If instead we return an empty array (the default behavior), all services 
             *    and devices will be discovered and no advertisements will be dropped.
             */

            /* The only purpose of this class is to show you how you'd
             * configure the AndroidUpnpServiceImpl in your application:
             */
            @Override
            public int getRegistryMaintenanceIntervalMillis() {
                return 7000;
            }

           /*@Override
           public ServiceType[] getExclusiveServiceTypes() {
               return new ServiceType[] {
                       new UDAServiceType("RenderingControl"),
                       new UDAServiceType("AVTransport")
               };
           }*/

        };
    }

    @Override
    public void onDestroy() {

    //TODO check replacment or full removal
      //  if (isListeningForConnectivityChanges()){
      //      unregisterReceiver(((AndroidWifiSwitchableRouter) upnpService.getRouter()).getBroadcastReceiver());
      //  }

        new Shutdown().execute(upnpService);

        upnpService.getRegistry().removeDevice(UpnpManager.udn);
//        UpnpApp application = (UpnpApp)getApplication();
//        application.CancelNotification();
    }
    /**
     * Do shutdown in separate thread else android.os.NetworkOnMainThreadException
     */
    class Shutdown extends AsyncTask<UpnpService, Void, Void>{
        @Override
        protected Void doInBackground(UpnpService... svcs) {
            UpnpService svc = svcs[0];
            if (null != svc) {
                try{
                    svc.shutdown();
                }
                catch(java.lang.IllegalArgumentException ex){}
            }
            return null;
        }
    }
}
