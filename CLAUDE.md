# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Coding Guidelines

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### Think Before Coding

- State assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### Simplicity First

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

### Surgical Changes

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

### Goal-Driven Execution

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

## Build / Test / Run

```bash
# Quick compile check (Kotlin only, no dex/package — fastest for verifying code compiles)
.\gradlew.bat :app:compileAppDebugKotlin

# Assemble all variants
./gradlew assembleAppRelease

# Assemble without R8 (for crash debugging — no minification/shrinking)
./gradlew assembleAppNoR8

# Debug build
./gradlew assembleAppDebug

# Run unit tests (JVM, local)
./gradlew test

# Run a single test class
./gradlew test --tests "io.legado.app.model.cache.CacheDownloadQueueTest"

# Run connected Android tests
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

The project uses JDK 21 for development and CI (set in `build.gradle.kts` via `jvmToolchain`).

Gradle properties: 8 GB heap, configuration cache disabled (`gradle.properties:31`), non-transitive R classes, precise resource shrinking enabled.

## Architecture

This is a Material Design 3 **local-reading fork** of [Legado](https://github.com/gedoor/legado)
(based on [HapeLee/legado-with-MD3](https://github.com/HapeLee/legado-with-MD3)). It keeps only
offline reading of local TXT/EPUB/MOBI/PDF files, reading settings, replace rules, TXT chapter
rules, bookmarks, reading history and WebDAV backup; online features (book sources, RSS, AI,
dictionary, read-aloud, WebService, Cronet, Firebase, WebView, Rhino JS) have been removed and must
not be reintroduced.
`app/src/main/java/io/legado/app/` uses **Clean Architecture** with three layers:

| Layer | Package | Role |
|---|---|---|
| Data | `data/` | Room DB (`AppDatabase`, version 85, ~22 DAOs, ~25 entities), repository implementations |
| Domain | `domain/` | Gateway interfaces, use cases (14), domain models — no framework dependencies |
| UI | `ui/` | Jetpack Compose screens, Navigation 3 routes, ViewModels |

Additional top-level packages:
- **`help/`** — Infrastructure "glue": book content processing, backup/WebDAV, config
- **`model/`** — Runtime state coordinators (not entities): `ReadBook`, `BookCover`, etc.
- **`service/`** — Android foreground/background services (download, maintenance)
- **`lib/`** — Third-party library wrappers (MOBI parser, WebDAV client, legacy View theme system)
- **`base/`** — Abstract Activity/Fragment/ViewModel base classes
- **`utils/`** — Extension functions and utility classes (~70 files)

Modules: `:app`, `:modules:book` (epub/TXT parsing, namespace `me.ag2s`), `:baselineprofile`.
The online `:modules:rhino` (JS engine) and `modules/web` (Vue frontend) have been removed; do not
reintroduce them.

## Dependency Injection (Koin)

Two modules loaded in `App.onCreate()`:

```kotlin
startKoin {
    modules(appDatabaseModule, appModule)
}
```

- **`di/appDatabaseModule.kt`** — Singleton `AppDatabase` + factory bindings for all 22 DAOs
- **`di/appModule.kt`** — Singletons (repositories, use cases, gateways, Coil `ImageLoader`), `viewModelOf` / `viewModel { }` for all ViewModels, some parameterized definitions

Gateways are bound to their repository implementations explicitly (e.g., `single<LocalBookGateway> { LocalBookRepository(get()) }`), not through `singleOf`.

## Navigation

Uses **Jetpack Navigation 3** (`androidx.navigation3`) with type-safe `@Serializable` sealed interfaces for route keys:

```kotlin
@Serializable
private sealed interface MainRoute : NavKey
@Serializable
private data object MainRouteHome : MainRoute
@Serializable
private data class MainRouteCache(val groupId: Long) : MainRoute
```

`MainActivity` holds a single `NavDisplay` with `entryProvider { ... }` defining all composable entries. `Launcher0` through `LauncherW` extend `MainActivity` to provide multiple launcher icon alias entries. Separate activities handle the reader (`ReadBookActivity` — still View-based), book info, replace rules, file manager, etc. Source-management, QR-scan and other online entry points are **targets for removal in P4/P5**.

## Theme System

A multi-engine theming system in `ui/theme/`:

1. **Material 3 Expressive** (default): Uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`
2. **Miuix** (alternative): Uses `top.yukonga.miuix.kmp` theming engine

14 theme modes (`AppThemeMode` enum) — Dynamic (Monet), 12 named presets, Custom (MaterialKolor seed-color generation), Transparent. `CustomColorScheme` wraps `com.materialkolor` with configurable `PaletteStyle` (TonalSpot, Neutral, Vibrant, Expressive, Rainbow, etc.) and `ColorSpec` (2021 vs 2025).

Legacy View-based theme still exists in `lib/theme/` (used by non-migrated screens like `ReadBookActivity`).

## Hybrid Compose + View

The app is mid-migration from Views to Compose. View-based screens (reader, book info) coexist with Compose screens (main tabs, settings, bookshelf). Online screens (search, RSS, source management, discovery, read-aloud, manga, audio) have been removed. XML layouts, `viewBinding`, and traditional Activities are still heavily used. The `viewBinding` build feature is enabled but Compose screens are the target.

## Jetpack Compose Requirements (new screens MUST follow)

All **new** UI screens must be implemented in Jetpack Compose following the patterns below. Do **not
** create new View-based Activities/Fragments/XML layouts. Existing View-based screens can remain
until migrated.

### MVI/UDF Architecture

Every Compose screen follows a strict **Model-View-Intent** pattern with three artifacts defined in
a `*Contract.kt` file:

```
ui/{feature}/
├── XxxContract.kt      // UiState, Intent, Effect (and optionally Sheet/Dialog)
├── XxxViewModel.kt     // ViewModel
├── XxxScreen.kt        // Screen composable
└── XxxRouteScreen.kt   // (optional) outer wrapper for activity results / lifecycle
```

**Contract definitions:**

```kotlin
// @Stable data class — all screen state in one place
@Stable
data class XxxUiState(
    val loading: Boolean = false,
    val items: ImmutableList<ItemUi> = persistentListOf(),
    val activeSheet: XxxSheet? = null,
    val activeDialog: XxxDialog? = null,
)

// sealed interface — every user action is an Intent
sealed interface XxxIntent {
    data class LoadData(val id: Long) : XxxIntent
    data object Refresh : XxxIntent
}

// sealed interface — one-shot side effects (navigation, toast, etc.)
sealed interface XxxEffect {
    data class ShowToast(val message: String) : XxxEffect
    data class NavigateTo(val route: MainRoute) : XxxEffect
}

// (optional) sealed interface for multi-sheet/dialog scenarios
sealed interface XxxSheet { data object Filter : XxxSheet }
sealed interface XxxDialog { data class Confirm(val msg: String) : XxxDialog }
```

**Naming rules:**

- State: `{Feature}UiState` — `@Stable data class`
- Intent: `{Feature}Intent` — `sealed interface` with `data class` / `data object` members
- Effect: `{Feature}Effect` — `sealed interface`
- Sheet/Dialog: `{Feature}Sheet`, `{Feature}Dialog` — `sealed interfaces` stored in UiState

### ViewModel

```kotlin
class XxxViewModel(/* injected dependencies */) : ViewModel() {

    private val _uiState = MutableStateFlow(XxxUiState())
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<XxxEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    fun onIntent(intent: XxxIntent) {
        when (intent) {
            is XxxIntent.LoadData -> loadData(intent.id)
            is XxxIntent.Refresh -> refresh()
        }
    }

    private fun loadData(id: Long) {
        // Use viewModelScope, update _uiState via update { it.copy(...) }
    }
}
```

Key rules:

- Extend `ViewModel()` directly (not `BaseViewModel`).
- `_uiState` is `MutableStateFlow`, exposed as `StateFlow` via `.asStateFlow()`.
- `_effects` is `MutableSharedFlow(extraBufferCapacity = 16)`, exposed via `.asSharedFlow()`.
- Emit effects via `_effects.tryEmit(...)`.
- Single `onIntent()` entry point, dispatched via `when`.

### Screen Composable

```kotlin
// Stateless screen — ViewModel wired in entry provider or RouteScreen
@Composable
fun XxxScreen(
    state: XxxUiState,
    onIntent: (XxxIntent) -> Unit,
    effects: Flow<XxxEffect>,                   // one-shot effects from ViewModel
    onBack: () -> Unit,
    onNavigateToYyy: (YyyRoute) -> Unit,
) {
    // Collect effects
    LaunchedEffect(Unit) {
        effects.collectLatest { effect ->
            when (effect) {
                is XxxEffect.ShowToast -> { /* ... */ }
                is XxxEffect.NavigateTo -> onNavigateToYyy(effect.route)
            }
        }
    }

    AppScaffold(
        topBar = {
            GlassMediumFlexibleTopAppBar(
                title = { Text("Title") },
                scrollBehavior = GlassTopAppBarDefaults.defaultScrollBehavior(),
                navigationButton = { TopBarNavigationButton(onBack) },
            )
        },
    ) { contentPadding ->
        // UI content, no business logic here
    }
}
```

Key rules:

- Screen is **stateless** — receives `state`, `onIntent`, `effects`, never accesses ViewModel
  directly.
- Effects collected in `LaunchedEffect(Unit) { ... }` using `collectLatest`.
- Alternatively, effects can be collected in the outer `RouteScreen` or entry provider if the screen
  doesn't need them directly.
- Use project custom widgets: `AppScaffold`, `AppText`, `AppIcon`, `AppIcons`, `AppAlertDialog`,
  `AppModalBottomSheet`, `NormalCard`, `GlassMediumFlexibleTopAppBar`, `TopBarNavigationButton`,
  `TopBarActionButton`, etc.
- No business logic, no direct DB/network calls in composables.

Two input patterns are acceptable:

- **Stateless (preferred for new screens):** `state: XxxUiState` + `onIntent: (XxxIntent) -> Unit` —
  ViewModel wired in entry provider or RouteScreen.
- **ViewModel as default param:** `viewModel: XxxViewModel = koinViewModel()` — simpler for
  standalone screens.

### Stability

- All `UiState` and UI item data classes **must** be annotated with `@Stable`.
- Use `ImmutableList` (from `kotlinx.collections.immutable`) for list properties in state classes,
  not `List` or `MutableList`.
- Prefer `persistentListOf()` / `toImmutableList()` for default values.

### Navigation

Uses **Navigation 3** (`androidx.navigation3`). Routes are `@Serializable` sealed interfaces:

```kotlin
// In MainNavKey.kt
@Serializable
data class MainRouteXxx(val id: Long) : MainRoute
```

Entry registered in `MainNavGraph.kt`:

```kotlin
entry<MainRouteXxx> { route ->
    val viewModel = koinViewModel<XxxViewModel>()
    XxxScreen(
        state = viewModel.uiState.collectAsStateWithLifecycle().value,
        onIntent = viewModel::onIntent,
        onBack = { onNavigateBack() },
        onNavigateToYyy = { onNavigateToRoute(it) },
    )
}
```

Key rules:

- Screens **never** reference the navigator directly — receive `onBack`, `onNavigateToXxx` lambdas.
- Navigation is callback-based, wired by the entry provider.
- New routes added to the `MainRoute` sealed interface in `MainNavKey.kt`.

### Koin DI

- Register ViewModels in `di/appModule.kt` with `viewModelOf(::XxxViewModel)`.
- Inject in Compose via `koinViewModel()` (default param or explicit in entry provider).
- For keyed ViewModels (e.g. per-book): `koinViewModel<XxxViewModel>(key = route.bookUrl)`.
- Repositories/gateways/use cases registered as `singleOf(::...)`.

### Activity Base Class

New standalone Compose activities extend `BaseComposeActivity`:

```kotlin
class XxxActivity : BaseComposeActivity() {
    @Composable
    override fun Content() {
        // Screen content — AppTheme is already applied by the base class
    }
}
```

### RouteScreen Wrapper

For screens needing activity result handling, lifecycle observation, or permission requests, use a
two-layer pattern:

- Outer `XxxRouteScreen`: handles `ActivityResultLauncher`, lifecycle callbacks, file pickers,
  permission requests. Wires ViewModel.
- Inner `XxxScreen`: pure UI, stateless with `state` + `onIntent`.

### Material 3 vs Miuix

The project supports two Compose theme engines. If a screen needs engine-specific UI, branch on:

```kotlin
if (ThemeResolver.isMiuixEngine(LegadoTheme.composeEngine)) {
    // Miuix implementation
} else {
    // Material 3 implementation
}
```

For detailed Compose review conventions and migration patterns, see
`.agents/skills/legado-compose-review/`.

## Important Constraints

- Code namespace is `io.legado.app`; Android `applicationId` is `io.github.dasoops.reader` (they are
  intentionally different — the fork installs alongside the original app without replacing it)
- App crypto uses JCA (`javax.crypto`/`java.security`) under `help/crypto/`. Hutool 5.8.22 remains
  on the classpath only for lenient base64 decoding in `help/crypto/CryptoUtils.kt` (its semantics
  differ from the JDK decoder); do not upgrade or remove it without verification
- Min SDK 26, target SDK 37, compile SDK 37
- Release builds enable R8 minification + resource shrinking; `noR8` variant disables both for crash debugging
- APK is split by ABI (`armeabi-v7a`, `arm64-v8a`, plus universal)
- Firebase telemetry has been removed; do not add new telemetry
