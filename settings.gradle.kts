pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

// The native Mapbox Maps SDK is served from Mapbox's private Maven repository, which requires a
// SECRET token with the DOWNLOADS:READ scope. It is a build-time credential only: it is never packaged
// into the APK. Put it in ~/.gradle/gradle.properties (MAPBOX_DOWNLOADS_TOKEN=sk....) or the environment.
// Without it the app builds with the offline demo map surface (see docs/MAPBOX.md).
val mapboxDownloadsToken: String? =
    providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").orNull
        ?: providers.environmentVariable("MAPBOX_DOWNLOADS_TOKEN").orNull

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        if (!mapboxDownloadsToken.isNullOrBlank()) {
            maven {
                url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
                credentials {
                    username = "mapbox"
                    password = mapboxDownloadsToken
                }
                authentication { create<BasicAuthentication>("basic") }
                content { includeGroupByRegex("com\\.mapbox.*") }
            }
        }
    }
}

rootProject.name = "YABAH-Android"
include(":app")
