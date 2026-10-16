package com.beardedgoat.goatdash.music

import android.service.notification.NotificationListenerService

/**
 * Exists only so the user can grant GoatDash "notification access", which is how Android
 * lets an app see and control other apps' music. GoatDash doesn't read any notifications.
 */
class MediaListenerService : NotificationListenerService()
