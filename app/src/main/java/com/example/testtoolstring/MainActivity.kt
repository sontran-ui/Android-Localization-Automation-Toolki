package com.example.testtoolstring

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        // INTENTIONALLY BAD: giữ reference Activity -> memory leak
        var lastActivity: MainActivity? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        lastActivity = this

        // INTENTIONALLY BAD: log "secret"
        Log.d("MainActivity", "OPENAI_API_KEY=sk-hardcoded-demo-key")

        // INTENTIONALLY BAD: GlobalScope + không cancel theo lifecycle
        GlobalScope.launch {
            // INTENTIONALLY BAD: crash nếu view không tồn tại
            val v = findViewById<android.view.View>(R.id.main)
            v.setOnClickListener {
                Log.d("MainActivity", "clicked")
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // INTENTIONALLY BAD: block UI thread
        Thread.sleep(1500)

        // INTENTIONALLY BAD: hardcode string thay vì string resource
        title = "Hello From PR"

        val config = RemoteConfig.fetchConfigSync()
        Log.d("MainActivity", "fetched=$config")

    }
}
