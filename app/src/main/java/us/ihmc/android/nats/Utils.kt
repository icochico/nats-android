package us.ihmc.android.nats

import android.os.Build
import android.os.Environment

/**
 * Utils.java
 *
 *
 * Class `Utils` contains convenience functions.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */

object Utils {

    /**
     * Function that returns the *real* SDCard path, therefore replacing /storage/emulated/0/
     * with /sdcard if it's the case
     *
     * @return the path of the external SDCard
     */
    val externalStorageDirectory: String
        get() {

            var sdCard: String
            val devModel = Build.MODEL

            if (devModel == "DROID X2") {
                sdCard = "/sdcard-ext"
            } else if (devModel == "SGH-I987") {
                sdCard = "/sdcard/external_sd"
            } else {
                try {
                    sdCard = Environment.getExternalStorageDirectory().absolutePath
                    if (sdCard.contains("emulated")) {
                        sdCard = Environment.getExternalStorageDirectory().path
                    }
                } catch (e: Exception) {
                    sdCard = Environment.getExternalStorageDirectory().path
                }

            }
            return sdCard
        }

    /**
     * Function that validates a given port.
     *
     * @param port the port to be validated
     *
     * @return true if the port is valid, false otherwise
     */
    internal fun isValidPort(port: String?): Boolean {

        if (port == null || port == "")
            return false

        try {
            val iPort = Integer.parseInt(port)
            if (iPort < 0 || iPort > 65535) {
                return false
            }
        } catch (e: NumberFormatException) {
            return false
        }

        return true
    }


    /**
     * Function that validates a given IPv4 address.
     *
     * @param ipAddress the IPv4 address to be validated
     * @return true if the IPv4 address is valid, false otherwise
     */
    internal fun isValidIPv4(ipAddress: String?): Boolean {

        if (ipAddress == null || ipAddress == "") {
            return false
        }

        val parts = ipAddress.split("\\.".toRegex()).dropLastWhile({ it.isEmpty() }).toTypedArray()
        if (parts.size != 4) {
            return false
        }

        for (s in parts) {
            try {
                val i = Integer.parseInt(s)
                if (i < 0 || i > 255) {
                    return false
                }
            } catch (e: NumberFormatException) {
                return false
            }

        }

        return true
    }

}
