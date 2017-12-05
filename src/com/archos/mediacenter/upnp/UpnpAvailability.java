package com.archos.mediacenter.upnp;

/**
 * Created by alexandre on 05/12/17.
 */

public class UpnpAvailability {
    public static boolean isUpnpAvaialbe(){
        try {
            Class.forName( "org.fourthline.cling.Archos" );
            return false;
        } catch( ClassNotFoundException e ) {
            return true;
        }
    }
}
