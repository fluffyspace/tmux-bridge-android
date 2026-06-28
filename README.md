# tmux-bridge (Android client)

**Start and manage Claude Code agents on your dev box, straight from your phone.**

A native Android app that pairs with the
[tmux-bridge](https://github.com/fluffyspace/tmux-bridge) daemon to spin up,
list and kill remote [Claude Code](https://docs.claude.com/en/docs/claude-code)
`tmux` sessions over your own VPN/LAN. Each session runs
`claude --remote-control`, so once it's up you hop into **Claude's official app**
to do the actual coding — this app is the launcher and switchboard for the agents
running on your real machine.

No cloud, no accounts, no telemetry: it talks plain HTTP to *your* daemon using a
bearer token obtained during a one-time 6-digit pairing. Built with Jetpack
Compose + Material 3, stdlib-only networking, and it's F-Droid-ready.

## What it does

- **Add a computer** — enter its VPN IP and port, send a pairing request, and
  wait while the operator confirms with `tmux-bridge pair <code>` on the server.
- **Persist paired computers** — name, IP, port, and auth token are stored in
  app-private storage.
- **List sessions** — for each computer, fetch and show the active tmux sessions
  (name, attached/detached, window count).
- **Create a session** — optional name (auto-generated if blank); the daemon
  prefixes it with the machine's short hostname (e.g. `kodba-refactor-auth`).
  Optionally set a working directory, with recently-used paths offered as
  tappable chips and live validation against the server as you type.
- **Kill a session** — with a confirmation dialog.

## UI

Two levels:

1. **Computers** — the list of paired computers (`+` to pair, trash icon to
   remove / revoke the token).
2. **Sessions** — the session list for the selected computer, with create (`+`)
   and kill actions and a refresh button.

## Daemon protocol used

| Method | Path | Auth | Used for |
|---|---|---|---|
| `POST` | `/pair` | no | start pairing → `{request_id, pairing_code}` |
| `GET` | `/pair/<id>` | no | poll until `{status: approved, token}` |
| `GET` | `/sessions` | Bearer | list sessions |
| `POST` | `/sessions` | Bearer | create `{ "name"?: "...", "path"?: "/abs" }` |
| `DELETE` | `/sessions/<name>` | Bearer | kill |
| `GET` | `/session-paths` | Bearer | recent working directories for the chips |
| `GET` | `/check-path?path=…` | Bearer | live validation of a typed path |
| `DELETE` | `/devices/self` | Bearer | revoke token on remove |

## Tech

- Native Kotlin, **Jetpack Compose** + Material 3.
- Single-activity (`MainActivity`); the two-level navigation is a lightweight
  Compose back stack — no `navigation-compose` dependency.
- Networking via `HttpURLConnection` (stdlib); JSON via `org.json` (stdlib) —
  no third-party HTTP or JSON library.
- `minSdk` 26, `targetSdk` / `compileSdk` 35.

## Build

The system JDK is too new for the Android Gradle Plugin; build with the JDK
bundled in Android Studio (Java 21):

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Security note

Communication is plain HTTP and cleartext traffic is permitted app-wide, because
the server IP is entered at runtime and the daemon is designed for a trusted
private network (LAN / WireGuard / Tailscale). Do not expose either end to the
public internet.
