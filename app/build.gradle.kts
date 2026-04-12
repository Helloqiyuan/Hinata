import java.util.concurrent.TimeUnit

plugins {
    // AGP 9 内置 Kotlin，无需再应用 org.jetbrains.kotlin.android
    alias(libs.plugins.android.application)
}

/** 将 git remote 输出规范为浏览器可打开的 HTTPS（构建期使用） */
fun normalizeGitRemoteToHttps(raw: String): String {
    val t = raw.trim().removeSuffix(".git").trimEnd('/')
    if (t.startsWith("git@github.com:")) {
        return "https://github.com/" + t.removePrefix("git@github.com:")
    }
    if (t.startsWith("ssh://git@github.com/")) {
        return "https://github.com/" + t.removePrefix("ssh://git@github.com/")
    }
    if (t.startsWith("http://") || t.startsWith("https://")) {
        return t
    }
    return "https://github.com/Helloqiyuan/Hinata"
}

/** 写入 BuildConfig 字符串字段时转义引号与反斜杠 */
fun escapeForBuildConfigString(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

val gitRepoUrlForBuildConfig: String = run {
    val fallback = "https://github.com/Helloqiyuan/Hinata"
    try {
        val rootDir = rootProject.projectDir
        val pb = ProcessBuilder("git", "remote", "get-url", "origin")
            .directory(rootDir)
            .redirectErrorStream(true)
        val p = pb.start()
        if (!p.waitFor(5, TimeUnit.SECONDS)) {
            p.destroyForcibly()
            return@run fallback
        }
        if (p.exitValue() != 0) return@run fallback
        val out = p.inputStream.bufferedReader().use { it.readText() }.trim()
        if (out.isEmpty()) fallback else normalizeGitRemoteToHttps(out)
    } catch (_: Exception) {
        fallback
    }
}

android {
    namespace = "com.qiyuan.hinata"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.qiyuan.hinata"
        minSdk = 29
        targetSdk = 36
        versionCode = 4
        versionName = "1.1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "GIT_REPO_URL",
            "\"${escapeForBuildConfigString(gitRepoUrlForBuildConfig)}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
