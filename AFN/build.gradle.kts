plugins {
  kotlin("jvm") version "1.8.21"
  application
}

group = "dev.koalit"
version = "1.0"


repositories {
  mavenCentral()
}

dependencies {
  implementation("guru.nidi:graphviz-java:0.18.1")
}

application {
  mainClass.set("AFNKt")
}