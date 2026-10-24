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
- **Download Telemetry Card**: High-contrast card with 4dp elevation containing filename, real-time ETA countdown (`• 45s left (35.2 MB left)`), subtle primary-tinted `⚡ Desktop Mode Active` badge, dual-color progress bar, `SegmentedChunkStrip` multi-block chunk aggregation grid, real-time speed in `MB/s`, individual Wi-Fi and 5G streaming meters with live pulse animations, and an amber alert banner (`tertiaryContainer`) when non-range servers are detected.
- **Download Paused Card**: Elevated card (2dp elevation) displaying frozen progress percentage, progress bar, preserved byte count, primary filled "Resume" button with Play icon, and error-tinted outlined "Discard" button with trash icon.
- **Download Completed Card**: Container card (`primaryContainer`) displaying celebratory transfer summary (`⚡ 150 MB downloaded in 6s at 25.0 MB/s avg • Aggregated Wi-Fi + 5G Boost ⚡`), elapsed time, primary filled "Open File" button (`ACTION_VIEW`), adjacent tonal "Share" button (`ACTION_SEND`), and interactive 1-click "Copy SHA-256" cryptographic verification chip.
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
- Red container card (`#FFEBEE`) containing explicit error messages (e.g., DNS failure, timeout, HTTP 404).

### Animations
- Smooth linear progress transitions.
- Speed gauges dynamically update every 300-500ms without abrupt layout jumps.
- Zero-Flicker State Transitions: `AnimatedContent` uses `contentKey = { it::class }` so animations only fire when transitioning between major lifecycle states (`Idle`, `Probing`, `Downloading`, `Completed`), maintaining 100% solid opacity during active byte-stream updates.


### Badges & Status Indicators
- **Desktop Mode Active**: `primaryContainer` with 8.dp rounded corners, `1.dp` border at `primary.copy(alpha = 0.35f)`, displaying `⚡ Desktop Mode Active`.
- **Cloud Slicer Active**: `tertiaryContainer.copy(alpha = 0.85f)` with 8.dp rounded corners, `1.dp` border at `tertiary.copy(alpha = 0.35f)`, displaying `⚡ Cloud Slicer Active`. Rendered in both Downloading and Paused cards when stage 3 cloud stream slicing is engaged.

### Settings Modal Sheet (`SettingsBottomSheet`)
- Material 3 `ModalBottomSheet` with `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.
- Header: Centered 58.dp circular hero badge (`CircleShape`) with 14% translucent brand tint, crisp `Icons.Rounded.CloudDone` (when configured) or `Icons.Rounded.CloudQueue` (when unconfigured) (30.dp), and a dedicated status pill directly below the subtitle ("Worker Active & Ready" with pulsing emerald dot vs. "Worker Not Configured" with muted dot). Title "Personal Cloud Slicer" and subtitle.
- Educational callout: `surfaceContainerHigh` rounded card explaining the zero-cost proxy mechanism.
- Inputs: Outlined text fields with custom leading icons (`Language`, `VpnKey`) and clear buttons.
- Action Buttons: "Test Worker" (with progress spinner & status banner) and "Copy Script" (with haptic feedback and toast).
- Expandable setup accordion: Clean numbered steps for 60-second Cloudflare deployment.

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


