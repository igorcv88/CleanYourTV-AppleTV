package com.gathin.tvhome;

import android.service.notification.NotificationListenerService;

/**
 * Exists only so the owner can grant notification-listener access, which Android requires before a launcher
 * may read other apps' active media sessions (MediaSessionManager.getActiveSessions). It reads no
 * notifications. Without the grant, Glass Current stays hidden and Settings offers the permission.
 */
public class MediaAccessService extends NotificationListenerService {
}
