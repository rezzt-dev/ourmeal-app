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
  mainClass.set("ourmeal.prices.MainKt")
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.gson)
  implementation(libs.okhttp)
  implementation(libs.playwright)
  testImplementation(libs.junit)
  testImplementation(libs.okhttp.mockwebserver)
}

tasks.register<JavaExec>("installBrowser") {
  classpath = sourceSets.main.get().runtimeClasspath
  mainClass.set("com.microsoft.playwright.CLI")
  args("install", "chromium")
  environment("PLAYWRIGHT_SKIP_BROWSER_GC", "1")
}

tasks.named<JavaExec>("run") {
  standardInput = System.`in`
}

tasks.test {
  exclude("**/BrowserReaderTest.class")
}

tasks.register<Test>("browserTest") {
  description = "Runs the reader against disposable browser pages, without supermarket traffic."
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  include("**/BrowserReaderTest.class")
}
