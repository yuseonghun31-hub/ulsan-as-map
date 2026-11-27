package kr.ulsan.asmap

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * 울산 AS 지도앱(github.io)을 감싸는 앱.
 * 크롬 안의 웹페이지는 세라젬 AS 앱을 열 수 없지만(그 앱에 '웹에서 열어도 되는 문'이 없음),
 * 설치된 앱끼리는 열 수 있으므로 이 앱이 대신 열어 준다.
 */
class MainActivity : Activity() {

    companion object {
        const val HOME = "https://yuseonghun31-hub.github.io/ulsan-as-map/"
        const val SITE_HOST = "yuseonghun31-hub.github.io"
        const val SITE_PATH = "/ulsan-as-map"
        // 지도앱이 열 수 있는 앱은 이것뿐 (다른 앱은 거절)
        val ALLOWED_APPS = setOf("com.ceragem.as.android", "com.nhn.android.nmap")
        const val REQ_LOCATION = 7
    }

    private lateinit var web: WebView
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0E1316"))
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true            // PIN·설정·캐시 (지도앱이 localStorage를 씀)
            databaseEnabled = true
            setGeolocationEnabled(true)
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)    // 새 탭 링크도 같은 흐름에서 처리
            userAgentString = "$userAgentString UlsanAsMapApp/1"
        }
        web.addJavascriptInterface(Bridge(), "AsBridge")
        web.webViewClient = Client()
        web.webChromeClient = Chrome()

        if (savedInstanceState != null) web.restoreState(savedInstanceState) else web.loadUrl(HOME)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun isOurSite(uri: Uri?): Boolean =
        uri != null && uri.scheme == "https" && uri.host == SITE_HOST && (uri.path ?: "").startsWith(SITE_PATH)

    /** 지도앱 자바스크립트에서 부르는 다리: window.AsBridge.openApp("com.ceragem.as.android") */
    inner class Bridge {
        @JavascriptInterface
        fun openApp(pkg: String): Boolean {
            if (pkg !in ALLOWED_APPS) return false
            val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runOnUiThread {
                // 우리 지도 페이지가 떠 있을 때만 (다른 페이지가 이 다리를 쓰지 못하게)
                if (isOurSite(Uri.parse(web.url ?: ""))) {
                    try { startActivity(launch) } catch (e: ActivityNotFoundException) { toast("앱을 열 수 없어요") }
                }
            }
            return true
        }

        @JavascriptInterface
        fun hasApp(pkg: String): Boolean =
            pkg in ALLOWED_APPS && packageManager.getLaunchIntentForPackage(pkg) != null

        @JavascriptInterface
        fun version(): String = "1"
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
            val uri = req.url
            if (isOurSite(uri)) return false                       // 지도앱 안은 그대로
            return openOutside(uri)
        }
    }

    /** 지도앱 밖으로 나가는 것들: 전화(tel:), 문자, 네이버 지도(nmap:), intent:, 웹 MAS·기타 웹은 크롬으로 */
    private fun openOutside(uri: Uri): Boolean {
        try {
            when (uri.scheme) {
                "intent" -> {
                    val i = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                    i.addCategory(Intent.CATEGORY_BROWSABLE); i.component = null; i.selector = null
                    try { startActivity(i) } catch (e: ActivityNotFoundException) {
                        i.getStringExtra("browser_fallback_url")?.let { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                    }
                }
                "http", "https" -> startActivity(Intent(Intent.ACTION_VIEW, uri))
                else -> startActivity(Intent(Intent.ACTION_VIEW, uri))     // tel:, sms:, nmap: …
            }
        } catch (e: Exception) {
            toast("열 수 있는 앱이 없어요")
        }
        return true
    }

    private inner class Chrome : WebChromeClient() {
        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callback.invoke(origin, true, false)
            } else {
                pendingGeo = origin to callback
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            val ok = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            pendingGeo?.let { (origin, cb) -> cb.invoke(origin, ok, false) }
            pendingGeo = null
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
