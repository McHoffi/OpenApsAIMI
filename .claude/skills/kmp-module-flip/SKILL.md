---
name: kmp-module-flip
description: Turn an ordinary Android library in this repo into a Kotlin Multiplatform module, and move its code to commonMain. Use when converting a module to KMP, when a KMP module fails to build after conversion, or when deciding what can move to commonMain.
---

# Flipping a module to Kotlin Multiplatform

This is the repeatable part of the KMP migration. It is written from modules already flipped
(`:implementation`, `:database:impl`, `:database:persistence`, `:core:*`, `:ui`, `:plugins:*`,
`:appshell`), so each step is something that has actually gone wrong at least once.

**Keep this file up to date.** When you flip a module and hit something that is not written here, add
it before you finish. When a step here turns out to be wrong or no longer needed, correct it in the
same change rather than working around it. A stale recipe costs more than no recipe, because it is
believed.

## The target state, and what a module arriving from elsewhere has to become

`:plugins:calibration` is the closest thing to the finished shape: 9 files in commonMain, 1 in
androidMain, 1 in iosMain, tests in androidHostTest. Aim at that.

| | target |
|---|---|
| module type | `kotlin("multiplatform")` + `libs.plugins.android.kmp.library` |
| flavours | none - only `:app`, `:wear` and `:wear:watchfacepush` have them |
| DI | Metro only. `@Inject`, `@SingleIn(AppScope::class)`, `@ContributesBinding`; a plugin registers itself with `@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())` **from commonMain** |
| UI | Compose Multiplatform in commonMain; `androidx.compose.*` package names are the same |
| strings | `XxxStrings` (`TextRef`) generated into commonMain; no `R.string` and no `@StringRes Int` in any shared signature |
| platform work | behind an interface in commonMain, implemented in androidMain (and iosMain when it exists) |
| source sets | `commonMain` holds the bulk; `androidMain` holds only ports and Android entry points |
| tests | `androidHostTest`, run by `testAndroidHostTest`; instrumented in `androidDeviceTest` |
| targets | `iosArm64()` and `iosSimulatorArm64()` declared from the start |

A module written against the old architecture - a pump driver from another fork, say - will arrive as
`com.android.library` with an annotation-processor DI framework, XML layouts and ViewBinding,
`R.string` throughout, and a `Context` threaded through its classes. Convert in this order, so each
step is green on its own and the DI stays working:

1. **DI first.** Any annotation-processor DI out, Metro in. Nothing else can proceed while a processor
   is in the module, and this is where the constructor traps below bite.
2. **UI to Compose**, if it still has XML. A View-based screen cannot move to common code at all.
3. **Strings** to the generated `TextRef`s.
4. **Platform ports** - replace `Context` and other Android types with interfaces, checking first
   whether the parameter is used at all.
5. **Flip the module type**, move the sources, add the iOS targets.
6. **Move files to commonMain** and let the iOS compile tell you what is really left.

Then wire it up: `include` in `settings.gradle`; add it to `:appshell` as `api(...)` if it has
screens the navigation graph reaches; add it to `migratedModules` in `ios/shell/build.gradle.kts`
once it builds for iOS; register its string owner in `MainApp` and `BaseTestApp`.

## The hard precondition: no annotation-processor DI in the module

No KMP module in this tree runs a DI annotation processor, and that is not a coincidence - a processor
that generates Java has nothing to generate into in a multiplatform module. Metro is a **compiler
plugin**, so it works everywhere and is the only DI here.

An earlier "it still builds" is usually stale generated output. **Always `rm -rf <module>/build`
before believing a processor is unnecessary.**

## Build file

Copy `plugins/smoothing/build.gradle.kts` for a small module, or `core/ui/build.gradle.kts` for one
with resources, Compose and Robolectric.

- `id("kmp-android-library")` replaces `kotlin("multiplatform")` + the AGP KMP library plugin +
  `kmp-test-defaults`. It also sets `compileSdk`, `minSdk`, the `lint { disable += ... }` block and
  the `iosArm64()`, `iosSimulatorArm64()` and `jvm()` targets. **Not** `com.android.library` - AGP 9
  refuses that plugin together with the multiplatform plugin.
- The module keeps only what is its own: `namespace`, `androidResources`, `withHostTest { ... }`, and
  `minSdk = min(Versions.minSdk, Versions.wearMinSdk)` if the watch uses it.
- The Android convention plugins (`android-module-dependencies`, `test-module-dependencies`,
  `compose-test-module-dependencies`, `jacoco-module-dependencies`) **cannot be applied** - they apply
  `com.android.library`. Write the test dependencies by hand. `all-open-dependencies` can be applied.
  The JaCoCo Robolectric setting and `jvmTarget` come from the root build file; do not restate them.
- `androidResources { enable = true }` - off by default here, unlike a plain Android library.
- There are **no product flavours and no build types**, so `debugImplementation` does not exist.
- `ios/shell` must link every module that builds for iOS: `checkMigratedModules` fails the framework
  link until the module is added to `migratedModules` there.

### Flavours are no longer a problem

Older modules carry a `ProductFlavorAttr` pin to disambiguate a flavoured dependency. **Do not copy
it into a new module.** Product flavours were removed from the library convention plugin, so only
`:app`, `:wear` and `:wear:watchfacepush` have flavours now, and an unflavoured
consumer resolves them without help. If you see the pin in an existing build file, it is left over
and can go.

### The dependency list the convention plugin used to supply

Read `buildSrc/src/main/kotlin/test-module-dependencies.gradle.kts` **before** flipping and copy the
whole list, rather than finding it one compile failure at a time: `kotlin("test")`,
`org-junit-jupiter`, `org-junit-jupiter-api`, `org-junit-platform-launcher`,
`org-mockito-junit-jupiter`, `org-mockito-kotlin`, `joda-time`, `com-google-truth`,
`org-skyscreamer-jsonassert`, `kotlinx-coroutines-test`. Add `libs.org.json.android` and
`org.robolectric` on top, plus the Compose test artifacts.

`libs.org.json.android` is the one that hurts if missed: `isReturnDefaultValues` makes the platform
`org.json` stub return null instead of throwing, and the shared profile fixtures in
`TestBaseWithProfile` then NPE. That once failed **121 of 210 tests** inside the shared base, nowhere
near the real cause.

### If the module has instrumented tests, it needs a second dependency list

`androidHostTest` is the easy one to remember, because a missing dependency there fails the build you
are already running. `androidDeviceTest` does not: nothing local compiles it, so a module can look
completely green and still be broken. `:plugins:sync` was pushed that way and only CI caught it.

Three separate things all have to be restated:

1. **The runner.** `withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`.
   An empty `withDeviceTest { }` builds fine and has nothing to run the tests with.
2. **JUnit 4 dependencies**, from the `androidTestImplementation` lines of
   `test-module-dependencies.gradle.kts`: `androidx-test-ext`, `androidx-test-rules`,
   `com-google-truth`, `org-mockito-android`, `org-mockito-kotlin`, `kotlinx-coroutines-test`.
   Instrumented tests are JUnit 4; the host tests next to them are JUnit 5.
3. **An exclusion, if the module depends on `:shared:tests` from the device test.** That project
   carries JUnit 5, `TestBase` pulls it onto the device classpath, and dexing it fails with
   `Attempt to create a global synthetic for 'Record desugaring'` - JUnit 6 uses Java records.
   ```kotlin
   configurations.named("androidDeviceTestImplementation") {
       exclude(group = "org.junit.jupiter")
       exclude(group = "org.junit.platform")
   }
   ```

Verify locally before pushing - none of this needs a device:

```
./gradlew.bat :module:compileAndroidDeviceTest :module:assembleAndroidDeviceTest --no-daemon
```

Watch for a JUnit 4 test extending a JUnit 5 base class. `GarminDeviceClientTest` extends `TestBase`,
whose `@BeforeEach` never fires under `@RunWith(AndroidJUnit4)`. It happens to work because it only
touches a field initialised at construction, but anything relying on `openMocks` in that base would
get nulls and no warning.

## Source moves

`src/main` → `src/androidMain`, `src/test` → `src/androidHostTest`,
`src/androidTest` → `src/androidDeviceTest`.

**`git mv` fails if the destination directory does not exist yet** - "No such file or directory",
which reads like a git problem and is not one. `mkdir -p` the destination first, in a separate call
(`cd &&` and `&&` between commands are both blocked by this repo's rules).

**Grep the moved tests for the literal strings `src/test/` and `src/main/`.** A hard-coded path
compiles fine and fails only at runtime.

**A file that changes source set can leave the Android compiler with a stale view of it.** The
symptom is an `Unresolved reference` in `compileAndroidMain` for a symbol that is plainly there, in
the same package, in the same source set as the file that cannot see it - and it survives re-running
the task. Do not go looking for the missing declaration and do not clean the build. Delete that one
task's incremental state and compile again:

    rm -rf core/graph/build/kotlin/compileAndroidMain

In `:core:graph` a move of `DoubleDataPoint` from commonMain to androidMain left
`AreaGraphSeries.kt` unable to resolve it, while the three iOS compiles, the JVM and the common
metadata compiles were all clean. That split - one target red, every other target green, symbol
present on disk - is the signature.

Task names change with the layout, and a wrong name **runs no tests and still exits 0**:

| source set | task |
|---|---|
| `androidHostTest` | `testAndroidHostTest` |
| `androidDeviceTest` | `connectedAndroidDeviceTest` |
| plain Android library | `testDebugUnitTest` |
| `:app` / `:wear` | `testFullDebugUnitTest` |

`.circleci/config.yml` names all of them, and
`buildSrc/src/main/kotlin/jacoco-aggregation.gradle.kts` picks the variant directory per module. If
you change a module's shape, check both.

## The Metro construction trap

This is the worst one, and it has happened four times. A class that nothing contributes is **never**
constructed by `app/src/test/.../di/metro/TestRoot.kt`. The moment it gets `@ContributesBinding`,
Metro builds it for real in every graph test, with every dependency resolved for real.

| class | work done at construction | symptom |
|---|---|---|
| `VersionCheckerUtilsImpl` | property init opens `definition.json` | infinite read loop, OOM - looks like a machine problem |
| `OneTimePassword` | `init { configure() }` generates and persists an OTP secret | NPE, 11 graph tests |
| `AuthFlowOut` | property init builds `AuthorizationService`, which inspects installed browsers | `ExceptionInInitializerError` |
| `NotificationManagerImpl`, `FabricPrivacyImpl` | notification channel, Firebase flags, `while(true)` loop | not converted - see below |

**Before adding `@ContributesBinding` to a class, read its property initializers and `init` block for
I/O, sockets, or anything blocking.** Usually the fix is `by lazy { ... }`, which is better in
production too, since building the graph should not do file I/O.

**Laziness is not always right.** Where the `init` work is a *startup obligation* - registering a
receiver, setting analytics flags, starting a periodic loop - deferring it changes behaviour. Those
need an explicit `start()` from `MainApp.onCreate()`, which is a real refactor.

**And an explicit `start()` has to be called by three shells, not one.** `MainApp.onCreate()` calls
several; `desktop/shell/.../Main.kt` and `ios/shell/.../IosAppStartup.kt` call almost none. A missed
call is silent - the class exists, the screens render, the work never happens. Prefer `by lazy` unless
the work really is an obligation, and when it is, add the call to all three at the same time.

**The clients make this worse than tests do.** `DesktopAppGraph` and `IosAppGraph` are built
**eagerly at startup, before any window exists**, so a constructor that opens a file or a socket fails
at launch there rather than in a test you can re-run. Anything reachable from `ClientGraphBindings`
gets this treatment on both platforms.

**Constructors must not write.** Two classes still do - `InsulinImpl` (`init { bootstrap() }` can
`putRemote`) and `ProfileRepositoryImpl`. Building a graph should never author a persisted write, and
on a paired client `putRemote` is on the sync path, so a graph build can publish a config change.

To confirm this class of failure: `git stash` the change and re-run the same test task. A baseline
that passes in about a minute against a run that never finishes is unambiguous.

`IntelligoPlugin` is the fifth case, and it fails at the opposite end: it is constructed, but its
constructor does not survive the mocks. Every test that reads `contributedPlugins` dies on it with
`NullPointerException: parse(...) must not be null at IntelligoPlugin.<init>`, and because the map is
built eagerly, that is *all* of them - the assertions never run. Read a plugin's constructor before
trusting a plugin-list test to tell you anything.

## A lost contribution is silent, and so is a duplicated one

Neither failure is a compile error, which is what makes this the second-worst trap after the one
above. A plugin whose `@ContributesIntoMap` was dropped simply never appears in the app; two plugins
sharing one `@IntKey` silently overwrite each other in the map. Both look exactly like a build that
worked.

**Apply `libs.plugins.metro` to every converted module that has `@Inject` files.** Without the
plugin, the module's `@ContributesTo` and `@ContributesIntoMap` hints and its inject factories are
never generated, so every binding it owns vanishes from the graph at once. A rewritten `build.gradle.kts`
that drops the old Hilt/KSP processors and forgets to add Metro looks fine and compiles - it just
contributes nothing. `:plugins:main` shipped that way.

**HEAD is the checklist.** The migration moved registrations from `@Binds @IntoMap @IntKey(n)` in a
`*PluginsListModule` to either an annotation on the plugin class or a `@Provides @IntoMap` entry in a
`@ContributesTo @BindingContainer`. So before believing the conversion is done:

```
git grep -n "abstract fun bind.*Plugin(plugin" HEAD   # every plugin the map used to hold
git grep -n "@IntKey(" HEAD -- '*PluginsListModule.kt' # with its order key
```

...then confirm each one exists in the working tree with the **same key**.

**Check both registration forms before "restoring" a missing one.** A scan of annotations above
`class` declarations cannot see the container form or a graph-extension declaration, so it reports
false positives - and adding an entry that already exists elsewhere is worse than the gap it was
meant to fix: the plugin lands in two scopes as two objects, or in two buckets under one key. Every
candidate needs a second look at `@Provides ... : PluginBase` in `*Bindings.kt` / `*Registrations.kt`
/ `*MetroGraph.kt`:

| plugin | where it actually lives | a class-only scan says |
|---|---|---|
| `OpenHumansUploaderPlugin` | `@Provides` in its own `OpenHumansMetroGraph` | missing |
| `SmsCommunicatorPlugin` | `@Provides` in `SyncPluginsBindings`, `@NotNSClient` | missing |
| `IobCobCalculatorPlugin`, the APS plugins | `@Provides` in `MainPluginsBindings` / `ApsPluginRegistrations` | missing |

Restoring `SmsCommunicatorPlugin` onto the class is the trap inside the trap: its entry carries
`@NotNSClient`, so the "fix" both duplicates key 300 and puts the SMS communicator into follower
builds that never had it. A qualifier lives with the *entry*, not with the class - see the KDoc in
`SyncPluginsBindings` for why Metro rejects `binding<@NotNSClient PluginBase>()` here.

**And check the keys against each other, not only against HEAD.** `LinearCalibrationPlugin` was
converted with `@MetroIntKey(700)`, which `NoCalibrationPlugin` already held - HEAD had 700 and 710
respectively. Nothing failed; one calibration plugin just replaced the other. Grep every `@IntKey`
in the working tree and look for a key that appears twice.

The graph tests under `app/src/test/.../di/metro/` are the intended net for this, but they are not
an oracle while they are stale: `ContributedPluginsTest` still asserts a key list written before
AIMI, Boost and BoostV5 (225, 231, 239) were registered. Compare the list against
`ApsPluginRegistrations.kt` before trusting a failure from it.

## Moving a whole feature tree to androidMain

The other direction. When a module is mostly shared but a feature inside it is not - a fork's own
plugins, a screen built on activities, anything that exists on one platform only - the answer is
usually to move that feature's **whole tree** down to androidMain, not to rewrite it file by file.

Do this when the feature is not yours to redesign. An estimate that says "318 files, 14 409 errors,
most of them mechanical" is still 318 files of somebody else's algorithm, and a rewrite means
re-reading all of it. A move means the feature keeps working exactly as it did on the platform it
was written for, and the others simply do not get it - `:plugins:source` is the precedent: about 20
CGM plugins sit in its androidMain with `@ContributesIntoMap`, and nothing else changed.

### Decide first: does any other module's commonMain name it?

**A file stays in commonMain if another module's commonMain imports it.** That is the whole rule.
androidMain can see commonMain, so a moving file may keep using shared code; it is the reverse that
breaks. Grep every consumer before you start:

    grep -rln "app\.aaps\.plugins\.aps\.openAPS" --include=*.kt --exclude-dir=build . \
      | grep "/commonMain/" | grep -v "^./plugins/aps/"

Two kinds of hit look the same and are not:

- A **real** import (`import app.aaps.plugins.aps.openAPSAIMI.orchestration.AimiLoopRuntimeGuard`)
  pins that one file to commonMain.
- A **string** naming a class - `screenOpener.open("app.aaps.plugins.aps.openAPSAIMI.advisor.…Activity")`
  in `:ui`'s commonMain - is not a compile-time dependency at all. Those activities move freely, and
  the "visibly absent" outcome is exactly right: the button opens nothing on iOS.

In `:plugins:aps`, 318 of 323 failing files moved and **one** stayed (`AimiLoopRuntimeGuard`), because
`:plugins:main`'s calculator reads it on every target. Finding that one before the move is much
cheaper than finding it in a failed iOS build afterwards.

### The move itself: per file, never per directory

`git mv` of a whole directory is wrong when the destination already holds part of the tree -
`openAPSAIMI/context/` existed in androidMain, so moving `openAPSAIMI/` on top of it nests instead of
merging. Move file by file, and **pre-check for collisions** (a source path that is also a
destination path would silently overwrite):

    find "$SRC" -name '*.kt' | while IFS= read -r f; do
      rel="${f#$SRC/}"; mkdir -p "$DST/$(dirname "$rel")"; mv "$f" "$DST/$rel"
    done

Check for `expect`/`actual` inside the trees first (they must not be split), and check `commonTest`
too - a test of a moved class has to move with it.

### Moving files between source sets breaks tools that point at paths

A path is not a compile-time dependency, so nothing tells you when a move invalidates one. The
`analyzer/` tool is the one in this repo: its Python scripts hard-code the source directories they
scan, and after the AIMI/Boost/AutoISF move to androidMain six of them still pointed there - plus a
stale comment in `generate_data.py` and a constant in `enrich_params.py` that was already dead.

Two of the three failure modes are quiet, which is what makes this worth checking:

- `FileNotFoundError` before writing anything - the three `generate_*_settings_paths.py` scripts.
- **A scan that finds nothing and still exits 0** - `generate_boost_data.py` and
  `generate_autoisf_data.py` walk `BOOST_SRC`/`AUTOISF_SRC`, find no directory, write empty JSON and
  report success. `update.sh` runs each generator as `run_gen ... || echo "⚠️ failed"`, so `set -e`
  does not stop it and the script still prints "✅ Done" at the end. Running it in that state
  replaces `boost_parameters.json` and `autoisf_parameters.json` with empty data.

So after a move, grep the scripts for the old source-set name and check every hit resolves:

    grep -rn "src/commonMain" analyzer/scripts/*.py
    python - <<'EOF'   # every literal source path in the scripts must exist
    import re, pathlib
    repo = pathlib.Path(".")
    for py in sorted((repo / "analyzer/scripts").glob("*.py")):
        for line in py.read_text().splitlines():
            for m in re.finditer(r'["\']((?:plugins|core|shared|implementation|database|ui)/[^"\']*?src/[^"\']*)["\']', line):
                if not (repo / m.group(1)).exists():
                    print("MISSING", py.name, m.group(1))
    EOF

Directory-joining constants (`BOOST_SRC` + `BOOST_SRC_DIRS`) are not literal paths, so check those by
counting the `.kt` files each root now finds. A root that resolves to 0 files is the silent case above.

The generated JSON under `analyzer/data/` holds UI navigation paths and a `source_commit`, not source
paths, so it does not need editing by hand. It is checked in, though: run the generators once the
scripts are correct and commit the result with them, or leave it alone - do not hand-edit it.

Two more traps in the same tool, one about the backend and one about the data it works from.
`analyzer/backend/app.py` is **baked into the Docker image** - compose mounts only `data/`,
`frontend/` and `history/`, so a backend edit changes nothing until the image is rebuilt
(`docker compose up -d --build`, or `analyzer/deploy.sh`). And the settings it analyzes are
**phone-wide**: `/api/decrypt-export` returns every preference on the device, so a per-plugin view
has to filter by key itself. The backend derives that set from the parameter files (`foreign_keys`)
and leaves a key alone when two other plugins both list it - those are the shared AAPS-core
preferences such as `enableSMB_always` and `carbsReqThreshold` that AIMI reads without listing them,
so dropping them would hide settings that really do change its behaviour. Check any such filter
against the plugin's Kotlin source, not only against its data file.

### When one Android-only signature drags a shared read-surface down

The hard case: the file that must stay in commonMain is *almost* free of platform types, but it reads
state that only an Android-only class can write - and that class is blocked by something worse (in
`:plugins:aps`, `AimiLoopTelemetry` took `AimiHormonitorStudyExporterMTR`, 391 errors by itself).

Do not move the writer back, and do not stub the reader. Put the **state** in its own tiny commonMain
object and let both sides talk to it:

    object AimiLoopTickState {
        @Volatile var activeTickId: Long = 0L
            private set
        @Volatile private var activeTickStartedWallMs: Long = 0L
        fun beginTick(tickId: Long, wallClockMs: Long): Long { … }   // returns the previous id
        fun endTick(previousTickId: Long) { … }
    }

The shared reader (`AimiLoopRuntimeGuard`) then has no dependency on the Android class at all, and the
Android writer keeps its own heavy imports. Two `@Volatile Long`s is the entire cost.

### A seam the other platform has to answer for

Where shared code genuinely needs the Android feature - not just its state - the seam is an interface
in `core/interfaces` commonMain, implemented in `androidMain`:

    @Inject @SingleIn(AppScope::class)
    @ContributesBinding(AppScope::class, binding = binding<AimiContextIntentInjector>())
    class AimiContextIntentInjectorImpl(private val contextManager: ContextManager, …) : AimiContextIntentInjector

Make it a `fun interface` when the "not here" answer is trivial: the client graphs can then bind a
lambda that logs and returns `false`, with no new class. On iOS and desktop the Nightscout note is
logged and dropped, and BOOST's meal-hypothesis history is permanently empty - the "visibly absent"
outcome, **spelled out in the binding's KDoc** rather than hidden behind a stub that pretends to work.

That binding lives in `:shared:clientbindings`, which only the two client shells include, so it cannot
collide with the Android one. If you instead had to add it to a container both share, you would get
two bindings of one interface - a graph error, not a warning.

### The answer belongs in one of two packages in `ios/shell`

Which one is not a style choice - it is a claim about the code, and each package has its own log level:

| package | means | logs |
|---|---|---|
| `ios/shell/missing/` | work not done yet; delete the file when the thing is ported | `aapsLogger.notOnIosYet(...)` - **error** (`failNotOnIosYet` logs and throws) |
| `ios/shell/platform/` | an answer about what an iOS client *is*; the file stays, or is deleted if the platform ever grows the feature | `aapsLogger.notOnThisPlatform(...)` - **debug** |

A method that returns `false` because iOS has no Android broadcast channel is `platform/`. A method
that returns `IDLE` because the algorithm it reads is still Android-only is `missing/`. Writing the
first into `missing/` claims porting work that will never be done; writing the second into `platform/`
hides real remaining work behind a story about the platform.

Each one carries a KDoc saying which it is and what would delete it - `IosAuditorStateProvider` names
the view model that forces its existence, `IosSkinDescriptionProvider` says a skin is a `LinearLayout`
swap and that "if an iOS skin system is ever built, this file is deleted rather than edited".

Answer the *entry* question and stay quiet on the cheap dependent getters: `IosEversenseCalibrationSource.isEnabled()`
logs, `isConnected()`/`readinessMessage()` do not.

**Missing bindings surface one at a time, at the end of the chain.** Metro reports
`[Metro/MissingBinding] X` against the `@DependencyGraph` interface line, not the accessor. Fix the one
it names and compile again for the next - do not try to guess the full list in one pass, and do not
read the report as "only one is missing".

### Android calls and what replaces them in commonMain

| Android | commonMain |
|---|---|
| `SystemClock.elapsedRealtime()` | `TimeSource.Monotonic.markNow()` held in a field, `.elapsedNow().inWholeMilliseconds` |
| `System.currentTimeMillis()` | `dateUtil.now()`. **Not** `Clock.System.now()` - see the trap below |
| `assert(x)` | `devAssert(x)` (`app.aaps.core.data.model`, actuals for every target) |
| `synchronized(lock) { }` / `@Synchronized` | `AapsLock()` + `lock.withLock { }` |
| `android.os.Handler` as a timer | `appScope.launch { delay(ms); while (isActive) { …; delay(ms) } }`, job kept in a field and cancelled in `onStop()` |
| `handler.postDelayed(r, ms)` | `appScope.launch { delay(ms); … }`; for the "cancel and re-arm" idiom, `job?.cancel(); job = appScope.launch { … }` |
| `Build.MANUFACTURER + " " + Build.MODEL` | `config.deviceModelForUpload` (same string, and it is already the transmitted format) |
| `Handler` + `Runnable` field | a `Job?` field. Check the old field is not read from outside the file before making it private |
| `@SuppressLint("CheckResult")` | delete it - the lint it silences does not exist off Android |

`kotlin.concurrent.Volatile` and `kotlin.concurrent.atomics.AtomicLong` are the multiplatform forms of
the `@Volatile` annotation and `java.util.concurrent.atomic`.

### The `Clock` import that resolves and still fails

`import kotlinx.datetime.Clock` compiles far enough to resolve `Clock`, then fails with
**`Unresolved reference 'System'`** on `Clock.System.now()` - `kotlinx.datetime.Clock` is a deprecated
alias for the type, without the companion. The repo's convention, and the one that works, is:

    import kotlin.time.Clock

No `@OptIn` is needed. The error names `System`, not `Clock`, which is what makes it confusing: the
unresolved reference is the *member* of a type that resolved to something subtly different.

### Keep the plugin map keys where they were

When a feature's plugins move to androidMain, their registration moves with them - and the `@IntKey`
values must be **the same numbers**. Split the container rather than the map: keep the cross-target
plugins in the commonMain `*Registrations.kt` and give the moved ones a sibling
`*ForkPluginRegistrations.kt` in androidMain, in the same `@ContributesTo(AppScope::class)
@BindingContainer` style. Metro generates the hints from androidMain, so on iOS the plugins simply are
not in the map - which is the intent, and is why the unqualified bucket (`@AllConfigs` semantics)
still merges cleanly in `:app`.

Then re-grep **every** `@IntKey` in the working tree for a duplicate. A key that appears twice is
silent: one plugin replaces the other, and no build fails.

## Moving code to commonMain

Counting files with no `android`/`androidx`/`java` import over-estimates badly: a
file can name `app.aaps.core.ui.R` or take a `Context` indirectly. Compile for iOS to find out.

Beware the grep, too: `^import android` also matches `androidx`, so it hides every Compose file.
Anchor it as `^import android\.`.

### Strings are usually the biggest single blocker

`R.string.x` cannot exist in commonMain. The fix is `GenerateKeyStringsTask`, which turns the
module's `strings.xml` into a `XxxStrings` object of `TextRef.Named` (commonMain) plus a
`XxxStringIds` map (androidMain). Add one line for the module to `StringOwnerModules.ALL` in
buildSrc, then `val generateXxxStrings = registerKeyStrings("xxx")` in the module's build file. The
build fails if the owner is not in the list. Then:

1. Add `kotlin.srcDir(...)` for the common output to `commonMain` and the android output to
   `androidMain`, and `implementation(project(":core:keys"))` to commonMain for `TextRef`.
2. Nothing to register by hand. Every shell (`:app`, its instrumented tests, desktop, iOS) generates
   `GeneratedStringOwners` from `StringOwnerModules.ALL`. Add one of the module's strings to
   `DesktopStringOwnersTest` and `IosStringOwnersTest`.
3. Swap `R.string.foo` for `XxxStrings.foo`. The substitution is name-preserving, so a wrong mapping
   cannot happen silently - it fails to compile.
4. In Composables import `app.aaps.core.ui.compose.stringResource` alongside the androidx one. Both
   are called `stringResource`; Kotlin picks by parameter type.

Sweep **every** receiver spelling, not just the obvious one: `rh.gs(R.string.x)`,
`resourceHelper.gs(...)`, the fully qualified `app.aaps.plugins.foo.R.string.x`, and any aliased
`FooR.string.x`. Each of these has been missed once and found only by a failing test.

After the swap, unwrap `TextRef.AndroidRes(XxxStrings.x)` - the argument is already a `TextRef`.

**Tests need the same swap**: `whenever(rh.gs(R.string.x))` becomes `whenever(rh.gs(XxxStrings.x))`,
and a blanket `rh.gs(anyInt())` stub becomes `doAnswer { ... }.whenever(rh).gs(any<TextRef>())` -
written that way round because `rh.gs(any<TextRef>())` on its own is ambiguous against the vararg
overload. If the module's owner is not registered in `shared/tests/TextRefStubs.kt`, an unstubbed
name resolves to itself, so expectations like `isNull()` become the string's own name. A Robolectric
Compose test must call `TextRefIdRegistry.register(owner) { XxxStringIds.idOf(it) }` in its setup,
exactly as `MainApp` does.

### A screen that inflates an XML layout stays on Android

**ViewBinding does not exist in a KMP module.** `viewBinding` cannot be turned on (AGP refuses
`com.android.library` next to the multiplatform plugin), so there are no generated `XBinding` classes
and no `databinding` package. A Fragment, a DialogFragment, a `RecyclerView.Adapter` that inflates a
row layout, and a `PagerAdapter` all belong in `androidMain` - moving them "to keep the package
together" fails the Android compile on the first import.

Compiling for Android is not the test. commonMain is compiled into **every** target, so
`import android.widget.Button` in commonMain passes `compileAndroidMain` and breaks
`compileKotlinJvm` and iOS later, when the reason is far less obvious. Run
`./gradlew <module>:compileKotlinJvm` in the same session as the move.

Where a screen has to move anyway, the replacement for the missing binding class is a plain class of
`val`s: `OverviewFragmentViews` in `:plugins:main` is the one to copy. Keep the **property names the
AGP-generated class used** (`binding.btnStart` becomes `views.btnStart`), give `root` the real widget
type of the layout root, and find the views once in a `from(root: View)` factory. Renaming the
properties instead turns a mechanical port into an edit of every call site, for no gain.

Two traps when such a screen is reachable from commonMain code:

- **A plugin's `PluginDescription` cannot name an androidMain Fragment.** `.fragmentClass(X::class.java.name)`
  in commonMain does not compile once `X` moves, so write the name out:
  `.fragmentClass("app.aaps.plugins.sync.nsclientV3.RemoteControlFragment")`. Note that
  `PluginDescription.fragmentClass` is read by no one today - the Compose plugin list does not use
  it - so the string is inert, and the whole XML screen behind it may be dead code worth asking about.
- **A class that only Android needs can still block every other target, one level down.** An Android
  impl of a commonMain interface (`ProcessedDeviceStatusDataImpl` over
  `ProcessedDeviceStatusData`) moves to androidMain and compiles, but any root graph that asks for
  the interface on iOS or desktop now has no binding. Check who injects the interface before
  calling the move done.

### A drawable becomes an `ImageVector`, path by path

`painterResource(R.drawable.x)` is Android-only. When the screen moves to commonMain, the drawable
becomes an `ImageVector` in `:core:ui`'s `compose/icons` package - copy `IcSmb.kt`.

**Transcribe the SVG paths literally. Do not loop over paths that look almost the same.** Three
"identical" waves in `ic_dashboard_wave` differed in the second decimal (`0.42` vs `0.43`, `1.95` vs
`1.93`), and the top one ended with an absolute `V9.49` where the other two used a relative `v-1.95`.
A `forEach` over near-identical values renders something that looks almost right, which is the worst
way to be wrong. The same care applies to `fillType="evenOdd"` → `pathFillType = PathFillType.EvenOdd`
and to `<group translateX/Y>` → `group(translationX =, translationY =)`.

Then the call site takes `icon: ImageVector` and `Icon(imageVector = icon, ...)`, and the
`@DrawableRes` annotation goes with the `Int` parameter.

The XML drawable stays where it is - `:core:ui`'s `androidMain` still needs it for the Android views
that read it, and other flavours may reference it by name.

### A port only one target can implement: composition local with a safe default

Some shared code has to do something that only Android can do at all - open another screen by class
name, show the app's launcher icon. There is no Android implementation to inject into an iOS graph,
and the KMP rules here say the feature must then be **visibly absent**, not present and dead.

The shape to copy is `LocalAppIcon` in `core/ui/.../compose/AapsTheme.kt`:

1. a small interface in commonMain, with an `Unavailable` object that reports `isAvailable = false`
   and does nothing (`ScreenOpener.kt`);
2. `val LocalScreenOpener = compositionLocalOf<ScreenOpener> { ScreenOpener.Unavailable }` - the
   default is the safe one, so a host that says nothing gets the absent behaviour;
3. the real implementation in the same module's **androidMain** (`AndroidScreenOpener`, which keeps
   the caller's old `try/catch` around `startActivity`);
4. one `provides` line in each host that can honour it - `ComposeMainActivity` for Android. The iOS
   and desktop hosts simply do not provide it.

Call sites then read `LocalScreenOpener.current` and gate the control on `isAvailable` next to the
condition that already decided whether the control belongs there:

    if (isAIMIActive && screenOpener.isAvailable) { AuditorIconButton(...) }

This is also what keeps the graph clean: an Android-only `@ContributesBinding` for such a port would
leave every non-Android root graph without a binding, which is the trap named above. Decide per call
site what "absent" means - a button that only opens the other screen is hidden, while a card that
shows data of its own stays and only loses its tap action.

### `androidx.compose.ui.res.stringResource` is Android-only

It resolves for the Android and JVM targets, so a commonMain file that imports it compiles and then
fails the iOS compile with `Unresolved reference 'res'`. Importing it *next to*
`app.aaps.core.ui.compose.stringResource` is legal - Kotlin picks by parameter type, `Int` vs
`TextRef` - so a file can carry both and only the Android build notices. Grep commonMain for it when
a `:ui`-style module fails on `res`; delete it where every call passes a `TextRef`.

**Unless the whole file is fork UI, and then it moves instead.** Converting it costs a `XxxStrings`
entry per label, in a screen iOS will never show. When every consumer is Android - an activity, an
Android-only plugin module, a test - the file belongs in androidMain with its `R.string` ids intact.
`:plugins:source` is where that was decided: ten fork CGM files under `compose/` moved as one set,
while upstream's three `BgSource*` files stayed in commonMain on `SourceStrings`.

Move the set, not the failing files. A file that compiles today still has to go if it reads a class
declared in one that must move: `CgmWarmupRing.kt` took `CgmUiState` from `CgmStateChip.kt`, so it
was Android-only in fact while the compiler still accepted it in commonMain.

### The `String.format` family in a shared screen

Each of these has one replacement in this repo:

| Android/JVM | commonMain |
|---|---|
| `System.currentTimeMillis()` | `dateUtil.now()`, or `Clock.System.now().toEpochMilliseconds()` where no `DateUtil` is in reach |
| `"...".format(...)`, `String.format(...)` | `formatTemplate(template, listOf(...))` (`app.aaps.core.interfaces.resources`) |
| `SimpleDateFormat("HH:mm")` | `dateUtil.timeString(ms)` - and drop the `remember` that held the formatter |
| `Dispatchers.IO` | `aapsIoDispatcher` (`app.aaps.core.interfaces.concurrent`) |
| `ResourceHelper` as a constructor type | `TextResolver` - every `rh.gs(...)` call site stays the same |

A Composable gets `dateUtil` from `LocalDateUtil.current`. That local has **no default** - it
`error()`s - so check that a host renders the screen inside the provider before using it.
`AapsAppRoot` (iOS, desktop) and `ComposeMainActivity` (Android) both provide it.

`formatTemplate` is not only a compile fix: it routes numbers through the repo's `NumberFormat`, so
decimal separators stay consistent with the rest of the app.

### A `remember` block that reads a theme value needs that value as a key

Moving a colour from `ContextCompat.getColor(...)` to an `AapsTheme` token is not finished when it
compiles. If the colour is read inside `remember(a, b, c) { ... }`, a light/dark switch does not
re-run the block and the screen keeps the old palette. Read the colours into `val`s above the block
and add them to the key list.

### Android-only members of a common interface: sub-interface in androidMain

When upstream's version of a commonMain interface has no room for the fork's Android-only members
(`UiInteraction.showOkDialog`, `Overview.setVersionView`, `BgQualityCheck.icon`), do not widen the
common interface back and do not delete the members. Add a sub-interface in **androidMain**, in the
same package:

    interface UiInteractionAndroid : UiInteraction { fun showOkDialog(...) }

`@ContributesBinding` is `@Repeatable` and takes `binding = binding<T>()`, so one Android
implementation binds to both types in one place:

    @ContributesBinding(AppScope::class, binding = binding<UiInteraction>())
    @ContributesBinding(AppScope::class, binding = binding<UiInteractionAndroid>())
    class UiInteractionImpl(...) : UiInteractionAndroid

A caller that needs the Android members injects the `...Android` type; everyone else keeps injecting
the common type. This is far smaller than casting at every call site. A cast (`x as XxxAndroid`) is
only needed where the object is created outside the graph and handed out through the common type -
`OverviewPlugin` and `BgQualityCheckPlugin` have a `@Provides` for exactly that.

**If the common interface holds state, the Android class must be a decorator, not a second
implementation.** `ProcessedDeviceStatusData` is the worked example: upstream's class stays in
commonMain and owns the values, and `ProcessedDeviceStatusDataAndroidImpl` injects it and forwards
every lean member (`get() = delegate.pumpData`, `set(v) { delegate.pumpData = v }`), adding only the
`Spanned`/HTML members. Two independent implementations would mean the sync writing one object while
the screen reads the other - the screen would quietly stop updating, with nothing failing to build.
Give the decorator its own binding (`binding = binding<ProcessedDeviceStatusDataAndroid>()`) so both
types resolve from the one object.

The common interface itself goes back to upstream's version: `git diff upstream/dev -- <path>`
printing nothing is the goal. Also check the generated factory to confirm what the graph resolves:
`javap -p -c <module>/build/classes/kotlin/android/main/<pkg>/XxxMetroFactory.class` shows the
provider's type and any leftover `checkcast`.

### An `Int` in an interface is a hard stop

`PumpEnactResult.comment(Int)` and `HardLimits.verifyHardLimits(..., valueName: Int, ...)` take a
resource id in the **interface**. A `TextRef` overload exists for `comment`; where one does not, the
implementation cannot move until the interface changes.

### Kotlin/Native rejects a comma in a backticked test name

`fun \`the tag is appended, making it longer\`()` compiles on JVM and fails Native with
`Name contains illegal characters: ","`. It only shows up once a test reaches commonTest, so a
JVM-only test can carry one for years. Rewrite the name; do not rename the test's meaning.

### Moving crypto: the provider is stricter than javax was

`javax.crypto` built a fresh `Cipher` on every call, which hid API misuse. A multiplatform provider
reuses objects and enforces the rules, so a migration can fail on something that was always wrong.
Moving `ClientControlCrypto` turned up **two tests reusing one IV with one key** for AES-GCM -
forbidden, and the provider says so (`Cannot reuse iv for GCM encryption`). Production was fine
because the IV is generated per use; only the fixtures were wrong.

Two rules when the format is already on the wire:

- **Keep golden vectors and put them in commonTest.** Digests minted by the old implementation are
  what prove the new one emits the same bytes, and in commonTest they run on every target the module
  builds for rather than only on the JVM.
- **Watch the packaging, not the algorithm.** The primitives interoperate by definition; the silent
  breakage is in how they are assembled - whether the AEAD nonce is prepended or stored separately,
  whether the GCM tag is appended, hex case. In cryptography-kotlin the plain `encryptBlocking`
  generates and prepends its own nonce; `encryptWithIvBlocking` (behind `@DelicateCryptographyApi`)
  is the one that matches a format storing the IV separately.

### Positional `mock()` constructor arguments hide a wrong wiring

Tests here build big plugins positionally, with long runs of bare `mock()`. Adding or removing a
constructor parameter shifts everything after it, and nothing complains: `mock()` fits any type.

The failure surfaces far away and looks nothing like the cause. Passing an unstubbed `mock()` where
the class collects a `Flow` gives a **null** upstream, which fails as
`UncaughtExceptionsBeforeTest` in whatever test happens to run next - not in the test that caused
it, and not with a message naming the parameter.

- Do not target these lines with `sed -i '<line>s/.../.../'`. Line numbers shift as soon as an
  import or a field is added above, and the edit then lands on the wrong call.
- After changing a constructor, grep every construction site and check the argument that matters is
  the **named field**, not a fresh `mock()`.
- `git stash` and re-run to tell "my change broke this" from "this was already flaky". The suite has
  a real `UncaughtExceptionsBeforeTest` flake, so the two are easy to confuse.

### Splitting a WorkManager worker

A worker is almost always a body wrapped in a class WorkManager can construct. `RunnerWorker` and
`WorkOutcome` in `:core:objects` exist for this: the body becomes a `XxxRunner` in commonMain with
`suspend fun run(): WorkOutcome`, and the worker keeps only the `@AssistedInject` scaffolding.

The nine NS client workers all transformed the same way, so it is scriptable - drop the
`@Assisted context`/`params` and `fabricPrivacy` parameters, make `aapsLogger` a `private val`, swap
`@AssistedInject constructor` for `@Inject`, drop the `LoggingWorker` supertype and the
`@AssistedFactory`, and map the returns:

| worker | runner |
|---|---|
| `Result.success()` | `WorkOutcome.Success` |
| `Result.success(workDataOf("Result" to x))` | `WorkOutcome.Skipped(x)` |
| `Result.failure(workDataOf("Error" to x))` | `WorkOutcome.Failure(x)` |

**Review the mapping table by hand afterwards** - it is the only part that carries meaning. A
`Result.success` with output data is not the same as a bare one: `WorkOutcome.Skipped` was added
precisely because `LoadBgWorker` reported "Load not enabled" that way, and collapsing it into
`Success` silently dropped a signal a test was asserting on.

Worker tests construct the worker directly, so each needs its argument list wrapped:
`XxxWorker(appContext, params, aapsLogger, fabricPrivacy, XxxRunner(aapsLogger, ...rest))`.

### `@IntKey` collides with the preference keys

Registering a plugin needs `dev.zacsweers.metro.IntKey`, and any class that also reads preferences
imports **`app.aaps.core.keys.IntKey`**. Kotlin then refuses both: `Conflicting import: imported name
'IntKey' is ambiguous`. The fix is an alias, which `LoopPlugin` and `AutotunePlugin` have always used:

```kotlin
import dev.zacsweers.metro.IntKey as MetroIntKey
...
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(310)
```

This is worth knowing because the clash has been misdiagnosed before: `SyncPluginsBindings` recorded a
**Dagger** error (`InjectProcessingStep`, `error.NonExistentClass`) as proof a plugin "cannot" be
annotated, and that false reason was then copied to another module. Metro has no annotation processor
and emits no such diagnostic. If a class seems to refuse `@ContributesIntoMap`, read the actual
compiler error before writing down a reason.

### Two Metro forms this version will not take

Neither is a blocker, but both look like the obvious idiom and both fail:

- **`@Binds` cannot live in an `object`** - "Extension property must have accessors or be abstract".
  Every binding container here is an `object`, so an alias `@Provides fun x(impl: XImpl): X = impl` is
  the right form, not a thing to convert.
- **`binding<@Qualifier Type>()` is rejected** - `Inapplicable candidate(s): constructor(scope:
  KClass<*>, binding: binding<*> = ...)`. So a **qualified** map entry still needs a stated
  `@Provides` in a container; only unqualified ones can move onto the class.

### Other common blockers

`javax.inject` (swap to `dev.zacsweers.metro.Inject` only for a class Metro already builds),
`@Synchronized`, `org.json`, `java.util.Calendar`, and `System.currentTimeMillis()` - the last is
just `Clock.System.now().toEpochMilliseconds()`, with **`import kotlin.time.Clock`**. There is a
`kotlinx.datetime.Clock` too and it is the wrong one here: the import line itself compiles, and the
use then fails with `Unresolved reference 'System'`, which reads as if the call were wrong rather
than the import. Kotlin's stdlib `Clock` is what the rest of the tree uses.
`System.nanoTime()` is monotonic, so it is **not** interchangeable with `Clock`: the shared stand-in
is `TimeSource.Monotonic.markNow()` / `elapsedNow()` (`LoopPlugin` keeps its uptime that way). A
source set that only builds for the JVM - `jvmSharedMain`, `androidMain`, a host test - can keep
`System.nanoTime()`; only commonMain has to change.

The date and locale family is worth naming together, because each one is an innocent-looking import
and none of them fails an Android build: `java.util.Locale`, `java.text.SimpleDateFormat`,
`java.time.Instant`, `java.io.File`. **The grep for them over-reports badly**, so count before you
plan: a file whose only users are in androidMain is *moved*, not rewritten. In `:core:graph` 5 files
carried 27 errors and all 5 moved as they were.

`java.util.Locale` usually appears for one reason only - `String.lowercase(Locale.ROOT)` or
`Char.titlecase(Locale.getDefault())` - and the locale-free overload (`lowercase()`, `titlecase()`)
is already right for identifier text, which is what such a call almost always is. Deleting the
argument is the whole fix.

`androidx.activity.compose.BackHandler` is Android only, and `compileCommonMainKotlinMetadata` will
not tell you - it is a Compose import like any other. The multiplatform replacement is already on the
classpath (`libs.jetbrains.androidx.navigationevent.compose`):

    import androidx.navigationevent.NavigationEventInfo
    import androidx.navigationevent.compose.NavigationBackHandler
    import androidx.navigationevent.compose.rememberNavigationEventState

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCompleted = { onBackClick() }
    )

One handler per condition, each with its own `rememberNavigationEventState`. A screen that must also
never fall through to the nav stack adds one more with `isBackEnabled = true` and the screen's own
`onBackClick`; `PreferenceSubScreenHost` keeps that one first and the state handlers after it.

`java.util.concurrent.ConcurrentHashMap` is easy to miss: it looks like ordinary Kotlin, but it is a
JVM type. It resolves for the Android and JVM targets, so an Android build stays green, and then the
module's iOS compile or its `compileCommonMainKotlinMetadata` fails. A shared map that has to be
thread safe becomes a plain `mutableMapOf` guarded by `AapsLock`
(`app.aaps.core.interfaces.concurrent`), with every access inside `withLock` - the pattern
`ProfileFunctionImpl` uses for the same reason. This is easiest to miss when a merge or a port brings
in a map: a compile per target catches it, an Android-only build does not.

`Provider<T>` is deprecated: the compiler says *"Using the desugared `Provider<T>` type is
discouraged. Prefer the function syntax form `() -> T`."* Write `() -> T` in new code. Call sites are
identical - `provider()` either way - so only the type and the import change.

### An `org.json` class that moves: `kotlinx.serialization`, and what to do with its tests

A class holding an `org.json` document converts in a few mechanical steps, but three of them are
decisions rather than edits.

**Reading.** `lenientInt` / `lenientLong` / `lenientDouble` / `lenientStringOrNull` in
`app.aaps.core.utils` copy `org.json`'s coercion, so a document that carries a number as a string
(`"deviation":"5.43"`, which Nightscout data does) still reads. `json.has("k")` disappears into the
default argument: pass the field's current value, `x = json.lenientDouble("k", x)`, and an absent key
leaves the field alone exactly as the old guard did. `lenientStringOrNull` is the one that answers
`null` rather than a default, which is what a `?.let` in the old code wanted.

**Writing.** Three things come out differently, and each one needs a sentence in the code saying
which was chosen:

- a null value - `org.json.put(k, null)` **deletes the key**, kotlinx writes a literal `null`, so a
  nullable field becomes `x?.let { put("k", it) }` if the file is read back;
- a whole-numbered `Double` - `org.json` wrote `100`, kotlinx writes `100.0`;
- `/` - `org.json` escaped it as `\/` and the old code undid that with a `replace`.

`JsonObjectBuilder` has no `putAll`: copy the entries with `forEach { (k, v) -> put(k, v) }`.

**The tests are the real work, and they do not have to be rewritten.** A suite written against
`JSONObject` gets a two-line bridge in its own source set - the fixture inputs parsed into kotlinx
documents, and the results handed back to the Android getters:

    fun jsonObjectOf(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
    fun JsonObject.asOrgJson(): JSONObject = JSONObject(toString())

...then `JSONObject(prepJson)` becomes `jsonObjectOf(prepJson)`, `datum.toJSON()` becomes
`datum.toJSON().asOrgJson()`, and a test that builds its input turns `JSONObject().apply { put(...) }`
into `buildJsonObject { put(...) }` with no other change. Reading a result back through its text is
also what the app does with the file it writes, so nothing is hidden by the hop. `:plugins:aps`
autotune kept **all 89 of its tests green** this way in about 80 changed lines; rewriting them into
kotlinx by hand would have been a 1800-line edit with no new coverage.

That is also why those tests stay in `androidHostTest`: a suite built on `org.json` and Mockito is a
rewrite, not a move. Only move tests to `commonTest` when they are already platform-free.

### Lift the platform call out, keep the rule

When a class is blocked by one platform call, put that call behind an interface in commonMain and
implement it in androidMain, rather than leaving the whole class on Android. `PairedBtDevices` and
`LastKnownLocation` in `:plugins:automation` are the pattern: the trigger keeps its inputs,
serialization and matching logic in shared code, and only the Bluetooth or location call is
platform-specific. Implement the Android side straight away; other platforms can follow later.

A port must express *intent*, not steps. If the caller is coordinating platform timing on the
implementation.s behalf, the port is drawn in the wrong place. `LocationServiceController` was
`startService(): Boolean` / `stopService()`, and `AutomationRuntime` wrapped it in a
`DeferredForegroundStart` (Android 12 blocks `startForegroundService` from the background) plus its
own latch to retry after a location permission grant - two Android rules living in the rule engine.
Collapsing it to one idempotent `setLocationUpdatesEnabled(enabled)`, with the deferral and the latch
inside the Android implementation, removed the last non-UI blocker from the class. The test for a
suspicious port: ask whether iOS would need the same dance. If not, it belongs on the other side.

A related tell is an event that carries a platform type it does not need. `EventLocationChange` held
an `android.location.Location` but only ever fed a debug log - the distance a trigger compares comes
from `LastKnownLocation.distanceTo`, set separately. Check what a payload actually decides before
assuming it has to stay.

Two cautions. Keep the platform maths on the platform where an exact result matters -
`LastKnownLocation.distanceTo` still calls `Location.distanceTo`, so no distance changes. And a port
whose implementation on some target would be a silent no-op is a safety problem in this app: a rule
the user relies on would quietly stop firing, so the feature should be visibly absent on that target
instead.

### Test libraries are JVM-only, so a fixtures module barely moves

JUnit 5, Mockito and RxJava have no Kotlin/Native artifacts. Anything built on them is Android by
nature, not by accident, and no amount of work moves it. In `:shared:tests` that left exactly one
file in commonMain out of eleven:

| stays on Android | why |
|---|---|
| `TestBase`, `TestBaseWithProfile` | `@ExtendWith(MockitoExtension)`, JUnit 5 lifecycle |
| `TestAapsSchedulers` | RxJava |
| `TextRefStubs` | the generated `*StringIds` maps only exist in androidMain |
| `TestPumpPlugin` | `ResourceHelper` is androidMain; `PumpEnactResultObject` is in `:implementation` |
| `HardLimitsMock` | `HardLimits` still has abstract `Int` (resource id) overloads |
| `BundleMock`, `SharedPreferencesMock` | Android types are the point of them |
| `MemberInjectorCoverage`, `SplitBrainCoverage` | `JarFile` reflection over compiled output |

Flip such a module for the module type and the processor removal, not for the sharing. Say so up
front rather than discovering it file by file.

**A fixtures module must be excluded from `checkMigratedModules`.** It declares `iosArm64()` so that
common tests can use it, but `migratedModules` feeds the exported framework header, and test helpers
do not belong in the API Swift sees. There is a `filterNot` in `ios/shell/build.gradle.kts` for this.

## `:ios:shell:checkMigratedModules` will fail next

Once a module builds for iOS, the ios-branch guard fails until it is listed. It names the module and
what to do: add it to `migratedModules` in `ios/shell/build.gradle.kts` and bump
`ShellInfo.LINKED_MODULES`. Expect this on every flip.

## Do not run a javax-stripping sweep over `:app`'s DI files

`AppRootGraph`, `MetroGraphs`, `AapsLeaves` and `CoreObjectsModule` legitimately import
`javax.inject.Singleton` and `javax.inject.Inject`. A helper that strips javax while adding an import
breaks them with `Unresolved reference 'Singleton'`. Edit those by hand.
