# Design System

## Design Philosophy
DualBoost Downloader embraces Material 3 Expressive ("Material You") design principles, tailored specifically for Android 17+ on Google Pixel devices and future-proofed for Android 18 and 19. The interface emphasizes functional clarity, physical network transparency, dynamic wallpaper harmonization, and tactile feedback.

## Visual Direction & Brand Identity
- **Pixel Native Material You**: Full dynamic color extraction (`dynamicDarkColorScheme` / `dynamicLightColorScheme`) that adapts in real-time to the user's Pixel wallpaper.
- **Adaptive App Icon (Google Material 3 Native)**:
  - Background: Clean, crisp pure white background (`#FFFFFF`) matching Google Pixel system apps (`res/drawable/ic_launcher_background.xml`).
  - Foreground: Google-style geometric curves featuring converging Electric Cyan/Azure (`#00B0FF` / `#0288D1`) and Neon Violet/Purple (`#B388FF` / `#651FFF`) streams meeting at a central downward boost arrow with a clean white lightning core (`res/drawable/ic_launcher_foreground.xml`), calibrated within the 72dp safe zone.
  - Monochrome Layer: High-contrast single-color silhouette (`res/drawable/ic_launcher_monochrome.xml`) providing dynamic Monet tinting for Pixel Themed Icons.
- **Edge-to-Edge System Integration**: 100% transparent status bar and gesture navigation pill bar utilizing `enableEdgeToEdge()` and `WindowInsets.safeDrawing`.

## Colors & Themes
### Material You Dynamic Theming (Pixel Native Monet Engine)
The application fully embraces Material You dynamic color extraction (`dynamicColor = true`), harmonizing with the user's Pixel wallpaper and automatically switching between dynamic dark and dynamic light themes based on system Day/Night mode:
- **Background & Scaffold**: Adapts directly to `MaterialTheme.colorScheme.background`.
- **Surfaces & Cards Hierarchy**: Material 3 surface elevation hierarchy eliminates flat pitch-black clipping in Dark Mode:
  - Deepest tier: `surfaceContainerLowest` for unselected/dormant cards.
  - Base cards: `surfaceContainer` for primary content containers ("New Download", "Ready to Download", "Downloading", "Paused").
  - Elevated surfaces: `surfaceContainerHigh` for hero speed telemetry and info banners.
  - Tonal controls: `surfaceContainerHighest` for URL textfield input fills and inactive preset benchmark chips.
- **Accented Card Borders**: Subtle 1dp `BorderStroke` (`outlineVariant.copy(alpha = 0.35f)` for base cards, `outlineVariant.copy(alpha = 0.5f)` for inactive cards, and interface-tinted glows `activeColor.copy(alpha = 0.35f)` for active Wi-Fi and 5G) delivering crisp edge definition against deep dark backgrounds.
- **Action Button ("Boost Download")**: Full-width filled `Button` using `MaterialTheme.colorScheme.primary` and `onPrimary`.
- **Context-Aware Aggregation Banner**: Replaces static pills with a 4-state dynamic banner adapting container color and messaging to real-time network states:
  - *Dual Active*: `primaryContainer` with 1dp border ("Dual-Channel Active: Aggregating Wi-Fi + 5G").
  - *Both Offline*: `errorContainer` alert ("No Network Available: Reconnect Wi-Fi or Cellular to download.").
  - *Wi-Fi Only*: `surfaceContainerHigh` tip ("Using Wi-Fi only: Enable 'Mobile data always active' in Developer Options to aggregate 5G.").
  - *5G Only*: `surfaceContainerHigh` tip ("Using 5G Cellular only: Connect Wi-Fi to activate DualBoost aggregation.").
- **Preset Benchmark Chips**: Active chips use `primaryContainer` with 1.5dp `primary` outline and bold label; idle chips use `surfaceContainerHighest` with 1dp `outlineVariant` border.
- **Empty State Hero Card**: Replaced bulky placeholder slab with a sleek 24dp rounded `surfaceContainer` hero card featuring a 56dp primary-tinted circular icon container (`Icons.Rounded.CloudDownload`), bold `titleMedium` typography, and centered assistive copy.
- **Pause / Cancel Control**: Outlined button adapting to `MaterialTheme.colorScheme.error`.

### Functional Network Clarity Accents
To ensure physical network transparency across any wallpaper palette:
- **Wi-Fi Stream**: Electric Cyan (`#00E5FF` in Dark Mode, `#00838F` in Light Mode) applied to status dot, icon, and speed progress segment. Displays `Icons.Rounded.WifiOff` when disconnected.
- **Cellular (5G/LTE) Stream**: Neon Violet (`#B388FF` in Dark Mode, `#651FFF` in Light Mode) applied to status dot, icon, and speed progress segment. Displays `Icons.Rounded.SignalCellularOff` when disconnected.

## Typography & Motion
- System Google Sans / Roboto via Material 3 Typography scales.
- Smooth `animateFloatAsState` transitions on progress and speed meters.
- Expressive state changes utilizing `AnimatedContent` cross-fades.
- Haptic tactile feedback (`HapticFeedbackType.LongPress` and `TextHandleMove`) on actions and completion.

## Layout & Components
- **Top Bar**: `CenterAlignedTopAppBar` balanced with a navigation menu icon on the left opening `ModalNavigationDrawer`, the centered brand title (`[⚡] DualBoost`), and exactly 2 high-frequency quick actions (`FolderOpen` for History, `Settings` for Cloud Slicer) on the right, providing clean visual symmetry.
- **Modal Navigation Drawer**: Sleek Material 3 slide-out drawer providing unified access to the Download Library, Speed Benchmark, Cloud Slicer Settings, and About DualBoost info.
- **Hero Network Cards**: Dual `Card` containers with live status indicators, connection state badges, dynamic active/inactive iconography (`Wifi`/`WifiOff`, `SignalCellularAlt`/`SignalCellularOff`), dynamic 4-bar signal level meters with qualitative status (`Excellent • 4/4 bars`, `Good • 3/4 bars`, etc.), real-time generation detection (`5G Cellular` vs `4G LTE Cellular`), and an in-card amber advisory badge when on 4G LTE (`⚠️ 4G Active • Lower boost than 5G`).
- **Download Action Card**: 24dp rounded elevated container with Smart Context Pill Banner (auto-detecting URLs in clipboard), quick clipboard paste, text clear, dynamic quick-test benchmark presets arranged in ascending order (10MB, 25MB, 50MB, 100MB Cloudflare, 150MB, 500MB, 1GB), selected chip visual state with primary border and lightning bolt (`⚡`) range indicators, dynamic server capability captions, and prominent "Boost Download" button.
- **Sequential Stream Mode Toggle**: Sleek interactive surface container row within the New Download card with a play icon, title, dynamic auto-detection caption (`"Auto-detected video • Ready to stream"`, `"Forced on"`, or `"Disabled • High-speed random chunking"`), and a compact Material 3 Switch control for instant 1-tap toggling.
- **Dual-Color Progress Bar & Dynamic Chunk Strip**: Stacked proportional bar visualizing the real-time byte contribution between Wi-Fi and 5G, paired with a Segmented Dynamic Chunk Strip displaying multi-block chunk visualizers where individual chunks light up in Cyan (Wi-Fi) or Violet (5G) with live animated shimmer.
- **File Provider Integration**: Direct "Open File" launcher upon download completion.
- **Download Library (History Bottom Sheet)**: Sleek Material 3 ModalBottomSheet accessible via the top app bar folder icon (`Icons.Rounded.FolderOpen`). Features a 46dp circular hero badge (`FolderOpen`), real-time storage metrics (`X files • Y MB used`), clean empty-state vector art, dynamic file-type icons (APK, Video, Audio, Generic document), dual-boost speed tags, external existence warnings (`Deleted from storage`), 1-tap FileProvider open/share actions, SHA-256 copy chip, and interactive smart deletion confirmation dialogs.

## Responsive Breakpoints
- Standard Phone Portrait (<600dp width): Single-column vertical layout.
- Landscape / Foldable (>600dp width): Dual-pane (Controls on left, active download meters on right).

## Component Patterns

### Buttons
- **Primary Action ("Start Dual Download")**: Filled `Button`, full width or weighted, primary container color.
- **Secondary / Control Action ("Pause", "Resume")**: `OutlinedButton` with 12dp corner radius.

### Forms & Suggestions
- **Smart Context Pill Banner**: Expressive Material 3 pill (`secondaryContainer`) displaying detected clipboard download URLs with 1-tap "Paste" button and dismiss affordance.
- **URL Input Field**: `OutlinedTextField` spanning full width, single line, with explicit placeholder and clear affordance.

### Cards
- **Speed Benchmark Quick Card**: 18dp rounded container (`surfaceContainer`) with primary container icon badge, live subtitle, and quick-action "Test Speed" button launching the benchmark modal.
- **Download Telemetry Card**: High-contrast card with 4dp elevation containing sanitized filename (humanized, free of `%20`/`_20` encoding), real-time ETA countdown (`• 45s left (35.2 MB left)`), clean progress metric (`X of Y`), subtle primary-tinted `⚡ Desktop Mode Active` or `⚡ Cloud Slicer Active` badge, dynamic `⚡ Stream Ready • X MB buffered` (or `⚡ Buffering Stream • X / 24 MB`) badge in Sequential Stream Mode, sleek 12dp dual-color contribution progress bar (Cyan for Wi-Fi, Violet for Mobile Data), real-time "Download Speed" hero display, individual Wi-Fi and Mobile Data streaming meters with live pulse animations, an amber alert banner (`tertiaryContainer`) when non-range servers are detected, a full-width vibrant emerald green hero `[ ▶ Watch Preview (X MB buffered) ]` CTA when playable, and side-by-side [ Pause ] and [ Cancel ] buttons with an accidental-cancellation confirmation dialog.
- **Download Paused Card**: Elevated card (2dp elevation) displaying sanitized filename, frozen progress percentage, progress bar, preserved byte count, primary filled "Resume" button with Play icon, and error-tinted outlined "Discard" button with trash icon and safety confirmation.
- **Download Completed Card**: Container card (`primaryContainer`) displaying celebratory transfer summary (`⚡ 29.61 GB completed in 55m 55s at 9.0 MB/s avg • Aggregated Wi-Fi + 5G Boost ⚡`), formatted elapsed time, sanitized file name, clean folder indicator ("Saved to Downloads folder"), and a dedicated 2-tier action layout (`DownloadCompletedActions`):
  - **Hero Primary Action**: Full-width filled "Open File" button (`height = 48dp`, `RoundedCornerShape(14.dp)`, Play icon, semi-bold typography) optimized for thumb-reachability as the primary post-download intent (`ACTION_VIEW`).
  - **Split Secondary Action Row**: 50/50 balanced row (`height = 44dp`, `Modifier.weight(1f)`, 8dp gap) containing tonal filled "Show in Files" (`ACTION_VIEW_DOWNLOADS` / Google Files integration) and tonal filled "Share" button (`ACTION_SEND`, with icon + text label). Enforces strict single-line text constraints (`maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`, compact 12dp horizontal padding) and merged semantics, completely eliminating awkward multi-line wrapping and height misalignment across any device width.
- **System Tip Banner**: Flat card with `tertiaryContainer` background highlighting required OS settings (Developer Options).


### Navigation
- Clean `CenterAlignedTopAppBar` with menu drawer trigger on the left (`48dp`) and zero right-side clutter, centering the "DualBoost" brand mark with generous breathing room.
- Slide-out `ModalNavigationDrawer` (`ModalDrawerSheet`) for rapid access to all app destinations (Download Library, Speed Benchmark, Cloud Slicer Settings, and About dialog).

### Hero Network Cards
- **Symmetrical Equal-Height Grid**: Side-by-side cards in `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))` ensuring 100% equal height across both cards.
- **Single Icon + Pulsing Status Dot**: Clean visual hierarchy with exactly 1 interface icon in a tinted circle on the left, an animated pulsing connection dot on the top right, and qualitative signal metrics in the subtitle (`Excellent • 4/4 bars`).
- **Full-Width Positive Context Banner**: Context-aware aggregation banner placed directly below the cards in unified brand styling (`primaryContainer` with Speed icon). Dynamically displays `Dual-Channel Active: Aggregating Wi-Fi + 4G LTE` or `Aggregating Wi-Fi + 5G`, maintaining a clean, reassuring, and balanced visual flow.

### Modals & Bottom Sheets
- **Speed Test Bottom Sheet (`ModalBottomSheet`)**: Rounded 28dp surface container modal delivering an interactive 3-phase automated benchmark (Wi-Fi 3s -> 5G 3s -> DualBoost 4s) with real-time animated speedometer digits, dynamic progress indicator, comparative cards, and celebratory Boost Gain summary banner.
- Android system dialogs for runtime permissions (`POST_NOTIFICATIONS`).

### Tables
- Not used. Structured key-value telemetry presented via `SpeedRow` components with horizontal space-between arrangement.

### Loading States
- **Server Probing**: Indeterminate `LinearProgressIndicator` with explanatory text ("Connecting and probing server capabilities...").
- **Segmented Transfer**: Determinate `LinearProgressIndicator` displaying exact percentage ($0.0 \dots 1.0$).

### Empty States
- Centered placeholder text when no active download is running: *"Paste a URL or share a link from Chrome to begin."*

### Error States
- Red container card (`errorContainer`) containing explicit error messages (e.g., DNS failure, timeout, HTTP 404).
- **Actionable Error Recovery (`ADR-032`)**: Elevated failure card includes side-by-side action buttons:
  - **Retry**: High-contrast filled `Button` with error container tint and `Icons.Rounded.Refresh` to restart or re-attempt the transfer using the preserved URL.
  - **Dismiss**: Outlined `Button` with error border and `Icons.Rounded.Close` to cleanly reset the UI state to `Idle`.

### Back Navigation & System Dialogs (`ADR-032`)
- **Smart Back Handling**: System back gestures first dismiss open modals, sheets, and drawers sequentially.
- **Active Download Interception Dialog**: When back is triggered while a download is running, an alert dialog appears with `Icons.Rounded.Download` icon:
  - "Minimize to Background" (`moveTaskToBack(true)`) allowing uninterrupted transfer while freeing the user to use other applications.
  - "Cancel Download" safely discarding the transfer and returning to `Idle`.
  - Outside tap / dismiss retains the user in the active transfer view.

### Animations
- Smooth linear progress transitions.
- Speed gauges dynamically update every 300-500ms without abrupt layout jumps.
- Zero-Flicker State Transitions: `AnimatedContent` uses `contentKey = { it::class }` so animations only fire when transitioning between major lifecycle states (`Idle`, `Probing`, `Downloading`, `Completed`), maintaining 100% solid opacity during active byte-stream updates.


### Badges & Status Indicators
- **Desktop Mode Active**: `primaryContainer` with 8.dp rounded corners, `1.dp` border at `primary.copy(alpha = 0.35f)`, displaying `⚡ Desktop Mode Active`.
- **Cloud Slicer Active**: `tertiaryContainer.copy(alpha = 0.85f)` with 8.dp rounded corners, `1.dp` border at `tertiary.copy(alpha = 0.35f)`, displaying `⚡ Cloud Slicer Active`. Rendered in both Downloading and Paused cards when stage 3 cloud stream slicing is engaged.
- **Battery Optimization Warning Banner (`ADR-033`)**: High-visibility `tertiaryContainer` card with `Icons.Rounded.BatteryAlert` warning users on aggressive OEM devices (Samsung, Xiaomi, Oppo, etc.) when battery optimization is unexempted, providing one-tap "Exempt App" navigation (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and "Dismiss" actions.
- **VPN Active Status Banner (`ADR-033`)**: `secondaryContainer` card with `Icons.Rounded.VpnKey` informing users when an active VPN tunnel is detected and multi-stream acceleration is safely routed through the encrypted tunnel.
- **Data Roaming Guard Banner (`ADR-033`)**: High-contrast `errorContainer` warning banner with `Icons.Rounded.Warning` alerting users when cellular data is paused to prevent unexpected international or partner roaming fees.
- **Cellular Carrier Transparency & Modem Standby Diagnostics (`ADR-033`)**: Hero cellular card dynamically displays carrier name (`5G Cellular (Verizon)`). Subtitle indicates `Roaming (Paused)` or `Modem Standby`. When Wi-Fi is active and cellular drops into standby, the context banner provides clear guidance to toggle "Mobile data always active" in Android Developer Options.

### Settings Modal Sheet (`SettingsBottomSheet`)
- Material 3 `ModalBottomSheet` with `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.
- Header: Centered 58.dp circular hero badge (`CircleShape`) with 14% translucent brand tint, crisp `Icons.Rounded.CloudDone` (when configured) or `Icons.Rounded.CloudQueue` (when unconfigured) (30.dp), and a dedicated status pill directly below the subtitle ("Worker Active & Ready" with pulsing emerald dot vs. "Worker Not Configured" with muted dot). Title "Personal Cloud Slicer" and subtitle.
- Educational callout: `surfaceContainerHigh` rounded card explaining the zero-cost proxy mechanism.
- Inputs: Outlined text fields with custom leading icons (`Language`, `VpnKey`) and clear buttons.
- Action Buttons: "Test Worker" (with progress spinner & status banner) and "Copy Script" (with haptic feedback and toast).
- Expandable setup accordion: Clean numbered steps for 60-second Cloudflare deployment.
- **Antivirus & Threat Guard Card**: Dedicated surface card with green/red status badge, live `Switch` toggle, status indicator (`Active • Blocking malware & fake APKs`), and technical description of 3-stage heuristic protection.

### Security Threat UI Components (`ADR-038`)
- **SecurityThreatDialog**: Dedicated Material 3 `AlertDialog` presenting a prominent circular red shield icon (`errorContainer`), severity classification chip (`CRITICAL RISK • DOUBLE_EXTENSION`), original requested filename vs true deceptive target payload in high-contrast monospace font, explanatory text informing that bytes were purged from storage, a prominent red `[ 🛡️ Safe Discard (Recommended) ]` button, and an explicit `[ ⚠️ Download Anyway (At your own risk) ]` bypass action.
- **Threat Detected Inline Card**: Rendered in `MainActivity` during `DownloadState.ThreatDetected` with `1.5.dp` error border, red shield header, threat description, and side-by-side "Safe Discard" and "Override & Download" buttons.

### Accessibility
- Minimum touch targets of 48x48dp for all buttons.
- Contrast ratio exceeding 4.5:1 for all text against backgrounds.
- Explicit `contentDescription` on non-text visual icons.

### Do Not
- Do NOT use unbranded generic gray progress bars when both networks are active.
- Do NOT bury interface status inside nested menus; Wi-Fi and 5G indicators must remain permanently visible at the top of the main screen.
- Do NOT hide download error messages behind generic toasts; render explicit failure cards with actionable recovery steps.
- Do NOT pair container tokens with accent tints (e.g. `primary` inside `primaryContainer`, or `error` inside `errorContainer`); strictly use designated on-tokens (`onPrimaryContainer`, `onErrorContainer`) to prevent contrast inversions across dynamic color palettes.
- Do NOT use un-branched static hex colors across dark and light themes; always use theme tokens or branch on `isSystemInDarkTheme()`.


