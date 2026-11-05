package com.gathin.tvhome;

/** One physical held OK may be reported by both Android's View and the launcher's fallback hold timer. */
final class ScenicHoldGuard {
    private boolean delivered;
    private String lastKey;
    private long lastAt;

    void startPress() { delivered = false; }

    boolean claim(String key, long uptimeMs) {
        if (delivered) return false;
        // Some IR remotes report repeated zero-count DOWN frames as *new* key presses.
        // A second legitimate held OK needs >=550ms, so 420ms cannot block one.
        if (key != null && key.equals(lastKey) && uptimeMs - lastAt >= 0 && uptimeMs - lastAt < 420)
            return false;
        delivered = true;
        lastKey = key;
        lastAt = uptimeMs;
        return true;
    }
}
