// Root build: declares plugin versions once (from the version catalog) without applying them.
// Each module applies what it needs.
plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

allprojects {
    group = "in.codefarm"
    version = "0.1.0-SNAPSHOT"
}
