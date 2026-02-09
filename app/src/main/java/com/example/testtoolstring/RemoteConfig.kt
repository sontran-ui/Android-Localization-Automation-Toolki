package com.example.testtoolstring

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object RemoteConfig {

    // INTENTIONALLY BAD: network call sync, không timeout, không error handling chuẩn
    fun fetchConfigSync(): String {
        val url = URL("https://example.com/config.json")
        val conn = (url.openConnection() as HttpURLConnection)
        conn.requestMethod = "GET"
        val body = conn.inputStream.bufferedReader().readText()
        Log.d("RemoteConfig", "config=$body") // INTENTIONALLY BAD: log raw config
        return body
    }
}
