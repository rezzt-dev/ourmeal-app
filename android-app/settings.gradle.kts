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
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "ourmeal"
include(":app")
include(":price-reader")
project(":price-reader").projectDir = file("../price-reader")
include(":shared", ":backend")
project(":shared").projectDir = file("../shared")
project(":backend").projectDir = file("../backend")
