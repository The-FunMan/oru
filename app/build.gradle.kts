import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import kotlin.random.Random

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.devtools)
    alias(libs.plugins.about.libraries)
    alias(libs.plugins.about.libraries.android)
    signing
}

// Firebase reads app/google-services.json, which only Morphe's build has. Without the file the
// plugin is not applied, so the project still configures. Oru never processes the file either
// (see the processOru*GoogleServices tasks at the end of this script).
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.ktx)
    implementation(libs.runtime.ktx)
    implementation(libs.runtime.compose)
    implementation(libs.splash.screen)
    implementation(libs.activity.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.preferences.datastore)
    implementation(libs.appcompat)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.navigation.compose)

    // Coil (async image loading, network image)
    implementation(libs.coil.compose)
    implementation(libs.coil.appiconloader)
    implementation(libs.appiconloader.iconloaderlib)

    // KotlinX
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collection.immutable)
    implementation(libs.kotlinx.datetime)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Morphe
    implementation(libs.arsclib)
    implementation(libs.morphe.patcher)
    implementation(libs.morphe.library)

    implementation(libs.androidx.documentfile)

    // Native processes
    implementation(libs.kotlin.process)

    // HiddenAPI
    compileOnly(libs.hidden.api.stub)
    implementation(libs.hidden.api.bypass)

    // Shizuku / Sui
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    // LibSU
    implementation(libs.libsu.core)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.workmanager)

    // Licenses
    implementation(libs.about.libraries.core)
    implementation(libs.about.libraries.m3)

    // Ktor
    implementation(libs.ktor.core)
    implementation(libs.ktor.okhttp)
    implementation(libs.ktor.content.negotiation)
    implementation(libs.ktor.serialization)

    // Firebase Cloud Messaging
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.play.services.base)

    // Fading Edges
    implementation(libs.fading.edges)

    // Reorderable lists
    implementation(libs.reorderable)

    // Compose Icons
    implementation(libs.compose.icons.fontawesome)

    // Semantic versioning parser
    implementation(libs.semver.parser)

    // Unit tests
    testImplementation(libs.kotlin.test.junit)
}

/**
 * Languages Morphe is translated into as language and region pairs, read from the resource folders
 * Crowdin writes, so a new language needs no change anywhere else.
 */
val translations = project.file("src/main/res").listFiles().orEmpty()
    .mapNotNull { Regex("values-([a-z]{2,3})(?:-r([A-Z]{2}))?").matchEntire(it.name) }
    .map { it.groupValues[1] to it.groupValues[2] }
    .sortedWith(compareBy({ it.first }, { it.second }))

/**
 * Locales kept from library resources. Each comes with and without the region, since libraries
 * mostly use the bare one.
 */
val translatedLocales = translations
    .flatMap { (language, region) ->
        // Filipino is still filed under its legacy Tagalog code by some libraries
        listOfNotNull(
            language,
            "$language-r$region".takeIf { region.isNotEmpty() },
            "tl".takeIf { language == "fil" }
        )
    }
    .plus("en")
    .toSet()

/**
 * Translations as BCP 47 tags for the in-app language picker, written as a Java array literal for
 * BuildConfig. Resource folders still name Indonesian, Hebrew and Yiddish by their withdrawn ISO
 * codes, which tags no longer accept.
 */
val translationTags = translations.joinToString(prefix = "{", postfix = "}") { (language, region) ->
    val tagLanguage = mapOf("in" to "id", "iw" to "he", "ji" to "yi")[language] ?: language
    val tag = listOf(tagLanguage, region).filter { it.isNotEmpty() }.joinToString("-")
    "\"$tag\""
}

android {
    namespace = "app.morphe.manager"
    compileSdk = 37
    ndkVersion = "28.2.13676358"   // the version .github/actions/setup-oru installs

    defaultConfig {
        applicationId = "app.morphe.manager"
        minSdk = 26

        versionName = version.toString()

        // Deterministic versionCode: major*100000000 + minor*1000000 + patch*1000 + s
        // where s is the dev number for X.Y.Z-dev.N (1..998) and 999 for stable.
        // The ranges below keep the encoding unique and inside Int (the largest code is 2099999999).
        val versionRegex = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-dev\.(\d+))?$""")
        val versionMatch = versionRegex.matchEntire(versionName.orEmpty())
            ?: error("Invalid version format in app/gradle.properties: $versionName")
        val vMajor = versionMatch.groupValues[1].toInt()
        val vMinor = versionMatch.groupValues[2].toInt()
        val vPatch = versionMatch.groupValues[3].toInt()
        val vDev = if (versionMatch.groupValues[4].isNotEmpty()) versionMatch.groupValues[4].toInt() else null

        require(vMajor in 0..20) {
            "vMajor must be in 0..20: a larger major lets the Int versionCode overflow (found $vMajor)"
        }
        require(vMinor in 0..99) {
            "vMinor must be in 0..99: a larger minor runs into the major digit (found $vMinor)"
        }
        require(vPatch in 0..999) { "vPatch must be in 0..999 (found $vPatch)" }
        if (vDev != null) {
            require(vDev in 1..998) { "vDev must be in 1..998 (found $vDev)" }
        }

        val vS = vDev ?: 999
        val vCode = vMajor * 100_000_000L + vMinor * 1_000_000L + vPatch * 1_000L + vS
        require(vCode <= Int.MAX_VALUE) { "versionCode $vCode for $versionName does not fit an Int" }
        versionCode = vCode.toInt()

        // Expose the resolved morphe-patcher version so PatcherViewModel can compare it
        // against the Patcher-Version declared in .mpp bundle manifests at runtime.
        buildConfigField("String", "PATCHER_VERSION", "\"${libs.versions.morphe.patcher.get()}\"")

        buildConfigField("String[]", "TRANSLATIONS", translationTags)

        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("long", "BUILD_ID", "${Random.nextLong()}L")
        }

        release {
            if (!project.hasProperty("noProguard")) {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            }

            val keystoreFile = file("keystore.jks")

            signingConfig = if (project.hasProperty("signAsDebug") || !keystoreFile.exists()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.create("release") {
                    storeFile = keystoreFile
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEYSTORE_ENTRY_ALIAS")
                    keyPassword = System.getenv("KEYSTORE_ENTRY_PASSWORD")
                }
            }

            buildConfigField("long", "BUILD_ID", "0L")
        }
    }

    // The morphe flavor is upstream's build. The oru flavor takes its own package and resources from
    // src/oru, and turns off the services that belong to Morphe's Firebase project and update feed.
    flavorDimensions += "brand"
    productFlavors {
        create("morphe") {
            dimension = "brand"
            isDefault = true
            buildConfigField("boolean", "UPSTREAM_SERVICES", "true")
            buildConfigField("boolean", "ORU_UX", "false")
        }
        create("oru") {
            dimension = "brand"
            applicationId = "app.oru.manager"
            buildConfigField("boolean", "UPSTREAM_SERVICES", "false")
            buildConfigField("boolean", "ORU_UX", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Unit tests run on the JVM, where android.util.Log is only a stub
    testOptions.unitTests.isReturnDefaultValues = true

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources.excludes.addAll(
            listOf(
                // Build junk
                "/prebuilt/**",
                "/smali.properties",
                "/baksmali.properties",
                "/properties/apktool.properties",

                // Kotlin / debug metadata
                "/META-INF/*.version",
                "/META-INF/*.kotlin_module",
                "/kotlin-tooling-metadata.json",
                "/DebugProbesKt.bin",

                // Specific META-INF junk
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                // Bouncy Castle 1.86 ships the same licence file in three jars, and the licences are listed in About
                "/META-INF/LICENSE.md",

                // Crypto optional metadata
                "/org/bouncycastle/pqc/**.properties",
                "/org/bouncycastle/x509/**.properties",

                // ANTLR tool templates and grammar tokens; smali only needs the runtime at patch time
                "/org/antlr/codegen/**",
                "/org/antlr/tool/**",
                "/com/android/tools/smali/smali/*.tokens",

                // Mocking agent resources whose classes R8 already strips
                "/org/mockito/**",
                "/win32-x86/**",
                "/win32-x86-64/**"
            )
        )

        jniLibs {
            useLegacyPackaging = true
        }
    }

    androidResources {
        // Libraries ship strings in far more languages than Morphe has, which only bloat resources.arsc
        @Suppress("UnstableApiUsage")
        localeFilters += translatedLocales

        // Lists the translations for the per-app language setting of Android 13+ from the same
        // resource folders, with the default locale taken from res/resources.properties
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        aidl = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    lint {
        disable += setOf("MissingTranslation")
        // Crowdin drops a removed string from the translations only on its next sync
        warning += setOf("ExtraTranslation")
        baseline = file("lint-baseline.xml")
    }
}

// APK output file name
base.archivesName.set(provider {
    "${rootProject.name}-$version"
})

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime"
        )
    }
}

/**
 * A Rust crate in the repository, built with cargo for each ABI the app ships and packaged as lib[libraryName].so.
 * The patcher loads it when it is there and falls back to the JVM otherwise, so a missing cargo only warns.
 * Pages are aligned to 16 KB, which Android 15 and later require.
 */
abstract class BuildNativeCrate : DefaultTask() {
    @get:InputFiles
    abstract val sources: ConfigurableFileCollection

    @get:InputFile
    abstract val manifest: RegularFileProperty

    @get:InputFile
    abstract val lockfile: RegularFileProperty

    @get:Input
    abstract val ndkDirectory: Property<String>

    @get:Input
    abstract val minSdk: Property<Int>

    @get:Input
    abstract val libraryName: Property<String>

    /** The workspace member to build with `-p`, or null for the package in [crateDirectory]. */
    @get:Optional
    @get:Input
    abstract val cargoPackage: Property<String>

    @get:Internal
    abstract val crateDirectory: DirectoryProperty

    @get:Internal
    abstract val cargoTargetDirectory: DirectoryProperty

    @get:Internal
    abstract val repositoryRoot: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibs: DirectoryProperty

    @TaskAction
    fun build() {
        val output = jniLibs.get().asFile.apply { deleteRecursively() }
        val cargoFound = try {
            ProcessBuilder("cargo", "--version").redirectErrorStream(true).start().waitFor() == 0
        } catch (_: IOException) {
            false
        }
        if (!cargoFound) {
            logger.warn("cargo is not installed: lib${libraryName.get()}.so is not packaged")
            return
        }

        val windows = System.getProperty("os.name").startsWith("Windows")
        val host = when {
            windows -> "windows-x86_64"
            System.getProperty("os.name").startsWith("Mac") -> "darwin-x86_64"
            else -> "linux-x86_64"
        }
        val bin = File(ndkDirectory.get(), "toolchains/llvm/prebuilt/$host/bin")
        val crate = crateDirectory.get().asFile
        val targetDir = cargoTargetDirectory.get().asFile

        // Cargo ranks CARGO_TARGET_*_RUSTFLAGS below RUSTFLAGS and splits it on spaces: pass every flag encoded.
        // The remapped prefixes keep the builder's home, cargo and rustup paths out of the binaries.
        val sysroot = ProcessBuilder("rustc", "--print", "sysroot").directory(crate).start()
            .inputStream.bufferedReader().use { it.readText().trim() }
        val inherited = System.getenv("CARGO_ENCODED_RUSTFLAGS")?.takeIf { it.isNotEmpty() }?.split('\u001f')
            ?: System.getenv("RUSTFLAGS")?.split(' ')?.filter { it.isNotEmpty() }.orEmpty()
        val remap = listOf(
            repositoryRoot.get().asFile.absolutePath to "/oru",
            (System.getenv("CARGO_HOME") ?: File(System.getProperty("user.home"), ".cargo").absolutePath) to "/cargo",
            sysroot to "/rustc",
        ).map { (from, to) -> "--remap-path-prefix=$from=$to" }
        val rustflags = (inherited + listOf("-C", "link-arg=-Wl,-z,max-page-size=16384") + remap).joinToString("\u001f")

        // ABI, Rust target and the clang target prefix of the NDK wrappers
        listOf(
            Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android"),
            Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi"),
            Triple("x86", "i686-linux-android", "i686-linux-android"),
            Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android"),
        ).forEach { (abi, rustTarget, clangTarget) ->
            val linker = bin.resolve("$clangTarget${minSdk.get()}-clang${if (windows) ".cmd" else ""}")
            val ar = bin.resolve("llvm-ar${if (windows) ".exe" else ""}")
            val command = mutableListOf(
                "cargo", "build", "--release", "--lib",
                "--target", rustTarget,
                "--target-dir", targetDir.absolutePath,
            )
            cargoPackage.orNull?.let { command += listOf("-p", it) }
            val build = ProcessBuilder(command).directory(crate).inheritIO()
            val env = rustTarget.uppercase().replace('-', '_')
            build.environment().apply {
                put("CARGO_TARGET_${env}_LINKER", linker.absolutePath)
                put("CARGO_TARGET_${env}_AR", ar.absolutePath)
                put("CARGO_ENCODED_RUSTFLAGS", rustflags)
            }
            if (build.start().waitFor() != 0) throw GradleException("cargo failed for $rustTarget")

            output.resolve(abi).mkdirs()
            val library = "lib${libraryName.get()}.so"
            targetDir.resolve("$rustTarget/release/$library").copyTo(output.resolve("$abi/$library"), overwrite = true)
        }
    }
}

val buildNativeDexIndex = tasks.register<BuildNativeCrate>("buildNativeDexIndex") {
    group = "build"
    description = "Builds the native dex index in native/ for the ABIs the app ships"

    val crate = rootProject.layout.projectDirectory.dir("native")
    sources.from(crate.dir("src"))
    manifest.set(crate.file("Cargo.toml"))
    lockfile.set(crate.file("Cargo.lock"))
    crateDirectory.set(crate)
    repositoryRoot.set(rootProject.layout.projectDirectory)
    libraryName.set("oru_patcher")
    cargoTargetDirectory.set(layout.buildDirectory.dir("nativeDexIndex/cargo"))
    jniLibs.set(layout.buildDirectory.dir("generated/nativeDexIndex/jniLibs"))
    ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
    minSdk.set(android.defaultConfig.minSdk ?: 29)
}

val buildNativePackager = tasks.register<BuildNativeCrate>("buildNativePackager") {
    group = "build"
    description = "Builds the native APK packager and its JNI wrapper in native-packager/ for the ABIs the app ships"

    // The workspace root holds the lockfile for both the packager and its JNI crate.
    val workspace = rootProject.layout.projectDirectory.dir("native-packager")
    sources.from(workspace.dir("src"), workspace.dir("jni/src"))
    manifest.set(workspace.file("jni/Cargo.toml"))
    lockfile.set(workspace.file("Cargo.lock"))
    crateDirectory.set(workspace)
    repositoryRoot.set(rootProject.layout.projectDirectory)
    cargoPackage.set("oru-packager-jni")
    libraryName.set("oru_packager")
    cargoTargetDirectory.set(layout.buildDirectory.dir("nativePackager/cargo"))
    jniLibs.set(layout.buildDirectory.dir("generated/nativePackager/jniLibs"))
    ndkDirectory.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
    minSdk.set(android.defaultConfig.minSdk ?: 29)
}

androidComponents.onVariants { variant ->
    variant.sources.jniLibs?.addGeneratedSourceDirectory(buildNativeDexIndex) { it.jniLibs }
    variant.sources.jniLibs?.addGeneratedSourceDirectory(buildNativePackager) { it.jniLibs }
}

aboutLibraries {
    collect {
        configPath = file("aboutlibraries")
    }
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.EXACT
    }
}

tasks {
    whenTaskAdded {
        if (name.startsWith("lintVital")) {
            enabled = false
        }
    }
}

// google-services.json only lists Morphe's package names, so the oru variant has no Firebase config to process
tasks.matching { it.name.startsWith("process") && it.name.contains("Oru") && it.name.endsWith("GoogleServices") }
    .configureEach { enabled = false }

// Release signing falls back to the public debug key when keystore.jks is absent. Refuse that for release tasks in
// the graph; -PsignAsDebug is the explicit opt-in. Checked on the graph so configuration (IDE sync) still works.
val releaseTaskName = Regex("^(assemble|bundle|package|install)\\w*Release\\w*$")
val appProject = project
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any { it.project == appProject && releaseTaskName.matches(it.name) }
    if (releaseRequested && !appProject.hasProperty("signAsDebug") && !appProject.file("keystore.jks").exists()) {
        throw GradleException("Release build needs app/keystore.jks. Add it, or pass -PsignAsDebug to sign with the debug key.")
    }
}

/**
 * Launcher icons on offer, the one place they are defined. Each is a background with a mark over it.
 * The launcher only shows icons the manifest declares, so the build generates from this an
 * activity-alias per icon, the adaptive icon it shows and the catalog the app picks from.
 *
 * Backgrounds are listed in picker order. A background's drawable is ic_launcher_background_<id>
 * and its name settings_appearance_app_icon_<id>; a mark's name is
 * settings_appearance_app_icon_mark_<id>.
 *
 * The launcher remembers an icon by its alias, MainActivity_<Background>, plus _<Mark> for any mark
 * but the background's base one. Released ids and base marks must therefore never change, or users
 * of that icon would lose it on update. A background's color can change freely.
 */
val launcherIconMarks = linkedMapOf(
    "color" to "ic_launcher_foreground",
    "white" to "ic_launcher_foreground_white",
    "black" to "ic_launcher_foreground_black"
)

val launcherIconBackgrounds = listOf(
    // id to (base mark, other marks that read on it). A mark reads when it keeps a contrast of
    // about 2.5:1 against the background, the brand mark about 2:1 at its weaker end
    "default" to ("color" to listOf("black")),
    "light_2" to ("color" to listOf("black")),
    "light_3" to ("color" to listOf("black")),
    "light_4" to ("color" to listOf("black")),
    "light_5" to ("color" to listOf("black")),
    "light_6" to ("color" to listOf("black")),
    "light_7" to ("color" to listOf("black")),
    "dark_1" to ("white" to listOf("black")),
    "accent_1" to ("white" to listOf("black")),
    "accent_4" to ("white" to listOf("black")),
    "dark_3" to ("white" to listOf("black")),
    "dark_4" to ("white" to emptyList()),
    "dark_5" to ("white" to listOf("black")),
    "dark_2" to ("color" to listOf("white")),
    "accent_2" to ("white" to listOf("color")),
    "accent_3" to ("black" to listOf("color"))
)

val generateLauncherIcons = tasks.register<GenerateLauncherIconsTask>("generateLauncherIcons") {
    group = "build"
    description = "Generates the launcher icon aliases, adaptive icons and app catalog."
    marks.set(launcherIconMarks)
    backgrounds.set(launcherIconBackgrounds.map { (id, marks) -> listOf(id, marks.first) + marks.second })
    resDirectory.set(layout.buildDirectory.dir("generated/launcherIcons/res"))
    kotlinDirectory.set(layout.buildDirectory.dir("generated/launcherIcons/kotlin"))
    manifestFile.set(layout.buildDirectory.file("generated/launcherIcons/AndroidManifest.xml"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(generateLauncherIcons, GenerateLauncherIconsTask::resDirectory)
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateLauncherIcons, GenerateLauncherIconsTask::kotlinDirectory)
        variant.sources.manifests.addGeneratedManifestFile(generateLauncherIcons, GenerateLauncherIconsTask::manifestFile)
    }
}
