# Privacy

**What the app knows:** which of your *chosen* apps are foreground and when, session
durations, intervention counts, your settings. That is the complete dataset.

**What it can never know:** screen contents, keystrokes, messages, notifications,
browsing content, anything about non-tracked apps beyond "not tracked".

- No network permission exists in the manifest — offline by construction, no telemetry,
  no remote config, no crash reporting.
- All data stays in app-private storage (DataStore + Room); no backup
  (`allowBackup=false`) so sessions never leave the device.
- No accessibility service; nothing inspects other apps' UI.
- Debug logging (session events) is behind `BuildConfig.DEBUG` conventions and must
  never include content or identifiers beyond package names of tracked apps.
- Play Data safety form: data stored locally only, no collection, no sharing.
