# Floating Joystick

Circular overlay atop all apps. Drag = move spoofed location. Release = stop. Drag anywhere on screen.

Key files: `:feature:joystick:impl/JoystickOverlayService.kt`, `:feature:joystick:impl/JoystickView.kt`

## Requirements

- `SYSTEM_ALERT_WINDOW` required.
- Uses `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` (mandatory — prevents stealing keyboard focus from foreground app) and `FLAG_NOT_TOUCH_MODAL`.
- Overlay utils shared via `:core:overlay`.
- Outer disc is a light fill at `JoystickConstants.OUTER_ALPHA` (128) so it reads on light apps without going solid on dark ones. The outer ring is a medium gray (`OUTER_BORDER_RGB`) so the pad is visible on a white screen. The stick stays light with a slightly darker gray edge (`KNOB_EDGE_RGB`).

## Movement

- Drag → direction vector × speed (m/s).
- Release eases the knob back to center (180 ms overshoot) then reports zero force.
- New lat/lon via Haversine.
- Pushed to `MockLocationService`.
- Overlay reposition: `View.OnTouchListener` → `WindowManager.LayoutParams`.
- **Manual takeover** (issue #96): moving the stick past the dead zone cancels any walk-to, route replay, roaming/planting or follower sync — playing or paused — plus a road route still being planned for one, then steers from the current position. `JoystickOverlayService` checks `shouldJoystickTakeOver` (`:core:model/MovementPriority.kt`) on each live touch and runs `MapController.stopAutomatedMovement()` (the same `stopWalk()` + `TeleportUseCase.stopAutomatedMovement()` stops a teleport uses); ticks wait for it so nothing competes. A follower also turns **Follow leader** off (same as the Group Sync switch) and stays in the group. Saved routes and preferences are untouched. Releasing an unlocked stick stops at the current position; nothing resumes. Dead-zone touches, release, and dragging the overlay handle never take over, and a retained locked direction is not a new gesture.
- Without a new touch, the stick's ticks (e.g. a locked direction) are still ignored while a **playing** route, **running** roam, walk-to, or follower owns the tick, and steer a paused route or roam without ending it (`shouldIgnoreJoystickInput`). Controls in that state fade toward their background circle instead of changing colour, but stay readable (widget chrome uses ~42% white on black, not a near-match). The widget eye (show/hide) opens or closes the overlay on its own; lock is not required. Lock still shows-then-locks if the overlay is hidden.

## Cleanup

Call `windowManager.removeView` in `onDestroy`, null/attached check.

## Edge Cases

- Revoke `SYSTEM_ALERT_WINDOW` while showing → `removeView` throws. Wrap in try/catch.
- MIUI/ColorOS: overlay perms reset on reboot. Show startup reminder.