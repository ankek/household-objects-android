import dev.hho.android.buildsrc.ClientVersion
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.openapi.generator)
}

val hhoClientVersion = ClientVersion.validate("0.1.2")

val releaseKeystore = providers.environmentVariable("HHO_KEYSTORE_FILE")
    .orElse(providers.gradleProperty("hho.keystore.file"))
    .orNull
    ?.let(::file)
    ?.takeIf { it.isFile }

android {
    namespace = "dev.hho.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.hho.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = hhoClientVersion

        buildConfigField("String", "HHO_CLIENT_VERSION", "\"$hhoClientVersion\"")

        testInstrumentationRunner = "dev.hho.android.HhoHiltTestRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("gms") {
            dimension = "distribution"
        }
        create("foss") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = providers.environmentVariable("HHO_KEYSTORE_PASSWORD")
                    .orElse(providers.gradleProperty("hho.keystore.password")).orNull
                keyAlias = providers.environmentVariable("HHO_KEY_ALIAS")
                    .orElse(providers.gradleProperty("hho.key.alias")).orNull
                keyPassword = providers.environmentVariable("HHO_KEY_PASSWORD")
                    .orElse(providers.gradleProperty("hho.key.password")).orNull
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        val java = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        sourceCompatibility = java
        targetCompatibility = java
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs(
                    "--add-opens=java.base/java.lang=ALL-UNNAMED",
                    "--add-opens=java.base/java.util=ALL-UNNAMED",
                    "--add-opens=java.base/java.io=ALL-UNNAMED",
                    "--add-opens=java.base/java.security=ALL-UNNAMED",
                    "--add-opens=java.base/java.net=ALL-UNNAMED",
                    "--add-opens=java.base/java.text=ALL-UNNAMED",
                    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
                    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
                    "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
                    "--add-opens=java.base/sun.security.x509=ALL-UNNAMED",
                    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                )
            }
        }
    }

    sourceSets {
        getByName("main").kotlin.srcDir("src/main/kotlin")
        getByName("test").kotlin.srcDir("src/test/kotlin")
        getByName("debug").assets.srcDir("schemas")
        getByName("androidTest").kotlin.srcDir("src/androidTest/kotlin")
        getByName("gms").kotlin.srcDir("src/gms/kotlin")
        getByName("foss").kotlin.srcDir("src/foss/kotlin")
        getByName("testGms").kotlin.srcDir("src/testGms/kotlin")
        getByName("testFoss").kotlin.srcDir("src/testFoss/kotlin")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.appcompat)

    "gmsImplementation"(libs.mlkit.barcode.scanning)

    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)

    kspTest(libs.dagger.compiler)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)

    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}

val mergedDebugManifest =
    layout.buildDirectory.file("intermediates/merged_manifest/fossDebug/processFossDebugMainManifest/AndroidManifest.xml")

val checkNoWorkManagerDefaultInitializer = tasks.register("checkNoWorkManagerDefaultInitializer") {
    group = "verification"
    description = "T158e: fails if the merged debug manifest still registers WorkManager's " +
        "default androidx.startup initializer (which would bypass HiltWorkerFactory)."

    dependsOn("processFossDebugMainManifest")
    val manifestProvider = mergedDebugManifest
    inputs.file(manifestProvider)

    doLast {
        val manifest = manifestProvider.get().asFile
        check(manifest.isFile) {
            "checkNoWorkManagerDefaultInitializer: merged manifest not found at ${manifest.path} " +
                "— AGP's output path changed; update this task rather than letting it pass vacuously."
        }
        val androidNs = "http://schemas.android.com/apk/res/android"
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(manifest)
        val metaData = doc.getElementsByTagName("meta-data")
        val registered = (0 until metaData.length)
            .map { (metaData.item(it) as org.w3c.dom.Element).getAttributeNS(androidNs, "name") }
        if ("androidx.work.WorkManagerInitializer" in registered) {
            throw GradleException(
                "Merged manifest (${manifest.path}) still contains androidx.work.WorkManagerInitializer: " +
                    "WorkManager would be initialised with its default WorkerFactory at process start, " +
                    "bypassing HhoApplication's HiltWorkerFactory, so SyncWorker could never be created. " +
                    "Restore the tools:node=\"remove\" meta-data entry in src/main/AndroidManifest.xml.",
            )
        }
    }
}

tasks.configureEach {
    if (name == "testGmsDebugUnitTest" || name == "testFossDebugUnitTest" || name == "check") {
        dependsOn(checkNoWorkManagerDefaultInitializer)
    }
}

ksp {
    arg("room.schemaLocation", layout.projectDirectory.dir("schemas").asFile.path)
    arg("room.generateKotlin", "true")
}

val apiClientPackagePath = "dev/hho/android/data/apiclient/generated"

val openApiGenerateKotlinClient = tasks.register<GenerateTask>("openApiGenerateKotlinClient") {
    group = "hho openapi"
    description = "Generates the Kotlin API client from server/api/openapi.yaml into a scratch " +
        "build directory (D3.3, T157a). Run `generateApiClient` instead to update the committed " +
        "sources under data/apiclient/generated/."

    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(rootProject.layout.projectDirectory.file("../server/api/openapi.yaml").asFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    packageName.set("dev.hho.android.data.apiclient.generated")

    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "java8",
            "legacyDiscriminatorBehavior" to "false",
            "enumPropertyNaming" to "PascalCase",
        ),
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false",
            "apiDocs" to "false",
            "modelDocs" to "false",
        ),
    )
}

val apiClientGeneratedDir = layout.projectDirectory.dir("src/main/kotlin/$apiClientPackagePath")
val apiClientHeader = """
    |// SPDX-License-Identifier: AGPL-3.0-only
    |// Generated by OpenAPI Generator from server/api/openapi.yaml — DO NOT EDIT BY HAND.
    |// Regenerate via `./gradlew :app:generateApiClient` (D3.3, T157a/T157b).

""".trimMargin()

tasks.register<Sync>("generateApiClient") {
    group = "hho openapi"
    description = "Regenerates the committed Kotlin API client under " +
        "data/apiclient/generated/ from server/api/openapi.yaml (D3.3, T157a/T157b)."

    dependsOn(openApiGenerateKotlinClient)
    from(layout.buildDirectory.dir("generated/openapi/src/main/kotlin/$apiClientPackagePath"))
    into(apiClientGeneratedDir)

    doLast {
        apiClientGeneratedDir.asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val text = file.readText()
            if (!text.startsWith(apiClientHeader)) {
                file.writeText(apiClientHeader + text)
            }
        }
    }
}

val apiClientDriftStageDir = layout.buildDirectory.dir("generated/openapi-drift-check")

val stageApiClientForDriftCheck = tasks.register<Sync>("stageApiClientForDriftCheck") {
    group = "hho openapi"
    description = "Regenerates the Kotlin API client into a scratch directory (never the " +
        "committed src/ tree) for checkApiClientDrift to compare against (D3.3, T157d)."

    dependsOn(openApiGenerateKotlinClient)
    from(layout.buildDirectory.dir("generated/openapi/src/main/kotlin/$apiClientPackagePath"))
    into(apiClientDriftStageDir)

    doLast {
        apiClientDriftStageDir.get().asFileTree.matching { include("**/*.kt") }.forEach { file ->
            val text = file.readText()
            if (!text.startsWith(apiClientHeader)) {
                file.writeText(apiClientHeader + text)
            }
        }
    }
}

tasks.register("checkApiClientDrift") {
    group = "hho openapi"
    description = "CI drift gate (D3.3, T157d, NFR-032): fails if the committed Kotlin API " +
        "client under data/apiclient/generated/ differs, byte-for-byte, from what " +
        "server/api/openapi.yaml generates today."

    dependsOn(stageApiClientForDriftCheck)

    doLast {
        val committedDir = apiClientGeneratedDir.asFile
        val stagedDir = apiClientDriftStageDir.get().asFile

        fun relativeFiles(root: File) = root.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root).path }.toSortedSet()

        val committedFiles = relativeFiles(committedDir)
        val stagedFiles = relativeFiles(stagedDir)
        val staleInCommitted = (committedFiles - stagedFiles).toSortedSet()
        val missingFromCommitted = (stagedFiles - committedFiles).toSortedSet()
        val changed = (committedFiles intersect stagedFiles).filterTo(sortedSetOf()) { rel ->
            !committedDir.resolve(rel).readBytes().contentEquals(stagedDir.resolve(rel).readBytes())
        }

        if (staleInCommitted.isEmpty() && missingFromCommitted.isEmpty() && changed.isEmpty()) {
            logger.lifecycle(
                "checkApiClientDrift: no drift — the committed API client matches " +
                    "server/api/openapi.yaml.",
            )
            return@doLast
        }

        val message = buildString {
            appendLine(
                "Generated Kotlin API client drift detected: data/apiclient/generated/ no " +
                    "longer matches what server/api/openapi.yaml generates.",
            )
            if (staleInCommitted.isNotEmpty()) {
                appendLine("  stale (committed, no longer generated): $staleInCommitted")
            }
            if (missingFromCommitted.isNotEmpty()) {
                appendLine("  missing (generated, not committed): $missingFromCommitted")
            }
            if (changed.isNotEmpty()) {
                appendLine("  changed: $changed")
            }
            append("Fix: run './gradlew :app:generateApiClient' and commit the result.")
        }
        throw GradleException(message)
    }
}

val apiClientAdditiveProbeDir = layout.buildDirectory.dir("generated/openapi-additive-probe")
val apiClientAdditiveProbeSpec = apiClientAdditiveProbeDir.map { it.file("openapi.yaml") }
val apiClientAdditiveProbeOutputDir = apiClientAdditiveProbeDir.map { it.dir("output") }

val additiveProbeOperationId = "getDiagnosticsPing"
val additiveProbePathYaml = """
    |  /diagnostics/ping:
    |    get:
    |      operationId: $additiveProbeOperationId
    |      tags: [diagnostics]
    |      summary: "T157e synthetic additive-drift-tolerance probe — never implemented server-side."
    |      responses:
    |        "200":
    |          description: OK
    |          content:
    |            application/json:
    |              schema:
    |                type: object
    |                properties:
    |                  status:
    |                    type: string
    |                required: [status]
    |
""".trimMargin()

val stageAdditiveOpenApiProbeSpec = tasks.register("stageAdditiveOpenApiProbeSpec") {
    group = "hho openapi"
    description = "T157e: writes a scratch copy of server/api/openapi.yaml with one synthetic " +
        "additive path grafted in, for checkApiClientAdditiveDriftTolerance to regenerate " +
        "against. Never modifies the real openapi.yaml."

    val realSpecFile = rootProject.layout.projectDirectory.file("../server/api/openapi.yaml").asFile
    val outSpecProvider = apiClientAdditiveProbeSpec
    inputs.file(realSpecFile)
    outputs.file(outSpecProvider)

    doLast {
        val original = realSpecFile.readText()
        check("/diagnostics/ping" !in original) {
            "T157e's synthetic probe path '/diagnostics/ping' already exists in the real " +
                "server/api/openapi.yaml — pick a different placeholder path for the probe, " +
                "this check needs a path guaranteed not to be there yet."
        }

        val marker = "\npaths:\n"
        val markerIndex = original.indexOf(marker)
        check(markerIndex >= 0) {
            "T157e: could not find a top-level 'paths:' section in server/api/openapi.yaml to " +
                "graft the synthetic additive probe path onto."
        }
        val insertAt = markerIndex + marker.length
        val mutated = original.substring(0, insertAt) + additiveProbePathYaml +
            original.substring(insertAt)

        val outFile = outSpecProvider.get().asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(mutated)
    }
}

val openApiGenerateAdditiveProbeClient = tasks.register<GenerateTask>("openApiGenerateAdditiveProbeClient") {
    group = "hho openapi"
    description = "T157e: regenerates the Kotlin API client from the mutated scratch spec " +
        "(server/api/openapi.yaml plus one synthetic additive path) into its own scratch " +
        "output directory."

    dependsOn(stageAdditiveOpenApiProbeSpec)
    generatorName.set("kotlin")
    library.set("jvm-okhttp4")
    inputSpec.set(apiClientAdditiveProbeSpec.get().asFile.absolutePath)
    outputDir.set(apiClientAdditiveProbeOutputDir.get().asFile.path)
    packageName.set("dev.hho.android.data.apiclient.generated")

    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "dateLibrary" to "java8",
            "legacyDiscriminatorBehavior" to "false",
            "enumPropertyNaming" to "PascalCase",
        ),
    )
    globalProperties.set(
        mapOf(
            "apiTests" to "false",
            "modelTests" to "false",
            "apiDocs" to "false",
            "modelDocs" to "false",
        ),
    )
}

tasks.register("checkApiClientAdditiveDriftTolerance") {
    group = "hho openapi"
    description = "T157e / NFR-032, D3.3 cross-phase check: proves an additive openapi.yaml " +
        "path (a synthetic probe route standing in for a future real addition, e.g. D2.3's " +
        "postponed /import/homebox/* per A161) regenerates cleanly instead of being treated " +
        "as unexpected drift. Runs entirely against scratch copies of the spec and client " +
        "output — never touches the real openapi.yaml or the committed generated/ client."

    dependsOn(openApiGenerateAdditiveProbeClient)

    doLast {
        val outputDir = apiClientAdditiveProbeOutputDir.get().asFile
        check(outputDir.exists() && outputDir.isDirectory) {
            "checkApiClientAdditiveDriftTolerance: expected generator output directory at " +
                "${outputDir.path} — the additive-probe generation run did not produce one."
        }

        val generatedFiles = outputDir.walkTopDown().filter { it.isFile }.toList()
        check(generatedFiles.isNotEmpty()) {
            "checkApiClientAdditiveDriftTolerance: additive-probe generation produced an empty " +
                "output tree — regenerating against a spec with a new path must still emit files."
        }

        val newApiFile = generatedFiles.firstOrNull { it.name.equals("DiagnosticsApi.kt", ignoreCase = true) }
        check(newApiFile != null) {
            "checkApiClientAdditiveDriftTolerance: expected a generated DiagnosticsApi.kt for " +
                "the synthetic /diagnostics/ping probe path (new tag => new file is how this " +
                "check tells 'the new path was picked up' from 'generation silently ignored " +
                "it'); got: ${generatedFiles.map { it.name }.sorted()}"
        }
        check(newApiFile.readText().contains(additiveProbeOperationId)) {
            "checkApiClientAdditiveDriftTolerance: generated DiagnosticsApi.kt does not " +
                "contain the probe operation '$additiveProbeOperationId'."
        }

        logger.lifecycle(
            "checkApiClientAdditiveDriftTolerance: PASS — an additive openapi.yaml path " +
                "regenerates cleanly (new file: ${newApiFile.relativeTo(outputDir)}), " +
                "confirming the drift check above would pick it up rather than reject it.",
        )
    }
}
