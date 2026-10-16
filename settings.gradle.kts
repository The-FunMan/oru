@file:Suppress("UnstableApiUsage")

import java.util.Properties

val localProps = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun githubUser(): String? =
    localProps.getProperty("gpr.user")
        ?: providers.gradleProperty("gpr.user").orNull
        ?: System.getenv("GITHUB_ACTOR")

fun githubToken(): String? =
    localProps.getProperty("gpr.key")
        ?: providers.gradleProperty("gpr.key").orNull
        ?: System.getenv("GITHUB_TOKEN")

// mavenLocal() is opt-in (-Poru.mavenLocal) and comes last, so a locally installed artifact can never shadow a
// verified remote one in release builds.
pluginManagement {
    repositories {
        mavenCentral()
        google()
        gradlePluginPortal()
        if (providers.gradleProperty("oru.mavenLocal").isPresent) mavenLocal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io") {
            metadataSources {
                mavenPom()
                artifact()
            }
        }
        maven {
            // A repository must be specified for some reason. "registry" is a dummy.
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                val gprUser: String? = githubUser()
                val gprKey: String? = githubToken()

                username = gprUser.orEmpty().ifBlank { "anonymous" }
                password = gprKey.orEmpty()
            }
        }
        if (providers.gradleProperty("oru.mavenLocal").isPresent) mavenLocal()
    }
}

rootProject.name = "morphe-manager"
include(":app")

// Include morphe-patcher and morphe-library as composite builds if they exist locally
mapOf(
    "morphe-patcher" to "app.morphe:morphe-patcher",
//    "morphe-library" to "app.morphe:morphe-library", // FIXME: Must upgrade library gradle to use this
//    "ARSCLib" to "com.github.MorpheApp:ARSCLib"
).forEach { (libraryPath, libraryName) ->
    val libDir = file("../$libraryPath")
    if (libDir.exists()) {
        includeBuild(libDir) {
            dependencySubstitution {
                substitute(module(libraryName)).using(project(":"))
            }
        }
    }
}

// The patcher lives in this repository (patcher/) and replaces the published morphe-patcher artifact.
includeBuild("patcher") {
    dependencySubstitution {
        substitute(module("app.morphe:morphe-patcher")).using(project(":"))
    }
}
