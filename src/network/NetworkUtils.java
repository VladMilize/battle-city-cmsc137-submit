package network;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

public class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Returns the machine's first non-loopback, non-virtual, up IPv4 LAN address.
     * Falls back to "127.0.0.1" if none is found.
     */
    public static String getLanIp() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr.isLoopbackAddress()) continue;
                    String ip = addr.getHostAddress();
                    if (!ip.contains(":")) return ip; // IPv4 only (skip IPv6)
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
