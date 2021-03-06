package org.xwiki.android.sync.auth

import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.xwiki.android.sync.R
import org.xwiki.android.sync.URL_FIELD
import org.xwiki.android.sync.utils.openLink

class RegistrationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)
        val webView = findViewById<WebView>(R.id.webview)
        val url = intent.getStringExtra(URL_FIELD) ?: error("Url was not provided for registration WebView")
        webView.openLink(url)
    }
}
