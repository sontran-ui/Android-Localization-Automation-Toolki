import tools.stringcheck.StringsExcelCheckTask

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
