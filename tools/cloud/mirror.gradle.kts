fun RepositoryHandler.mirrorCentral() = all {
    if (this is MavenArtifactRepository && url.host == "repo.maven.apache.org") {
        url = uri("http://127.0.0.1:8099/")
        isAllowInsecureProtocol = true
    }
}
beforeSettings { pluginManagement.repositories.mirrorCentral() }
settingsEvaluated {
    pluginManagement.repositories.mirrorCentral()
    dependencyResolutionManagement.repositories.mirrorCentral()
}
allprojects { buildscript.repositories.mirrorCentral(); repositories.mirrorCentral() }
