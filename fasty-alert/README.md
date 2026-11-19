# Fasty Alert

Android alert companion for FastHand Status, built in Kotlin with Jetpack Compose.

## What it does

- Registers this Android installation with Fast Notifications for the fixed account `fuoahgfiasgfiasfidhgafoahgfhacjakljdfhbcuasocgsadfga@fasthandstudio.asia`.
- Keeps a foreground connection to the notification service's WebSocket stream and fetches queued notifications when it reconnects.
- Displays outage and recovery notices on the high-importance **Outage alerts** channel, with the bundled `alert_tone.wav` sound.
- Acknowledges a queued notification only after Android has accepted it for display.
- Does not contain or receive the Fast Notifications API key. The status server stores and uses that key.

The app's notification sound is selected when Android creates the notification channel the first time. If you later change the bundled sound, users may need to reset the channel in Android settings or reinstall the app.

## Build

Open this directory in Android Studio (JDK 17 or newer) and let Gradle sync, then run the `app` configuration on a device running Android 8.0 or newer. Android 13+ asks for notification permission. Tap **Connect alerts** and keep the app's alert connection enabled. Android battery controls may affect long-running background connections on some devices.

## Backend setup

1. Apply `../supabase/migrations/0005_fasty_alert.sql` after migrations `0001`–`0004` in the existing FastHand Status project.
2. Open `/admin/alerts` and save the Fast Notifications API key.
3. Install Fasty Alert and grant notification permission. Its first connection registers the device to the fixed recipient email.

The status project sends alerts when manual or automated incidents are created, edited, updated, resolved, or deleted. It also alerts on maintenance scheduling, rescheduling, start, completion, cancellation, edits, and deletion. Monitor-triggered incident opening and recovery events are included. Push failures never undo a successfully stored status change. `/admin/alerts` shows whether the key is configured and provides catch-up actions for active incidents and scheduled or in-progress maintenance windows.

## Service endpoints used

- `POST /v1/devices/register`
- `GET /v1/notifications/pending`
- `POST /v1/notifications/:id/ack`
- `WSS /v1/ws?userId=…&deviceId=…`

Push dispatch (`POST /v1/push`) is server-side only and uses the Bearer API key configured in the admin desk.
