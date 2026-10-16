# Design System

## Design Philosophy
DualBoost Downloader embraces Material 3 Expressive ("Material You") design principles, tailored specifically for Android 17+ on Google Pixel devices and future-proofed for Android 18 and 19. The interface emphasizes functional clarity, physical network transparency, dynamic wallpaper harmonization, and tactile feedback.

## Visual Direction & Brand Identity
- **Pixel Native Material You**: Full dynamic color extraction (`dynamicDarkColorScheme` / `dynamicLightColorScheme`) that adapts in real-time to the user's Pixel wallpaper.
- **Adaptive App Icon ("Dual Converging Boost")**:
  - Background: Deep slate linear gradient with centered cyan ambient glow (`res/drawable/ic_launcher_background.xml`).
  - Foreground: Dual energy streams (Cyan Wi-Fi stream on left, Electric Violet 5G stream on right) converging into a high-speed downward arrow (`res/drawable/ic_launcher_foreground.xml`).
  - Monochrome Layer: Full-fidelity silhouette (`res/drawable/ic_launcher_monochrome.xml`) providing dynamic tinting for Pixel Themed Icons.
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
- **Accented Card Borders**: Subtle 1dp `BorderStroke` (`outlineVariant.copy(alpha = 0.35f)` for base cards, and interface-tinted glows `activeColor.copy(alpha = 0.35f)` for Wi-Fi and 5G) delivering crisp edge definition against deep dark backgrounds.
- **Action Button ("Boost Download")**: Full-width filled `Button` using `MaterialTheme.colorScheme.primary` and `onPrimary`.
- **Aggregation Pill**: Adapts to `MaterialTheme.colorScheme.primaryContainer` with `onPrimaryContainer` and subtle 1dp primary border.
- **Preset Benchmark Chips**: Active chips use `primaryContainer` with 1.5dp `primary` outline and bold label; idle chips use `surfaceContainerHighest` with 1dp `outlineVariant` border.
- **Empty State Hero Card**: Replaced bulky placeholder slab with a sleek 24dp rounded `surfaceContainer` hero card featuring a 56dp primary-tinted circular icon container (`Icons.Rounded.CloudDownload`), bold `titleMedium` typography, and centered assistive copy.
- **Pause / Cancel Control**: Outlined button adapting to `MaterialTheme.colorScheme.error`.

### Functional Network Clarity Accents
To ensure physical network transparency across any wallpaper palette:
- **Wi-Fi Stream**: Electric Cyan (`#00E5FF` in Dark Mode, `#00838F` in Light Mode) applied to status dot, icon, and speed progress segment.
- **Cellular (5G/LTE) Stream**: Neon Violet (`#B388FF` in Dark Mode, `#651FFF` in Light Mode) applied to status dot, icon, and speed progress segment.

## Typography & Motion
- System Google Sans / Roboto via Material 3 Typography scales.
- Smooth `animateFloatAsState` transitions on progress and speed meters.
- Expressive state changes utilizing `AnimatedContent` cross-fades.
- Haptic tactile feedback (`HapticFeedbackType.LongPress` and `TextHandleMove`) on actions and completion.

## Layout & Components
- **Top Bar**: `CenterAlignedTopAppBar` with branded lightning bolt badge, dynamic title, and technical information dialog.
- **Hero Network Cards**: Dual `ElevatedCard` containers with live status indicators, connection state badges, and Developer Options guidance.
- **Download Action Card**: 24dp rounded elevated container with quick clipboard paste, text clear, dynamic quick-test benchmark presets (10MB, 100MB Range, 100MB Cloudflare, 1GB Range), selected chip visual state with primary border and lightning bolt (`⚡`) range indicators, dynamic server capability captions, and prominent "Boost Download" button.
- **Dual-Color Progress Bar**: Stacked proportional bar visualizing the real-time byte contribution between Wi-Fi and 5G.
- **File Provider Integration**: Direct "Open File" launcher upon download completion.

## Responsive Breakpoints
- Standard Phone Portrait (<600dp width): Single-column vertical layout.
- Landscape / Foldable (>600dp width): Dual-pane (Controls on left, active download meters on right).

## Component Patterns

### Buttons
- **Primary Action ("Start Dual Download")**: Filled `Button`, full width or weighted, primary container color.
- **Secondary / Control Action ("Pause", "Resume")**: `OutlinedButton` with 12dp corner radius.

### Forms
- **URL Input Field**: `OutlinedTextField` spanning full width, single line, with explicit placeholder and clear affordance.

### Cards
- **Download Telemetry Card**: High-contrast card with 4dp elevation containing filename, linear progress bar, combined transfer rate, and individual Wi-Fi and 5G meters.
- **Download Paused Card**: Elevated card (2dp elevation) displaying frozen progress percentage, progress bar, preserved byte count, primary filled "Resume" button with Play icon, and error-tinted outlined "Discard" button with trash icon.
- **Download Completed Card**: Container card (`primaryContainer`) displaying successful transfer summary, elapsed time, primary filled "Open File" button (`ACTION_VIEW`), and adjacent tonal "Share" button (`ACTION_SEND`).
- **System Tip Banner**: Flat card with `tertiaryContainer` background highlighting required OS settings (Developer Options).


### Navigation
- Single-screen utility view; no complex bottom bar or navigation drawer required in MVP.

### Modals
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


### Accessibility
- Minimum touch targets of 48x48dp for all buttons.
- Contrast ratio exceeding 4.5:1 for all text against backgrounds.
- Explicit `contentDescription` on non-text visual icons.

### Do Not
- Do NOT use unbranded generic gray progress bars when both networks are active.
- Do NOT bury interface status inside nested menus; Wi-Fi and 5G indicators must remain permanently visible at the top of the main screen.
- Do NOT hide download error messages behind generic toasts; render explicit failure cards with actionable recovery steps.

