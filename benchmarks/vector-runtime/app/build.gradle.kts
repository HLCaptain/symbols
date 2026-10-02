plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val supportsFilled = providers.gradleProperty("vectorFilled").orElse("false").map(String::toBooleanStrict)

android {
    namespace = "io.github.hlcaptain.symbols.benchmark.vectorruntime"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        applicationId = namespace
        minSdk = 26
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    sourceSets.getByName("main").kotlin.srcDir(if (supportsFilled.get()) "src/filled/kotlin" else "src/outline/kotlin")
    buildFeatures { compose = true }
    buildTypes {
        create("benchmark") {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

androidComponents { beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" } }
kotlin { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }

dependencies {
    // The version is an explicit experiment input: no source substitution or mavenLocal.
    implementation(libs.benchmark.vectors)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)
}

tasks.register("recordVectorArtifact") {
    doLast {
        val configuration = configurations.getByName("benchmarkRuntimeClasspath")
        val artifacts = configuration.incoming.artifactView {
            componentFilter { id ->
                id is org.gradle.api.artifacts.component.ModuleComponentIdentifier && id.group == "io.github.hlcaptain"
            }
            attributes.attribute(org.gradle.api.artifacts.type.ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "aar")
        }.artifacts.artifacts
        layout.buildDirectory.file("vector-artifacts.tsv").get().asFile.writeText(
            artifacts.sortedBy { it.id.componentIdentifier.displayName }.joinToString("\n") {
                "${it.id.componentIdentifier.displayName}\t${it.file.absolutePath}"
            } + "\n",
        )
    }
}
