import java.io.File
import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

val baseVersionCode = project.property("VERSION_CODE").toString().toInt()
/**
 * Source identity embedded in the APK for diagnostics.
 *
 * CI exports GITHUB_SHA; a local checkout falls back to HEAD. Both are resolved once
 * during configuration, so the same commit always produces the same value and no git
 * binary is required at runtime. "unknown" (not a timestamp, not a random id) is used
 * when neither is available.
 */
val sourceIdentity: String = (providers.environmentVariable("GITHUB_SHA").orNull
    ?: runCatching {
        providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get()
    }.getOrNull())
    ?.trim()?.takeIf { it.length >= 7 }?.take(12) ?: "unknown"
val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
val abiSplits = providers.gradleProperty("ABI_SPLITS").map(String::toBoolean).getOrElse(false)
// Only opt-in CI uses this; normal release defaults and ABI matrix are unchanged.
val abiTarget = providers.gradleProperty("ABI_TARGET").orNull?.takeIf { it.isNotBlank() }
require(abiTarget == null || (abiSplits && abiCodes.containsKey(abiTarget))) {
    "ABI_TARGET requires ABI_SPLITS=true and a supported ABI"
}

base {
    archivesName = "gallery-$baseVersionCode"
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APP_ID").toString()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = baseVersionCode
        // Diagnostics must be able to name the source of a release APK. Resolved once
        // at configuration time from CI's commit or the checkout's HEAD, so the value
        // is reproducible per commit and absent (not random) outside a git checkout.
        buildConfigField("String", "SOURCE_IDENTITY", "\"$sourceIdentity\"")
    }

    // Keep instrumentation runnable from a normal local checkout without release signing keys.
    testBuildType = "debug"

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Debug trace paths call android.util.Log; plain JVM test stubs should
        // return their defaults instead of throwing for nonessential log output.
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            // Large backup fixtures plus the native-render matrix exceed Gradle's 512 MiB test default.
            it.maxHeapSize = "2g"
            // macOS exposes /var via /private/var. Keep host temp fixtures on a
            // canonical root so symlink/security tests exercise their intended alias.
            it.systemProperty("java.io.tmpdir", File(System.getProperty("java.io.tmpdir")).canonicalPath)
            // Sandboxed/offline hosts can point Robolectric at a pre-populated
            // android-all directory instead of writing into ~/.m2.
            providers.environmentVariable("ROBOLECTRIC_DEPENDENCY_DIR").orNull?.let { dir ->
                it.systemProperty("robolectric.dependency.dir", dir)
                it.systemProperty("robolectric.offline", "true")
            }
            it.jvmArgs(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED",
                "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED"
            )
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("licensing")
    productFlavors {
        register("foss")
        register("gplay")
    }

    splits {
        abi {
            isEnable = abiSplits
            reset()
            include(*(abiTarget?.let { arrayOf(it) } ?: abiCodes.keys.toTypedArray()))
            isUniversalApk = abiTarget == null
        }
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    packaging {
        resources {
            excludes += "META-INF/library_release.kotlin_module"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

if (abiSplits) {
    androidComponents {
        onVariants(selector().withBuildType("release").withFlavor("licensing" to "foss")) { variant ->
            variant.outputs.forEach { output ->
                val abi = output.filters.find {
                    it.filterType == FilterConfiguration.FilterType.ABI
                }?.identifier
                val versionCode = baseVersionCode + (abiCodes[abi] ?: 0)
                val abiSuffix = abi?.let { "-$it" }.orEmpty()
                val unsignedSuffix =
                    if (output.outputFileName.get().endsWith("-unsigned.apk")) "-unsigned" else ""
                output.versionCode.set(versionCode)
                output.outputFileName.set("gallery-$versionCode-foss${abiSuffix}-release${unsignedSuffix}.apk")
            }
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // The offline Picasso test fixture implements Downloader, whose API uses OkHttp types.
    testImplementation(libs.okhttp)
    // Test-only Android View measurement, interaction and native Canvas rendering.
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("junit:junit:4.13.2")
    implementation(libs.fossify.commons)
    // Match Commons 7.1.3 while distinguishing retryable biometric mismatch
    // callbacks from terminal system errors.
    implementation("androidx.biometric:biometric-ktx:1.4.0-alpha02")
    implementation(libs.androidx.print)
    implementation(libs.android.image.cropper)
    implementation(libs.exif)
    implementation(libs.android.gif.drawable)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.media3.exoplayer)
    implementation("androidx.media3:media3-ui:1.10.1")
    implementation(libs.sanselan)
    implementation(libs.androidphotofilters)
    implementation(libs.androidsvg.aar)
    implementation(libs.gestureviews)
    implementation(libs.subsamplingscaleimageview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.awebp)
    implementation(libs.apng)
    implementation(libs.avif)
    implementation(libs.avif.integration)
    implementation(libs.jxl.integration)
    implementation(libs.okio)
    implementation(libs.picasso) {
        exclude(group = "com.squareup.okhttp3", module = "okhttp")
    }
    compileOnly(libs.okhttp)

    ksp(libs.glide.compiler)
    implementation(libs.zjupure.webpdecoder)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
}

// Commons owns internal/nested dialogs too. Patch only its 7.1.3 presentation boundary.
check(libs.versions.commons.get() == "7.1.3") { "Review classic Commons compatibility before upgrading" }
androidComponents {
    onVariants(selector().all()) { variant ->
        variant.instrumentation.transformClassesWith(
            org.fossify.gallery.build.ClassicCommonsVisitorFactory::class.java,
            com.android.build.api.instrumentation.InstrumentationScope.ALL
        ) {
            // Invalidate AGP's cached external artifacts when the patch changes.
            it.patchRevision.set(2)
        }
        variant.instrumentation.setAsmFramesComputationMode(
            com.android.build.api.instrumentation.FramesComputationMode.COMPUTE_FRAMES_FOR_INSTRUMENTED_METHODS
        )

        // AGP applies external dependency instrumentation for the APK, but host tests
        // load the original AAR classes. Patch just the resume class with the same visitor.
        val capitalizedVariant = variant.name.replaceFirstChar { it.uppercaseChar() }
        val commons = variant.runtimeConfiguration.incoming.artifactView {
            componentFilter { id ->
                id is org.gradle.api.artifacts.component.ModuleComponentIdentifier &&
                    id.group == "org.fossify" && id.module == "commons"
            }
            attributes {
                attribute(org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE,
                    "android-classes-jar")
            }
        }.files
        val testCommons = tasks.register<org.fossify.gallery.build.TestCommonsResumeClass>(
            "patch${capitalizedVariant}CommonsResumeForUnitTest"
        ) {
            commonsClasses.from(commons)
            outputDirectory.set(layout.buildDirectory.dir("generated/commonsResumeUnitTest/${variant.name}"))
        }
        // AGP assigns the test classpath while registering its tasks.
        gradle.projectsEvaluated {
            tasks.withType<Test>().matching { it.name == "test${capitalizedVariant}UnitTest" }.configureEach {
                classpath = files(testCommons.flatMap { it.outputDirectory }) + classpath
                dependsOn(testCommons)
            }
        }
    }
}
