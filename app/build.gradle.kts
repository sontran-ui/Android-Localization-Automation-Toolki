import tools.stringcheck.StringsExcelCheckTask
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.net.URL
import org.w3c.dom.Element
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.testtoolstring"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.testtoolstring"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

tasks.register("checkStringsFromExcel", StringsExcelCheckTask::class) {
    group = "verification"
    description = "Compare strings.xml files with translations in an Excel sheet."

    val excelPath = project.findProperty("stringsExcel")?.toString()
        ?: "C:\\Users\\Admin\\StudioProjects\\testtoolstring\\buildSrc\\src\\main\\kotlin\\tools\\stringcheck\\Official String tool.xlsx"
    val configPath = project.findProperty("stringsConfig")?.toString()
        ?: "tools/strings-tool/config.properties"

    excelFile.set(rootProject.layout.projectDirectory.file(excelPath))
    configFile.set(rootProject.layout.projectDirectory.file(configPath))
    resDir.set(project.layout.projectDirectory.dir("src/main/res"))
    reportDir.set(layout.buildDirectory.dir("reports/strings-check"))
}

tasks.register("syncStrings") {

    doLast {

        val apiUrl =
            "https://script.google.com/macros/s/AKfycbxZVroQCWVO_pFNajzH0h-a7SLwb0l6_WlmGFBPLqn0MkcuPF9-d8zqtPN-Dar0X_fC/exec"

        println("Fetching remote strings...")
        val json = URL(apiUrl).readText()

        val gson = Gson()
        val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
        val remoteData: Map<String, Map<String, String>> =
            gson.fromJson(json, type)

        val baseLocale = remoteData.keys.firstOrNull {
            it.equals("en-US", true) ||
                    it.equals("en", true) ||
                    it.contains("en", true)
        } ?: throw GradleException(
            "No English base locale found. Available: ${remoteData.keys}"
        )

        println("Base locale detected: $baseLocale")

        val baseEnglish = remoteData[baseLocale]!!

        remoteData.forEach { (localeFull, remoteStringsRaw) ->

            val languageCode = localeFull.substringBefore("-").lowercase()

            val folderName =
                if (languageCode == "en") "values"
                else "values-$languageCode"

            val resDir =
                File(project.projectDir, "src/main/res/$folderName")

            resDir.mkdirs()

            val file = File(resDir, "strings.xml")

            println("Overwriting locale: $localeFull → $folderName")

            val builder = StringBuilder()
            builder.appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
            builder.appendLine("<resources>")

            remoteStringsRaw
                .forEach { (key, rawValue) ->

                    if (key.isBlank()) return@forEach

                    val value = normalize(rawValue)

                    validateKey(key)
                    validateXml(value)

                    val baseValue = baseEnglish[key] ?: ""
                    val basePH = extractPlaceholders(baseValue)
                    val currentPH = extractPlaceholders(value)

                    if (basePH != currentPH) {
                        throw GradleException(
                            """
                            Placeholder mismatch detected!
                            Locale: $localeFull
                            Key: $key
                            Base: $basePH
                            Current: $currentPH
                            """.trimIndent()
                        )
                    }

                    builder.appendLine(
                        """    <string name="$key">${escapeXml(value)}</string>"""
                    )
                }

            builder.appendLine("</resources>")

            file.writeText(builder.toString())
        }

        println("✔ Full overwrite completed successfully.")
    }
}

fun normalize(value: String): String {
    return value.trim()
}

fun validateKey(key: String) {
    val regex = Regex("^[a-z0-9_]+$")
    if (!regex.matches(key)) {
        throw GradleException("Invalid key format: $key")
    }
}

fun validateXml(value: String) {
    if (value.contains("<string") || value.contains("</resources>")) {
        throw GradleException("Invalid XML injection detected.")
    }
}

fun escapeXml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "\\\"")
        .replace("'", "\\'")
}

fun extractPlaceholders(value: String): List<String> {
    val regex = Regex("%(\\d+\\$)?[sd]")
    return regex.findAll(value)
        .map { it.value }
        .sorted()
        .toList()
}


