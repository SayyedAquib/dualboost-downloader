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
The application uses Compose Material 3 `1.5.0` (`MaterialExpressiveTheme`, `MotionScheme.expressive()`, `DualBoostShapes` backed by `ShapeDefaults`, and `DualBoostTypography` in `Theme.kt`, plus Material Symbols Rounded vector drawables in `DualBoostIcons.kt`) and fully embraces Material You dynamic color extraction (`dynamicColor = true`), harmonizing with the user's Pixel wallpaper and automatically switching between dynamic dark and dynamic light themes based on system Day/Night mode (with complete fallback `surfaceBright`, `surfaceDim`, and `*Fixed` roles):
- **Background & Scaffold**: Adapts directly to `MaterialTheme.colorScheme.background`.
- **Surfaces & Cards Hierarchy**: Material 3 surface elevation hierarchy eliminates flat pitch-black clipping in Dark Mode:
  - Deepest tier: `surfaceContainerLowest` for unselected/dormant cards.
  - Base cards: `surfaceContainer` for primary content containers ("New Download", "Ready to Download", "Downloading", "Paused").
  - Elevated surfaces: `surfaceContainerHigh` for hero speed telemetry and info banners.
  - Tonal controls: `surfaceContainerHighest` for URL textfield input fills and inactive preset benchmark chips.
- **Accented Card Borders**: Subtle 1dp `BorderStroke` (`outlineVariant.copy(alpha = 0.35f)` for base cards, `outlineVariant.copy(alpha = 0.5f)` for inactive cards, and interface-tinted glows `activeColor.copy(alpha = 0.35f)` for active Wi-Fi and 5G) delivering crisp edge definition against deep dark backgrounds.
- **Action Button ("Boost Download")**: Full-width filled `Button` using `MaterialTheme.colorScheme.primary` and `onPrimary`.
- **Context-Aware Aggregation Banner**: Replaces static pills with a 4-state dynamic banner adapting container color and messaging to real-time network states:
  - *Dual Active*: `primaryContainer` with 1dp border ("Dual-Channel Active: Aggregating Wi-Fi + {CellularShort}").
  - *Both Offline*: `errorContainer` alert ("No Network Available: Reconnect Wi-Fi or Cellular to download.").
  - *Wi-Fi Only*: `surfaceContainerHigh` tip ("Using Wi-Fi only: Enable 'Mobile data always active' in Developer Options to aggregate {CellularShort}.").
  - *Cellular Only*: `surfaceContainerHigh` tip ("Using {CellularDetail} only: Connect Wi-Fi to activate DualBoost aggregation.").
- **Preset Benchmark Chips**: Active chips use `primaryContainer` with 1.5dp `primary` outline and bold label; idle chips use `surfaceContainerHighest` with 1dp `outlineVariant` border.
- **Empty State Hero Card**: Replaced bulky placeholder slab with a sleek 24dp rounded `surfaceContainer` hero card featuring a 56dp primary-tinted circular icon container (`Icons.Rounded.CloudDownload`), bold `titleMedium` typography, and centered assistive copy.
- **Pause / Cancel Control**: Outlined button adapting to `MaterialTheme.colorScheme.error`.

### Functional Network Clarity Accents
To ensure physical network transparency across any wallpaper palette:
- **Wi-Fi Stream**: Electric Cyan (`#00E5FF` in Dark Mode, `#00838F` in Light Mode) applied to status dot, icon, and speed progress segment. Displays `Icons.Rounded.WifiOff` when disconnected.
- **Cellular (5G/LTE) Stream**: Neon Violet (`#B388FF` in Dark Mode, `#651FFF` in Light Mode) applied to status dot, icon, and speed progress segment. Displays `Icons.Rounded.SignalCellularOff` when disconnected.

## Typography & Motion
- **Material 3 `1.5.0` `Typography` & Emphasized Scale**: Configured via `DualBoostTypography` (`FontFamily.SansSerif`) and `*Emphasized` styles (`headlineSmallEmphasized`, `titleLargeEmphasized`, `titleMediumEmphasized`, `titleSmallEmphasized`, `labelLargeEmphasized`, `labelMediumEmphasized`, `labelSmallEmphasized`) for headers, active speeds, status chips, and actions.
- **`DualBoostShapes` & Organic Geometry**: `Shapes` backed by Material 3 `1.5.0` `ShapeDefaults` (`ExtraSmall` through `ExtraExtraLarge`) in `Theme.kt`, paired with `MaterialShapes.Cookie9Sided.toShape()` for the TopBar emblem, network card icon badges, sequential toggle badge, paused badge, completion badge, alert dialogs, and social/security hero emblems.
- **Material Symbols Rounded Icons**: 33 bundled Android Vector Drawables (`res/drawable/ms_*.xml` mapped in `DualBoostIcons.kt`) rendered via `painterResource(...)`, replacing legacy `Icons.Rounded.*` and removing `material-icons-extended`.
- **`MotionScheme.expressive()`**: Configured via `MaterialExpressiveTheme` in `Theme.kt` for spring-physics-based transitions across sheets, buttons, and controls (ADR-058 / ADR-060).
- **Shape-Morphing Buttons & Icon Buttons**: `ButtonDefaults.shapes()` and `ButtonDefaults.shapesFor(...)` (`ExtraSmallContainerHeight` / `MediumContainerHeight`) morph button corner radii smoothly on press; `ExpressiveTooltipIconButton` pairs `IconButtonDefaults.shapes()` with `TooltipBox` + `PlainTooltip` (`TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above)`).
- **Top Bar & Scroll/Toast Hygiene**: `CenterAlignedTopAppBar` uses `TopAppBarDefaults.pinnedScrollBehavior()` (`scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer`), and in-app errors/notices route through Material 3 `SnackbarHost` instead of system `Toast`s.
- Smooth `animateFloatAsState` transitions on progress and speed meters.
- Expressive state changes utilizing `AnimatedContent` cross-fades.
- Haptic tactile feedback (`HapticFeedbackType.LongPress` and `TextHandleMove`) on actions and completion.

## Layout & Components
- **Top Bar**: Pure Minimalist `CenterAlignedTopAppBar` displaying only the centered brand title (`[⚡] DualBoost`) with zero navigation icons and zero action buttons, delivering a distraction-free, balanced header (ADR-051).
- **Zero-Drawer Navigation**: Entire navigation drawer and hamburger menu button removed in favor of single-screen direct interaction (ADR-051).
- **Hero Network Cards**: Dual `Card` containers with live status indicators, connection state badges, dynamic active/inactive iconography (`Wifi`/`WifiOff`, `SignalCellularAlt`/`SignalCellularOff`), dynamic 4-bar signal level meters with qualitative status (`Excellent • 4/4 bars`, `Good • 3/4 bars`, etc.), real-time Wi-Fi telemetry (`wifiDetails.richHeroLabel`: e.g. `Wi-Fi 7 (6 GHz • 2400 Mbps)`, `Wi-Fi 6 (5 GHz • 1200 Mbps)`, `Wi-Fi`), real-time cellular generation detection (`5G Cellular`, `4G LTE Cellular`, `3G Cellular`, `Cellular`), and an in-card amber advisory badge when on 4G LTE (`⚠️ 4G Active • Lower boost than 5G`).
- **Download Action Card**: 24dp rounded elevated container with Smart Context Pill Banner (auto-detecting URLs in clipboard), quick clipboard paste, text clear, dynamic quick-test benchmark presets arranged in ascending order (10MB, 25MB, 50MB, 100MB Cloudflare, 150MB, 500MB, 1GB), selected chip visual state with primary border and lightning bolt (`⚡`) range indicators, dynamic server capability captions ("Aggregates {WifiShort} + {CellularShort} concurrently"), and prominent "Boost Download" button.
- **Watch While Downloading Toggle (Sequential Streaming)**: Sleek interactive surface container card (`RoundedCornerShape(16.dp)`) within the New Download card featuring a 34dp circular icon badge (`Icons.Rounded.PlayCircle`), humanized title (`"Watch While Downloading"`), dynamic plain-English status caption (`"Video detected • Ready to watch while downloading"`, `"On • Downloads in order to preview early"`, or `"Off • Maximum speed for files & games"`), and a compact Material 3 Switch control for instant 1-tap toggling.
- **Dual-Color Progress Bar & Dynamic Chunk Strip**: Stacked proportional bar visualizing the real-time byte contribution between Wi-Fi and Cellular, paired with a Segmented Dynamic Chunk Strip displaying multi-block chunk visualizers where individual chunks light up in Cyan (Wi-Fi) or Violet (Cellular) with live animated shimmer and dual-dynamic legend ("${wifiDetails.shortLabel} Chunk" / "${cellularGeneration.shortLabel} Chunk").
- **File Provider Integration**: Direct "Open File" launcher upon download completion.
- **Download Library (History Bottom Sheet)**: Sleek Material 3 ModalBottomSheet accessible via the top app bar folder icon (`Icons.Rounded.FolderOpen`). Features a 46dp circular hero badge (`FolderOpen`), real-time storage metrics (`X files • Y MB used`), clean empty-state vector art, dynamic file-type icons (APK, Video, Audio, Generic document), dual-boost speed tags, external existence warnings (`Deleted from storage`), 1-tap FileProvider open/share actions, SHA-256 copy chip, and interactive smart deletion confirmation dialogs.

## Responsive Breakpoints
- Standard Phone Portrait (<600dp width): Single-column vertical layout.
- Landscape / Foldable (>600dp width): Dual-pane (Controls on left, active download meters on right).

## Component Patterns

### Buttons
- **Interactive `ButtonGroup` Rows**: Horizontal control rows (`Battery Warning`, `Preview / Pause / Cancel`, `Safe Discard / Override`, `Retry / Dismiss`, `Resume / Discard`, `Show in Files / Share`, and `SocialMediaModalSheet` actions) use Material 3 `1.5.0` `ButtonGroup` (`ButtonGroupDefaults.OverflowIndicator` with `leadingIcon`s in overflow `DropdownMenuItem`s, `customItem`, and `Modifier.animateWidth(...)`) paired with `ButtonDefaults.shapes()`.
- **Primary Action ("Boost Download")**: Full-width filled `Button` sized with `ButtonDefaults.MediumContainerHeight` / `ButtonDefaults.shapesFor(ButtonDefaults.MediumContainerHeight)`, primary container color.
- **Secondary / Control Action ("Pause", "Resume", Clipboard "Paste")**: `OutlinedButton` with `ButtonDefaults.shapes()` and compact actions sized with `ButtonDefaults.ExtraSmallContainerHeight` / `ButtonDefaults.shapesFor(...)`.

### Forms & Suggestions
- **Smart Context Pill Banner**: Expressive Material 3 `OutlinedCard` (`secondaryContainer`) displaying detected clipboard download URLs with an extra-small "Paste" `Button` and tooltip dismiss icon button.
- **URL Input Field**: Full-width `OutlinedTextField` using `OutlinedTextFieldDefaults.tonalColors()` and `OutlinedTextFieldDefaults.roundedShape`, single line, with explicit placeholder and `ExpressiveTooltipIconButton` paste/clear trailing controls.
- **Sequential Stream Toggle**: Checkable Material 3 `ListItem` with a `MaterialShapes.Cookie9Sided` icon badge, unscaled native `Switch` using `thumbContent` (`DualBoostIcons.Check`), and an `AssistChip` for "Reset to Auto".

### Cards
- **Download Input Card**: 24dp rounded container (`surfaceContainer`) containing the URL input field, optional privacy-conscious clipboard suggestion pill, Sequential Video Stream Mode toggle, and the primary "Boost Download" button. Developer preset benchmark buttons have been pruned for a clean consumer-first layout (ADR-050).
- **Download Telemetry Card**: High-contrast card with 4dp elevation containing sanitized filename (humanized, free of `%20`/`_20` encoding), real-time ETA countdown (`• 45s left (35.2 MB left)`), clean progress metric (`X of Y`), functional status badges (`⚡ Desktop Mode Active`, `⚡ Cloud Slicer Active`, or dynamic `⚡ Stream Ready • X MB buffered` / `⚡ Buffering Stream • X / 24 MB` styled in cohesive primary cyan), sleek 12dp dual-color contribution progress bar (Cyan for Wi-Fi, Violet for Mobile Data), real-time "Download Speed" hero display, individual Wi-Fi and Mobile Data telemetry tags in a single horizontal row, compact inline chip for single-stream mode, and an integrated single-row action layout with a solid primary Pixel cyan `[ ▶ Preview ]` CTA (`height = 48dp`, `RoundedCornerShape(14.dp)`, `primary` fill `#64D2FF` with `onPrimary` deep navy text `#003549`, `weight = 1.3f`) paired side-by-side with `[ || Pause ]` and `[ ✕ Cancel ]` in a unified horizontal control bar (ADR-053 & ADR-054). Internal developer debug telemetry badges (`4 MB Chunks`, `Balanced Timeout`, `Auto 5G`, `Custom UA`) have been removed for an uncluttered visual hierarchy (ADR-050).
- **Download Paused Card**: Elevated card (2dp elevation) displaying sanitized filename, frozen progress percentage, progress bar, preserved byte count, primary filled "Resume" button with Play icon, and error-tinted outlined "Discard" button with trash icon and safety confirmation.
- **Download Completed Card**: Container card (`primaryContainer`) displaying celebratory transfer summary (`⚡ 29.61 GB completed in 55m 55s at 9.0 MB/s avg • Aggregated Wi-Fi + 5G Boost ⚡`), formatted elapsed time, sanitized file name, clean folder indicator ("Saved to Downloads folder"), and a dedicated 2-tier action layout (`DownloadCompletedActions`):
  - **Hero Primary Action**: Full-width filled "Open File" button (`height = 48dp`, `RoundedCornerShape(14.dp)`, Play icon, semi-bold typography) optimized for thumb-reachability as the primary post-download intent (`ACTION_VIEW`).
  - **Split Secondary Action Row**: 50/50 balanced row (`height = 44dp`, `Modifier.weight(1f)`, 8dp gap) containing tonal filled "Show in Files" (`ACTION_VIEW_DOWNLOADS` / Google Files integration) and tonal filled "Share" button (`ACTION_SEND`, with icon + text label). Enforces strict single-line text constraints (`maxLines = 1`, `softWrap = false`, `overflow = TextOverflow.Ellipsis`, compact 12dp horizontal padding) and merged semantics, completely eliminating awkward multi-line wrapping and height misalignment across any device width.
- **System Tip Banner**: Flat card with `tertiaryContainer` background highlighting required OS settings (Developer Options).


### Navigation
- Pure Minimalist `CenterAlignedTopAppBar` featuring exclusively the centered brand mark (`[⚡] DualBoost`) with zero navigation drawer triggers and zero top-right button clutter, providing pristine symmetry and maximum focus on the download experience (ADR-051).
- Zero-Drawer Architecture: The legacy `ModalNavigationDrawer` has been eliminated. The app operates as an ultra-fast, single-surface utility where completed downloads are managed directly on the finished card via "Open File" and "Share" (ADR-051).

### Hero Network Cards
- **Symmetrical Equal-Height Grid**: Side-by-side cards in `Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min))` ensuring 100% equal height across both cards.
- **Single Icon + Pulsing Status Dot**: Clean visual hierarchy with exactly 1 interface icon in a tinted circle on the left, an animated pulsing connection dot on the top right, and qualitative signal metrics in the subtitle (`Excellent • 4/4 bars`). Redundant stacked text banner below the cards removed to let the glowing cards communicate status directly (ADR-050).

### Modals & Bottom Sheets
- **Speed Test Bottom Sheet (`ModalBottomSheet`)**: Rounded 28dp surface container modal delivering an interactive 3-phase automated benchmark (Wi-Fi 3s -> 5G 3s -> DualBoost 4s) with real-time animated speedometer digits, dynamic progress indicator, comparative cards, and celebratory Boost Gain summary banner.
- Android system dialogs for runtime permissions (`POST_NOTIFICATIONS`).

### Tables
- Not used. Structured key-value telemetry presented via `SpeedRow` components with horizontal space-between arrangement.

### Loading States
- **Server Probing & Auto-Recovery**: Expressive Material 3 `1.5.0` `LoadingIndicator` + `LinearWavyProgressIndicator` with explanatory text ("Connecting and probing server capabilities..." / recovery attempt details).
- **Social Media Stream Extraction**: Indeterminate `CircularWavyProgressIndicator` inside `SocialMediaModalSheet` using deterministic `rememberBottomSheetState`.
- **Segmented Transfer / Paused**: Dual-color proportional transfer bar during active downloads (with `Badge` percentage indicator) and flattened determinate `LinearWavyProgressIndicator(progress = ..., amplitude = { 0f })` when paused ($0.0 \dots 1.0$).

### Empty States
- **Minimalist Idle Screen**: When no download is running, the app maintains an uncluttered, distraction-free layout showing only the hero network cards and the "New Download" card (the redundant "Ready to Download" empty placeholder card is removed per ADR-050).

### Error States
- Red container card (`errorContainer`) containing explicit error messages (e.g., DNS failure, timeout, HTTP 404).
- **Actionable Error Recovery (`ADR-032`)**: Elevated failure card includes side-by-side `ButtonGroup` actions:
  - **Retry**: High-contrast filled `Button` with `ButtonDefaults.shapes()`, error container tint, and `DualBoostIcons.Refresh` to restart or re-attempt the transfer using the preserved URL.
  - **Dismiss**: Outlined `Button` with `ButtonDefaults.shapes()`, error border, and `DualBoostIcons.Close` to cleanly reset the UI state to `Idle`.

### Back Navigation & System Dialogs (`ADR-032`)
- **Smart Back Handling**: System back gestures first dismiss open modals, sheets, and drawers sequentially.
- **Active Download Interception Dialog**: When back is triggered while a download is running, an alert dialog appears with `DualBoostIcons.Download` icon:
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
- **Battery Optimization Warning Banner (`ADR-033`)**: High-visibility `tertiaryContainer` card with `DualBoostIcons.BatteryAlert` warning users on aggressive OEM devices (Samsung, Xiaomi, Oppo, etc.) when battery optimization is unexempted, providing one-tap "Exempt App" navigation (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) and "Dismiss" actions.
- **VPN Active Status Banner (`ADR-033`)**: `secondaryContainer` card with `DualBoostIcons.VpnKey` informing users when an active VPN tunnel is detected and multi-stream acceleration is safely routed through the encrypted tunnel.
- **Data Roaming Guard Banner (`ADR-033`)**: High-contrast `errorContainer` warning banner with `DualBoostIcons.Warning` alerting users when cellular data is paused to prevent unexpected international or partner roaming fees.
- **Cellular Carrier Transparency & Modem Standby Diagnostics (`ADR-033`)**: Hero cellular card dynamically displays carrier name (`5G Cellular (Verizon)`). Subtitle indicates `Roaming (Paused)` or `Modem Standby`. When Wi-Fi is active and cellular drops into standby, the context banner provides clear guidance to toggle "Mobile data always active" in Android Developer Options.

### Top App Bar & Navigation
- Material 3 `CenterAlignedTopAppBar` with dynamic surface tint.
- Navigation icon: `Icons.Rounded.Menu` (hamburger ☰) on the top left opening the left navigation drawer.
- Action icons: None on top bar (clean header maintaining visual focus).
- Left Navigation Drawer: Displays app branding, Android 15 SDK 35 badge, "Settings" (`Icons.Rounded.Settings`), and "Speed Test Benchmark" items.

### Settings Modal Sheet (`SettingsBottomSheet` - ADR-049 Option 2)
- Material 3 `ModalBottomSheet` with `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`.
- **Header**: Centered 54dp circular hero badge (`CircleShape`) with 12% translucent brand tint, crisp `Icons.Rounded.VerifiedUser` icon (28dp), Title "Protection & Settings", and subtitle "All optimizations & security shields are active on auto-pilot".
- **Zero-Touch Architecture**: Removed all manual toggles and input fields. Converted from a configuration sheet into a reassuring, live **Protection & Automation Dashboard**.
- **Card 1: Antivirus & Threat Guard**: Displays green shield icon, `ACTIVE` status badge, "Always On • Blocking disguised malware & fake APKs", and technical description of 3-stage heuristic protection.
- **Card 2: Browser Identity & Adaptive Escalation**: Displays language icon, `AUTO` status badge, "Smart Auto • Phone with Desktop Chrome Failover", and explanation of automated Stage 2 Windows 11 Chrome spoofing whenever servers restrict mobile links.
- **Card 3: Automated Zero-Touch Engines**: Clean summary rows for Multi-Thread Concurrency (Auto), Chunk Slicing (Auto 1 MB–64 MB), Cellular Detection (Auto 5G/LTE), Network Timeout (Balanced), Cloud Slicer (Pre-configured), and Storage (Public Downloads).
- **Action Button**: Full-width "Done" button (`Icons.Rounded.Check`) dismissing modal.

### Security Threat UI Components (`ADR-038`, `ADR-060`)
- **SecurityThreatDialog**: Dedicated Material 3 `AlertDialog` (`MaterialTheme.shapes.largeIncreased`) presenting a prominent `MaterialShapes.Cookie9Sided` red shield badge (`errorContainer`), severity classification chip (`CRITICAL RISK • DOUBLE_EXTENSION`, `MaterialTheme.shapes.small`), original requested filename vs. actual disguised target payload in high-contrast monospace typography (`labelMediumEmphasized`), explanatory copy, and `ButtonDefaults.shapes()` actions (`[ 🛡️ Safe Discard (Recommended) ]` and `[ ⚠️ Download Anyway (At your own risk) ]`).
- **Threat Detected Inline Card**: Rendered in `MainActivity` during `DownloadState.ThreatDetected` with `1.5.dp` error border, red shield header, threat description, and `ButtonGroup` actions ("Safe Discard" and "Override & Download").

### Social Media Intake & Quality Modal (`ADR-039`, `ADR-058`, `ADR-060`)
- **SocialMediaModalSheet**: Material 3 `1.5.0` `ModalBottomSheet` (`BottomSheetDefaults.ExpandedShape`) using deterministic `rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))`, triggered on social link paste or Share Sheet intake:
  - **Platform Hero Badge**: 56dp `MaterialShapes.Cookie9Sided.toShape()` container with theme-adapted platform tint (`SocialPlatform.resolveBrandColorHex(isDark)`) and Material Symbols Rounded platform icon.
  - **URL Monospace Preview**: Compact `surfaceContainerHighest` pill with `DualBoostIcons.Link` icon and single-line URL truncation.
  - **Segmented Quality List**: 1-tap `SegmentedListItem` rows (`ListItemDefaults.segmentedShapes`, `ListItemDefaults.segmentedColors`) for `MAX (4K / 1080p)`, `HD 720p`, and `Audio Only (MP3)`.
  - **`ButtonGroup` Action Bar**: Animated side-by-side `ButtonGroup` actions (`Add to Queue`, `Boost Download`, `Dismiss`, `Retry` with `ButtonDefaults.shapes()`, `labelLargeEmphasized`, and `leadingIcon` overflow items).
  - **Resolving & Error Overlays**: Centered 56dp `CircularWavyProgressIndicator` during stream extraction, and dedicated error state with dismiss/retry recovery.

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


