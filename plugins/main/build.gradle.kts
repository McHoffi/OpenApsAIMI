plugins {
    id("kmp-android-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro, so this module can wire its own Android entry points. The rewrite of this build file
    // dropped the Hilt/KSP processors but did not add this one, and without it the module's
    // `@ContributesTo` containers are never registered and nothing here can be injected.
    alias(libs.plugins.metro)
}

registerKeyStrings("main")

kotlin {
    android {
        namespace = "app.aaps.plugins.main"
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))
                // The fork's AIMI loop guard, read by IobCobCalculatorPlugin (commonMain) and by the
                // dashboard refresh gate (androidMain). It was declared for androidMain only, which
                // made the commonMain import fail on every target but Android. :plugins:aps does not
                // depend on :plugins:main, so this is not a cycle.
                implementation(project(":plugins:aps"))

                implementation(libs.androidx.collection)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jetbrains.compose.runtime)
            }
        }

        androidMain {
            dependencies {
                // The compose overview lives in :ui (GraphViewModel, BgGraphCompose and the glass
                // detail screens use it). Present in the pre-KMP build file, dropped by the rewrite.
                implementation(project(":ui"))
                // The graph series types of the dashboard and the overview, and the vendored
                // GraphView widget (com.jjoe64.graphview) they draw on. :core:graph keeps the
                // widget to itself, so it is named here too.
                implementation(project(":core:graph"))
                implementation(project(":core:graphview"))
                // RxBusImpl, used by OverviewPlugin.
                implementation(project(":shared:impl"))
                // AndroidX Fragment: the overview and dashboard fragments and their view models.
                implementation(libs.androidx.fragment)
                // GridLayout, used by the overview chart menu.
                implementation(libs.androidx.gridlayout)
                // observeAsState, for the live data the dashboard screens read.
                implementation(libs.androidx.compose.runtime.livedata)
                // Fork legacy overview layout uses FlexboxLayout
                implementation(libs.com.google.android.flexbox)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :plugins:sensitivity.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":implementation"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures. Same reason as :pump:virtual.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

