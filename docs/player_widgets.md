# Home screen player widgets

Sistrum includes a native home screen player for Android,
inspired by Apple Music. Widgets share the existing playback queue and audio
handler. They display the current artwork, track, artist, and playback state.

## Adding and customizing

- **Android:** open the launcher's widget picker and add **Sistrum Player**.
  Resize it for a compact, horizontal, or large artwork layout. Open the widget's
  configuration to choose artwork colors, light, or dark, and toggle the artist
  name. Reconfiguration availability depends on the launcher.
- Choose a track in the app first. Tap the widget's artwork or text to open
  the full player. An empty widget opens the app with transport disabled.

Artwork is a static thumbnail. Updates follow track and transport changes;
there is no continuous timer or animated artwork in the home screen widget.
The operating system controls when widget updates appear.

## Native integration

`PlayerWidgetService` publishes presentation data through
`com.amwolfstein.sistrum/player_widget`. Artwork loading discards stale results when
tracks change. Commands are serialized and publish the latest state before
acknowledging completion. A restored session starts paused and uses the same
audio handler as the app.

Android uses `RemoteViews` and per-widget preferences. Controls call the active
Flutter engine. If the process has been killed, a control opens the activity to
restore the saved queue and execute the command.

## Verification

- `flutter test test/player_widget_service_test.dart`
- `flutter analyze lib/services/player_widget_service.dart lib/main.dart`
- Build Android and run `PlayerWidgetTest` on an emulator to apply real
  `RemoteViews` for all layout sizes and themes.
- On devices, add widgets and check play/pause, skip, opening the player,
  cold launch, resizing, appearance, and cover changes while backgrounded.
