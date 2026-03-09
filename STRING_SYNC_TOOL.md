# 📘 Android String Sync Tool Guide

Tool này giúp đồng bộ string đa ngôn ngữ từ Google Sheet vào Android
project bằng 1 lệnh Gradle.

------------------------------------------------------------------------

# 1️⃣ Cài đặt vào Project Android

## Bước 1: Thêm dependency

Trong `app/build.gradle.kts`:

``` kotlin
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URL
```

Đảm bảo project có Gson:

``` kotlin
implementation("com.google.code.gson:gson:2.10.1")
```

------------------------------------------------------------------------

## Bước 2: Copy Task vào `app/build.gradle.kts`

Copy toàn bộ phần:

``` kotlin
tasks.register("syncStrings") { ... }
```

và các hàm phía dưới:

-   normalize()
-   validateKey()
-   validateXml()
-   escapeXml()
-   extractPlaceholders()

------------------------------------------------------------------------

## Bước 3: Thay API URL

Tìm dòng:

``` kotlin
val apiUrl = "https://script.google.com/macros/s/XXXX/exec"
```

Thay bằng URL Web App của bạn.

------------------------------------------------------------------------

# 2️⃣ Tạo API từ Google Sheet

## Bước 1: Mở Google Sheet

Vào:

Extensions → Apps Script

------------------------------------------------------------------------

## Bước 2: Deploy Web App

1.  Nhấn **Deploy**
2.  Chọn **New deployment**
3.  Type: Web app
4.  Execute as: Me
5.  Who has access: Anyone
6.  Deploy

Bạn sẽ nhận được URL dạng:

https://script.google.com/macros/s/AKfycbxxxxxxx/exec

⚠ Phải là URL kết thúc bằng `/exec`

------------------------------------------------------------------------

# 3️⃣ Cách sử dụng Tool

Mở terminal tại root project Android.

## 🔹 Ghi đè toàn bộ string

``` bash
./gradlew syncStrings -Pmode=overwrite
```

## 🔹 Chỉ thêm key chưa tồn tại

``` bash
./gradlew syncStrings -Pmode=append
```

## 🔹 Merge thông minh

``` bash
./gradlew syncStrings -Pmode=merge
```

## 🔹 Chỉ sync một số key

PowerShell:

``` bash
./gradlew syncStrings -Pmode=merge -Pkeys="msg_test,msg_library_explore"
```

CMD / Mac / Linux:

``` bash
./gradlew syncStrings -Pmode=merge -Pkeys=msg_test,msg_library_explore
```

------------------------------------------------------------------------

# 4️⃣ Cấu trúc Sheet yêu cầu

| KEY \| Max-Length \| en-US \| vi-VN \| de-DE \| ... \|

-   KEY chỉ chứa: a-z, 0-9, \_
-   Placeholder phải giống nhau giữa các ngôn ngữ
-   Không chứa `<string>` hoặc `</resources>`

------------------------------------------------------------------------

# 5️⃣ Cơ chế hoạt động

1.  Gradle gọi API Google Apps Script
2.  API trả JSON theo từng locale
3.  Tool validate key, placeholder, XML
4.  Tạo hoặc cập nhật strings.xml
5.  Tự tạo folder values, values-vi, values-de...

------------------------------------------------------------------------

# 🎯 Kết luận

Tool giúp:

-   Quản lý string tập trung
-   Tránh lỗi placeholder runtime
-   Sync toàn bộ hoặc từng phần
-   Tự động hóa hoàn toàn import string
