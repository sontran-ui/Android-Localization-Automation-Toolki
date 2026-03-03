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

        // Detect base locale dynamically
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

            // localeFull ví dụ: en-US, vi-VN, de-DE
            val languageCode = localeFull.substringBefore("-").lowercase()

            val folderName =
                if (languageCode == "en") "values"
                else "values-$languageCode"

            val resDir =
                File(project.projectDir, "src/main/res/$folderName")

            resDir.mkdirs()

            val file = File(resDir, "strings.xml")

            val existingContent =
                if (file.exists()) file.readText()
                else "<resources>\n</resources>"

            var content = existingContent

            println("Syncing locale: $localeFull → $folderName")

            remoteStringsRaw
                .filter { it.key.isNotBlank() }
                .forEach { (key, rawValue) ->

                    val remoteValue = normalize(rawValue)

                    validateKey(key)
                    validateXml(remoteValue)

                    // placeholder check so với English
                    val baseValue = baseEnglish[key] ?: ""
                    val basePH = extractPlaceholders(baseValue)
                    val currentPH = extractPlaceholders(remoteValue)

                    if (basePH != currentPH) {
                        throw GradleException(
                            """
                            Placeholder mismatch detected!
                            Locale: $localeFull
                            Key: $key
                            Base (en-US): $basePH
                            Current: $currentPH
                            """.trimIndent()
                        )
                    }

                    val stringRegex =
                        Regex("""<string\s+name="$key"[^>]*>([\s\S]*?)</string>""")

                    if (stringRegex.containsMatchIn(content)) {

                        val match = stringRegex.find(content)!!
                        val oldRaw = match.groupValues[1]
                        val oldValue = normalize(oldRaw)

                        if (oldValue != remoteValue) {

                            val newNode =
                                """    <string name="$key">${escapeXml(remoteValue)}</string>"""

                            content =
                                content.replace(match.value, newNode)

                            println("Updated: $folderName/$key")
                        }

                    } else {

                        val newNode =
                            """    <string name="$key">${escapeXml(remoteValue)}</string>
"""

                        content =
                            content.replace(
                                "</resources>",
                                "$newNode</resources>"
                            )

                        println("Added: $folderName/$key")
                    }
                }

            file.writeText(content)
        }

        println("✔ Sync completed successfully.")
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


