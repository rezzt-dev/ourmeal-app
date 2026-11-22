plugins {
  alias(libs.plugins.kotlin.jvm)
  application
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = "17"
}
application {
  mainClass.set("ourmeal.backend.MainKt")
}
dependencies {
  implementation(project(":shared"))
  implementation(project(":price-reader"))
  implementation(libs.gson)
  implementation(libs.okhttp)
  implementation(platform(libs.netty.bom))
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.rate.limit)
  implementation(libs.ktor.serialization.gson)
  implementation(libs.postgresql)
  implementation(libs.hikari)
  runtimeOnly(libs.slf4j.simple)
  testImplementation(libs.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
}
tasks.test {
  exclude("**/BackendIntegrationTest.class")
}
tasks.register<Test>("integrationTest") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  include("**/BackendIntegrationTest.class")
}

tasks.register<JavaExec>("serveTestFixtures") {
  description = "Runs a local disposable API and database with explicitly synthetic test data."
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("ourmeal.backend.ServeFixturesKt")
}
