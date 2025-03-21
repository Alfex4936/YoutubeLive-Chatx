plugins {
	java
	application
	id("org.springframework.boot") version "3.4.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.10.6" // GraalVM native image support
}

group = "csw"
version = "1.0.0"

application {
	mainClass.set("csw.youtube.chat.Application")
	applicationDefaultJvmArgs = listOf(
		"-Xms2G", // Set initial heap size to 2GB
		"-Xmx8G", // Set max heap size to 8GB
		"-XX:+UseZGC", // Use Z Garbage Collector
		"-XX:+ZGenerational", // Enable Generational ZGC (Java 21+)
		"-XX:TieredStopAtLevel=1", // Reduce JIT compilation overhead
		"--enable-preview" // Enable Java preview features
	)
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(23)
	}

	sourceCompatibility = JavaVersion.VERSION_23
	targetCompatibility = JavaVersion.VERSION_23
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
//	implementation("org.springframework.boot:spring-boot-starter-data-redis")

	// https://mvnrepository.com/artifact/org.redisson/redisson-spring-boot-starter
	implementation("org.redisson:redisson-spring-boot-starter:3.45.0")

	implementation("com.fasterxml.jackson.module","jackson-module-blackbird","2.18.2")
	implementation("com.microsoft.playwright", "playwright", "1.50.0")
	implementation("com.google.guava:guava:33.4.0-jre")
	implementation("org.apache.commons:commons-pool2:2.12.1")
	implementation("com.github.pemistahl:lingua:1.2.2")
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.3")
	// https://mvnrepository.com/artifact/org.jfree/jfreechart
	implementation("org.jfree:jfreechart:1.5.5")

	implementation("com.nimbusds:nimbus-jose-jwt:10.0.2")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:3.4.3")
	implementation("org.springframework.boot:spring-boot-starter-oauth2-client:3.4.3")


	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	runtimeOnly("org.postgresql:postgresql")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<JavaExec>("run") { // "run" is the default task name for application plugin
	environment["PWDEBUG"] = "1"
}

// Enable Layered JARs
tasks.withType<org.springframework.boot.gradle.tasks.bundling.BootJar> {
	archiveFileName.set("csw-app.jar") // Set custom JAR name
	launchScript() // Makes the JAR executable
	layered { // Enables Layered JAR
		includeTools = true
	}
}

// Optimize dependency resolution
configurations.all {
	resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

// Enable GraalVM native image
graalvmNative {
	binaries {
		named("main") {
			buildArgs.add("--no-fallback") // Disable Java fallback image
		}
	}
}