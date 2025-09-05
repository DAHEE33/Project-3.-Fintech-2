import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.Confidence

plugins {
	java
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.owasp.dependencycheck") version "12.1.0"
	id("com.github.spotbugs") version "6.0.4"
	id("pmd")
	id("io.gatling.gradle") version "3.10.5"
}

group = "fintech2"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

tasks.withType<JavaCompile> {
	options.compilerArgs.addAll(listOf("--enable-preview"))
}

tasks.withType<Test> {
	jvmArgs("--enable-preview")
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
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// Redis는 개발 환경에서 제외 (운영 환경에서만 사용)
	// implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")

	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.3")
	implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
	implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

	// Database
	runtimeOnly("com.h2database:h2")
	runtimeOnly("org.postgresql:postgresql")

	// Swagger
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

	// BCrypt
	implementation("org.springframework.security:spring-security-crypto")

	// Monitoring
	implementation("io.micrometer:micrometer-registry-prometheus")

	compileOnly("org.projectlombok:lombok")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	
	// Gatling 성능 테스트
	gatlingImplementation("io.gatling.highcharts:gatling-charts-highcharts:3.10.5")
	gatlingImplementation("io.gatling:gatling-http-java:3.10.5")

	
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Gatling 설정 - 기본 설정 사용

// SpotBugs 플러그인 전체 설정 (Extension)
spotbugs {
	toolVersion.set("4.8.3")
	effort.set(Effort.MAX)
	reportLevel.set(Confidence.MEDIUM)
	excludeFilter.set(file("$projectDir/config/spotbugs/exclude.xml"))
	// ignoreFailures 속성은 Task에서만!
}

// SpotBugs Task별 설정 (여기서 ignoreFailures 가능)
tasks.withType<com.github.spotbugs.snom.SpotBugsTask> {
	ignoreFailures = true  // 임시로 에러 무시 (개발 중)
}

// PMD 플러그인 전체 설정 (Extension)
pmd {
	toolVersion = "6.55.0"
	ruleSetFiles = files("$projectDir/config/pmd/ruleset.xml")
	ruleSets = listOf()
	// ignoreFailures 속성은 Task에서만!
}

// PMD Task별 설정 (여기서 ignoreFailures 가능)
tasks.withType<org.gradle.api.plugins.quality.Pmd> {
	ignoreFailures = true  // 임시로 에러 무시 (개발 중)
}

// OWASP Dependency Check 설정
dependencyCheck {
	formats = listOf("HTML", "JSON")
	outputDirectory = "${layout.buildDirectory.get()}/reports/dependency-check"
	scanSet = listOf(file("$projectDir"))
	suppressionFile = "$projectDir/config/dependency-check/suppressions.xml"
}

// Docker 이미지 빌드 Task
tasks.register("dockerBuild") {
	dependsOn("build")
	doLast {
		exec {
			commandLine("docker", "build", "-t", "easypay:latest", ".")
		}
	}
}

// Docker Compose Up Task
tasks.register("dockerComposeUp") {
	doLast {
		exec {
			commandLine("docker-compose", "up", "-d")
		}
	}
}

// Docker Compose Down Task
tasks.register("dockerComposeDown") {
	doLast {
		exec {
			commandLine("docker-compose", "down")
		}
	}
}
