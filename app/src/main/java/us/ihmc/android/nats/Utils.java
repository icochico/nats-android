package us.ihmc.android.nats;

import android.os.Build;
import android.os.Environment;

/**
 * Utils.java
 * <p>
 * Class <code>Utils</code> contains convenience functions.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */

public class Utils {

    /**
     * Function that validates a given port.
     *
     * @param port the port to be validated
     *
     * @return true if the port is valid, false otherwise
     */
    public static boolean isValidPort(String port) {

        if (port == null || port.equals(""))
            return false;

        try {
            int iPort = Integer.parseInt(port);
            if (iPort < 0 || iPort > 65535) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        return true;
    }


    /**
     * Function that validates a given IPv4 address.
     *
     * @param ipAddress the IPv4 address to be validated
     * @return true if the IPv4 address is valid, false otherwise
     */
    public static boolean isValidIPv4(String ipAddress) {

        if (ipAddress == null || ipAddress.equals("")) {
            return false;
        }

        String[] parts = ipAddress.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String s : parts) {
            try {
                int i = Integer.parseInt(s);
                if ((i < 0) || (i > 255)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }

        }

        return true;
    }

    /**
     * Function that returns the *real* SDCard path, therefore replacing /storage/emulated/0/
     * with /sdcard if it's the case
     *
     * @return the path of the external SDCard
     */
    public static String getExternalStorageDirectory() {

        String sdCard;
        String devModel = Build.MODEL;

        if (devModel.equals("DROID X2")) {
            sdCard = "/sdcard-ext";
        } else if (devModel.equals("SGH-I987")) {
            sdCard = "/sdcard/external_sd";
        } else {
            try {
                sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
                if (sdCard.contains("emulated")) {
                    //log.warn("Found emulated external storage, forcing /sdcard");
                    sdCard = "/sdcard";
                }
            } catch (Exception e) {
                sdCard = "/sdcard";
            }
        }

        //log.info("SD card path is: " + sdCard);
        return sdCard;
    }

}
