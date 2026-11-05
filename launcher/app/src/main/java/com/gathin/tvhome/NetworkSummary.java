package com.gathin.tvhome;

/**
 * What Control Center's network capsule says. It is a shortcut to the TV's network settings, never a toggle:
 * an app cannot switch Wi-Fi on Android 10+ without privileged access, so the capsule only reports state.
 * Pure, so it is tested on the JVM.
 */
final class NetworkSummary {
    static final int OFFLINE = 0, WIFI = 1, ETHERNET = 2, OTHER = 3;

    final int kind;
    final String title, detail;

    private NetworkSummary(int kind, String title, String detail) {
        this.kind = kind;
        this.title = title;
        this.detail = detail;
    }

    /**
     * @param ssid the raw name from WifiInfo, quoted or "<unknown ssid>" when Android withholds it
     */
    static NetworkSummary of(boolean online, boolean validated, boolean ethernet, boolean wifi, String ssid) {
        if (!online) return new NetworkSummary(OFFLINE, "Offline", "Network settings");
        String state = validated ? "Connected" : "No internet";
        if (ethernet) return new NetworkSummary(ETHERNET, "Ethernet", state);
        if (wifi) {
            String name = cleanSsid(ssid);
            return new NetworkSummary(WIFI, "Wi-Fi", validated && name != null ? name : state);
        }
        return new NetworkSummary(OTHER, "Network", state);
    }

    /** The readable network name, or null when Android hides it (no location access, or none joined). */
    static String cleanSsid(String ssid) {
        if (ssid == null) return null;
        String s = ssid.replace("\"", "").trim();
        if (s.isEmpty() || s.contains("unknown ssid") || s.equals("0x") || s.equals("<unknown ssid>")) return null;
        return s;
    }
}
