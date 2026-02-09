# Strings Excel Check

1) Put your Excel file at: tools/strings-tool/strings.xlsx. The sheet should list the keys you want to verify (column A) and the headers for each language/locale.
2) Update `tools/strings-tool/config.properties`:
   * Map the `En`, `Vi`, `hi-IN`, ... headers to the matching `values-*` folders (~`locales.<code>=values-xx`).
   * Set `baseResDir` (default `values`) to the locale that should act as the placeholder reference.
3) Run:

```
./gradlew :app:checkStringsFromExcel
```

Optional: override paths

```
./gradlew :app:checkStringsFromExcel -PstringsExcel=path/to/file.xlsx -PstringsConfig=path/to/config.properties
```

Reports are written to:
`app/build/reports/strings-check/`
- report.csv: all checks
- failures.csv: only FAIL
- summary.txt

Console output now reports each key with `PASS`/`FAIL` and highlights placeholder mismatches or missing locales; a short summary follows the detail.
