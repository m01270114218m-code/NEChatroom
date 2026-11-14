# Supabase integration (audio disabled)

This Android project now includes a small OkHttp-based Supabase Auth + Data API adapter. It does not initialize or call the NetEase audio SDK. The existing NetEase demo screens are still present; this adapter is the backend layer for the text-first room flow and must be connected to the app screens before the app is a complete Supabase-powered product.

## 1. Configure the Android app

Create or edit `Android/local.properties` (this file is already ignored by Git):

```properties
SUPABASE_URL=https://pkqcxcfuelubonlkjgoq.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
```

Use the project's **publishable** key (`sb_publishable_...`) from Supabase Settings → API Keys. Never put a Supabase secret/service-role key in Android, GitHub, or any client app.

The Gradle build reads these values into `BuildConfig`. If either value is missing, the project still builds and the adapter returns a clear configuration error at runtime.

## 2. Available API methods

Use `SupabaseApi.getInstance()` from Android code:

- `signUp(email, password, username, displayName, callback)`
- `signIn(email, password, callback)`
- `signOut(accessToken, callback)`
- `listActiveRooms(accessToken, callback)`
- `createRoom(accessToken, userId, title, description, roomType, callback)`
- `joinRoom(accessToken, roomId, userId, role, callback)`
- `leaveRoom(accessToken, roomId, userId, callback)`
- `listMessages(accessToken, roomId, callback)`
- `sendMessage(accessToken, roomId, senderId, message, callback)`

All calls are asynchronous. The new launcher screen currently keeps the access token in memory only, so a process restart requires signing in again. Before release, move token persistence to Android Keystore-backed storage and derive the user ID only from the authenticated session.

## 3. Existing database

The connected Supabase project already contains `profiles`, `rooms`, `room_members`, and `chat_messages`, with RLS enabled and authenticated-user policies. A database trigger creates the profile when a new Supabase Auth user is created. The adapter uses those existing tables; it does not create duplicate tables or weaken RLS. The text-only launcher screen now supports email sign-up/sign-in, listing and creating public rooms, joining/leaving rooms, and sending/loading text messages. The chat screen has a manual refresh button; Supabase Realtime subscriptions are not wired yet.

## 4. Audio status

Audio is intentionally not part of this integration step. NetEase RTC/NERoom still requires a separately configured NetEase AppKey, enabled products, and a production backend. Do not expose the demo backend or an AppSecret in the Android client. Audio can be integrated after the text-first authentication, room, and messaging flow is verified.

## 5. Build verification

Open the `Android` directory in Android Studio, sync Gradle, and build the `app` module. This repository integration was prepared through GitHub's file API; an Android SDK/Gradle build was not run in this environment.
