package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Control Center's network capsule reports state honestly; it is a shortcut, not a toggle. */
public class NetworkSummaryTest {
    @Test public void offline() {
        NetworkSummary s = NetworkSummary.of(false, false, false, false, null);
        assertEquals(NetworkSummary.OFFLINE, s.kind);
        assertEquals("Offline", s.title);
    }

    @Test public void ethernetWinsOverWifi() {
        NetworkSummary s = NetworkSummary.of(true, true, true, true, "\"Home\"");
        assertEquals(NetworkSummary.ETHERNET, s.kind);
        assertEquals("Connected", s.detail);
    }

    @Test public void wifiShowsTheNameOnlyWhenAndroidSharesIt() {
        assertEquals("Home", NetworkSummary.of(true, true, false, true, "\"Home\"").detail);
        assertEquals("Connected", NetworkSummary.of(true, true, false, true, "<unknown ssid>").detail);
        assertEquals("Connected", NetworkSummary.of(true, true, false, true, null).detail);
    }

    @Test public void captivePortalOrNoInternetIsSaid() {
        assertEquals("No internet", NetworkSummary.of(true, false, false, true, "\"Cafe\"").detail);
    }

    @Test public void cleanSsid() {
        assertNull(NetworkSummary.cleanSsid("0x"));
        assertNull(NetworkSummary.cleanSsid("  "));
        assertEquals("Studio 5", NetworkSummary.cleanSsid("\"Studio 5\""));
    }
}
