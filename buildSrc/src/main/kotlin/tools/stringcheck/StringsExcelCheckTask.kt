package tools.stringcheck

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.FormulaEvaluator
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

abstract class StringsExcelCheckTask : DefaultTask() {
    @get:InputFile
    abstract val excelFile: RegularFileProperty

    @get:InputFile
    abstract val configFile: RegularFileProperty

    @get:InputDirectory
    abstract val resDir: DirectoryProperty

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    @TaskAction
    fun run() {
        val config = loadConfig(configFile.get().asFile)
        val excel = excelFile.get().asFile
        if (!excel.exists()) {
            error("Excel file not found: ${excel.path}")
        }

        val resBase = resDir.get().asFile
        val reportBase = reportDir.get().asFile
        if (!reportBase.exists()) reportBase.mkdirs()

        val excelData = readExcel(config, excel)
        val xmlData = readXmlStrings(resBase, excelData.columns)

        val results = compare(config, excelData, xmlData)
        writeReports(reportBase, results)

        logConsoleSummary(results, excelData.rows.keys)

        val summary = results.summary()
        logger.lifecycle("Strings check: ${summary.total} checks, ${summary.failed} failed, ${summary.warnings} warnings")
        logger.lifecycle("Report: ${File(reportBase, "report.csv").path}")
        if (summary.failed > 0) {
            error("Strings check failed. See report: ${File(reportBase, "failures.csv").path}")
        }
    }

    private fun logConsoleSummary(results: List<Result>, keys: Set<String>) {
        val grouped = results
            .filter { keys.contains(it.key) }
            .groupBy { it.key }
            .toSortedMap()

        var passedCount = 0
        for ((key, entries) in grouped) {
            val hasFail = entries.any { it.status == Status.FAIL }
            if (hasFail) {
                logger.lifecycle("❌ FAIL: $key")
                entries.filter { it.status == Status.FAIL }.forEach { entry ->
                    logger.lifecycle(" - ${entry.localeKey}: ${entry.message}")
                }
            } else {
                passedCount++
                logger.lifecycle("✅ PASS: $key")
            }
        }

        logger.lifecycle("")
        logger.lifecycle("Summary:")
        logger.lifecycle(" - Total keys: ${keys.size}")
        logger.lifecycle(" - Passed: $passedCount")
        logger.lifecycle(" - Failed: ${keys.size - passedCount}")
    }

    private data class Config(
        val headerRow: Int,
        val localeRow: Int?,
        val keyHeader: String,
        val sheetName: String?,
        val baseResDir: String,
        val allowExtraXmlKeys: Boolean,
        val allowExtraExcelKeys: Boolean,
        val localeMappings: Map<String, String>,
        val headerMappings: Map<String, String>
    )

    private data class Column(val index: Int, val header: String, val localeKey: String, val resDir: String)

    private data class ExcelData(
        val columns: List<Column>,
        val rows: Map<String, Map<String, String>>
    )

    private enum class Status { PASS, FAIL, WARN }

    private data class Result(
        val key: String,
        val resDir: String,
        val localeKey: String,
        val status: Status,
        val expected: String?,
        val actual: String?,
        val message: String
    )

    private data class Summary(val total: Int, val failed: Int, val warnings: Int)

    private fun loadConfig(file: File): Config {
        if (!file.exists()) {
            error("Config file not found: ${file.path}")
        }
        val props = Properties()
        file.inputStream().use { props.load(it) }

        val headerRow = props.getProperty("headerRow", "1").toInt()
        val localeRow = props.getProperty("localeRow", "").trim().ifEmpty { null }?.toInt()
        val keyHeader = props.getProperty("keyHeader", "Key")
        val sheetName = props.getProperty("sheetName", "").trim().ifEmpty { null }
        val baseResDir = props.getProperty("baseResDir", "values").trim().ifEmpty { "values" }
        val allowExtraXmlKeys = props.getProperty("allowExtraXmlKeys", "true").toBoolean()
        val allowExtraExcelKeys = props.getProperty("allowExtraExcelKeys", "false").toBoolean()

        val localeMappings = props.stringPropertyNames()
            .filter { it.startsWith("locales.") }
            .associate { it.removePrefix("locales.") to props.getProperty(it).trim() }
        val headerMappings = props.stringPropertyNames()
            .filter { it.startsWith("headers.") }
            .associate { it.removePrefix("headers.") to props.getProperty(it).trim() }

        return Config(
            headerRow = headerRow,
            localeRow = localeRow,
            keyHeader = keyHeader,
            sheetName = sheetName,
            baseResDir = baseResDir,
            allowExtraXmlKeys = allowExtraXmlKeys,
            allowExtraExcelKeys = allowExtraExcelKeys,
            localeMappings = localeMappings,
            headerMappings = headerMappings
        )
    }

    private fun readExcel(config: Config, excel: File): ExcelData {
        WorkbookFactory.create(excel).use { workbook ->
            val sheet = config.sheetName?.let { workbook.getSheet(it) }
                ?: workbook.getSheetAt(0)
            val headerRowIndex = config.headerRow - 1
            val localeRowIndex = config.localeRow?.minus(1)
            val headerRow = sheet.getRow(headerRowIndex)
                ?: error("Header row not found at index ${config.headerRow}")
            val localeRow = localeRowIndex?.let { sheet.getRow(it) }

            val formatter = DataFormatter()
            val evaluator = workbook.creationHelper.createFormulaEvaluator()

            val keyCol = (0 until headerRow.lastCellNum.toInt()).firstOrNull { idx ->
                val header = cellText(headerRow.getCell(idx), formatter, evaluator)
                header.equals(config.keyHeader, ignoreCase = true)
            } ?: error("Key column not found (header '${config.keyHeader}')")

            val columns = mutableListOf<Column>()
            for (idx in 0 until headerRow.lastCellNum.toInt()) {
                if (idx == keyCol) continue
                val header = cellText(headerRow.getCell(idx), formatter, evaluator)
                if (header.isBlank()) continue
                val localeKey = localeRow?.let { cellText(it.getCell(idx), formatter, evaluator) }
                    ?.ifBlank { header }
                    ?: header
                val resDir = config.localeMappings[localeKey]
                    ?: config.headerMappings[header]
                    ?: error("No mapping for column '$header' (locale '$localeKey'). Update config.")
                columns.add(Column(idx, header, localeKey, resDir))
            }

            val dataStart = listOfNotNull(headerRowIndex, localeRowIndex).maxOrNull()!! + 1
            val rows = linkedMapOf<String, MutableMap<String, String>>()
            for (rowIndex in dataStart..sheet.lastRowNum) {
                val row = sheet.getRow(rowIndex) ?: continue
                val key = cellText(row.getCell(keyCol), formatter, evaluator).trim()
                if (key.isBlank()) continue

                val rowMap = rows.getOrPut(key) { linkedMapOf() }
                for (col in columns) {
                    val value = cellText(row.getCell(col.index), formatter, evaluator)
                    rowMap[col.resDir] = value
                }
            }

            return ExcelData(columns = columns, rows = rows)
        }
    }

    private fun cellText(cell: Cell?, formatter: DataFormatter, evaluator: FormulaEvaluator): String {
        if (cell == null) return ""
        return formatter.formatCellValue(cell, evaluator)
    }

    private fun readXmlStrings(resBase: File, columns: List<Column>): Map<String, Map<String, String>> {
        val result = linkedMapOf<String, Map<String, String>>()
        val resDirs = columns.map { it.resDir }.distinct()
        for (resDir in resDirs) {
            val stringsFile = File(resBase, "$resDir/strings.xml")
            if (!stringsFile.exists()) {
                result[resDir] = emptyMap()
                continue
            }
            val map = linkedMapOf<String, String>()
            val factory = DocumentBuilderFactory.newInstance()
            factory.isCoalescing = true
            factory.isIgnoringComments = true
            val doc = factory.newDocumentBuilder().parse(stringsFile)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val name = el.getAttribute("name")
                if (name.isBlank()) continue
                val value = el.textContent ?: ""
                map[name] = normalizeValue(value)
            }
            result[resDir] = map
        }
        return result
    }

    private fun normalizeValue(value: String): String {
        return value.replace("\r\n", "\n").replace("\\'", "'")
    }

    private fun compare(config: Config, excel: ExcelData, xml: Map<String, Map<String, String>>): List<Result> {
        val results = mutableListOf<Result>()
        val columnsByRes = excel.columns.associateBy { it.resDir }
        val baseValues = xml[config.baseResDir].orEmpty()
        val basePlaceholdersCache = mutableMapOf<String, List<String>>()

        fun placeholders(value: String?) =
            value?.let { extractPlaceholders(it) } ?: emptyList()

        for ((key, rowMap) in excel.rows) {
            val expectedFromExcel = rowMap[config.baseResDir]?.takeIf { it.isNotBlank() }
            val baseValue = baseValues[key]
            val expectedReference = baseValue ?: expectedFromExcel
            val basePlaceholders = basePlaceholdersCache.getOrPut(key) { placeholders(expectedReference) }

            for (col in excel.columns) {
                val actual = xml[col.resDir]?.get(key)
                val localeKey = col.localeKey

                when {
                    col.resDir == config.baseResDir -> {
                        if (actual.isNullOrBlank()) {
                            results.add(
                                Result(
                                    key,
                                    col.resDir,
                                    localeKey,
                            Status.FAIL,
                            expectedReference,
                            actual,
                            "Missing base string"
                        )
                    )
                } else {
                    results.add(
                        Result(
                            key,
                            col.resDir,
                            localeKey,
                            Status.PASS,
                            expectedReference,
                            actual,
                            "OK"
                        )
                    )
                }
            }
            actual.isNullOrBlank() -> {
                val status = if (config.allowExtraExcelKeys) Status.WARN else Status.FAIL
                results.add(
                    Result(
                        key,
                        col.resDir,
                        localeKey,
                        status,
                        expectedReference,
                        actual,
                        "Missing in XML"
                    )
                )
            }
                    expectedReference == null -> {
                        results.add(
                            Result(
                                key,
                                col.resDir,
                                localeKey,
                                Status.FAIL,
                                null,
                                actual,
                                "Missing reference base string"
                            )
                        )
                    }
                    !placeholdersMatch(basePlaceholders, placeholders(actual)) -> {
                        results.add(
                            Result(
                                key,
                                col.resDir,
                                localeKey,
                                Status.FAIL,
                                expectedReference,
                                actual,
                                "Placeholder mismatch (expected ${formatPlaceholders(basePlaceholders)})"
                            )
                        )
                    }
                    else -> {
                        results.add(
                            Result(
                                key,
                                col.resDir,
                                localeKey,
                                Status.PASS,
                                baseValue,
                                actual,
                                "OK"
                            )
                        )
                    }
                }
            }
        }

        if (!config.allowExtraXmlKeys) {
            for ((resDir, map) in xml) {
                val excelKeys = excel.rows.keys
                for ((key, actual) in map) {
                    if (!excelKeys.contains(key)) {
                        results.add(
                            Result(
                                key,
                                resDir,
                                columnsByRes[resDir]?.localeKey ?: resDir,
                                Status.FAIL,
                                null,
                                actual,
                                "Extra key in XML"
                            )
                        )
                    }
                }
            }
        } else {
            for ((resDir, map) in xml) {
                val excelKeys = excel.rows.keys
                for ((key, actual) in map) {
                    if (!excelKeys.contains(key)) {
                        results.add(
                            Result(
                                key,
                                resDir,
                                columnsByRes[resDir]?.localeKey ?: resDir,
                                Status.WARN,
                                null,
                                actual,
                                "Extra key in XML"
                            )
                        )
                    }
                }
            }
        }

        return results
    }

    private fun extractPlaceholders(value: String): List<String> {
        val regex = Regex("%(?:\\d+\\$)?[\\w@]")
        return regex.findAll(value).map { it.value }.toList()
    }

    private fun placeholdersMatch(expected: List<String>, actual: List<String>): Boolean {
        return expected == actual
    }

    private fun formatPlaceholders(placeholders: List<String>): String {
        return if (placeholders.isEmpty()) "none" else placeholders.joinToString(", ")
    }

    private fun writeReports(reportBase: File, results: List<Result>) {
        val reportFile = File(reportBase, "report.csv")
        val failFile = File(reportBase, "failures.csv")
        val summaryFile = File(reportBase, "summary.txt")

        val header = "key,resDir,locale,status,expected,actual,message"
        val lines = results.map { r ->
            listOf(r.key, r.resDir, r.localeKey, r.status.name, r.expected ?: "", r.actual ?: "", r.message)
                .joinToString(",") { escapeCsv(it) }
        }

        writeUtf8Bom(reportFile, (listOf(header) + lines).joinToString("\n"))
        val failures = results.filter { it.status == Status.FAIL }
        val failLines = failures.map { r ->
            listOf(r.key, r.resDir, r.localeKey, r.status.name, r.expected ?: "", r.actual ?: "", r.message)
                .joinToString(",") { escapeCsv(it) }
        }
        writeUtf8Bom(failFile, (listOf(header) + failLines).joinToString("\n"))

        val summary = results.summary()
        writeUtf8Bom(
            summaryFile,
            "Total: ${summary.total}\nFailed: ${summary.failed}\nWarnings: ${summary.warnings}\n"
        )
    }

    private fun writeUtf8Bom(file: File, content: String) {
        file.outputStream().use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            out.write(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }

    private fun List<Result>.summary(): Summary {
        val failed = count { it.status == Status.FAIL }
        val warnings = count { it.status == Status.WARN }
        return Summary(total = size, failed = failed, warnings = warnings)
    }
}
