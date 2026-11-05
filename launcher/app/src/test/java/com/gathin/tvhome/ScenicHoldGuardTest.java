package com.gathin.tvhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ScenicHoldGuardTest {
    @Test public void viewLongClickAndFallbackTimerCountAsOnePress() {
        ScenicHoldGuard guard = new ScenicHoldGuard();
        guard.startPress();
        assertTrue(guard.claim("a", 500));   // View native long-click at approximately 500ms.
        assertFalse(guard.claim("a", 550));  // Fallback timer or remote repeat at approximately 550ms.
        assertFalse(guard.claim("a", 560));
        guard.startPress();
        assertFalse(guard.claim("a", 650)); // Spurious new DOWN from an IR remote.
        guard.startPress();
        assertTrue(guard.claim("a", 1100)); // A new deliberate hold is allowed.
        guard.startPress();
        assertTrue(guard.claim("b", 1150)); // A different tile is independent.
    }
}
