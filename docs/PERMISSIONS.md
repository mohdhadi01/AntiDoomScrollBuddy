# Permissions — what we request and exactly why

| Permission | Kind | Human explanation (shown in onboarding) |
|---|---|---|
| `PACKAGE_USAGE_STATS` (Usage access) | appop via Settings | "Lets your buddy know when a chosen app is open, so it knows when a session starts." |
| `SYSTEM_ALERT_WINDOW` (Display over other apps) | appop via Settings | "Lets the buddy appear on top of Instagram while you scroll. Nothing else." |
| `POST_NOTIFICATIONS` | runtime (33+) | "The quiet 'Buddy is on duty' status notification. Hide it if you like — the buddy works anyway." |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | install-time | Keeps the watcher alive; subtype declared in the manifest with a Play-reviewable justification. |
| `RECEIVE_BOOT_COMPLETED` | install-time | Restarts the watcher after reboot so you never re-setup. |
| `WAKE_LOCK` | install-time | Screen state handling around sessions. |

We do NOT request: location, contacts, storage, camera, microphone, accessibility,
QUERY_ALL_PACKAGES, exact alarms, battery-optimization exemption (candidate for a
future user-initiated step — see ANDROID_PLATFORM.md).

Every request is explained in one honest sentence before the Settings hop, and the
onboarding gates progression on the two appops being granted.
