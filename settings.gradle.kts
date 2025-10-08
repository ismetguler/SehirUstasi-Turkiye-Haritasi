pluginManagement {
    repositories {
        google() // Google'ın Maven deposu (Android Gradle Plugin, Firebase ve diğer Google eklentileri için)
        gradlePluginPortal() // Gradle eklenti portalı
        mavenCentral() // Maven Central deposu
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // Google'ın Maven deposu (AndroidX, Firebase ve diğer Google kütüphaneleri için)
        mavenCentral() // Maven Central deposu
    }
}
rootProject.name = "SehirTahminOyunu" // Proje adınız
include(":app")
