package com.qrprint.customer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val BASE_URL = "https://qr-print-ochre.vercel.app"
        private const val DEFAULT_CAFE_URL = "https://qr-print-ochre.vercel.app/c/the-smart-cafe-4ggv"
        private const val START_WITH_SCANNER = true
        private const val PREFS_NAME = "qr_print_customer_prefs"
        private const val KEY_LAST_CAFE_URL = "last_cafe_url"
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var cafeHeaderText: TextView

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoUri: Uri? = null

    private val qrScanLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scannedRaw = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)
            if (!scannedRaw.isNullOrBlank()) {
                val resolvedUrl = resolveCafeUrl(scannedRaw)
                saveLastCafeUrl(resolvedUrl)
                loadCafeUrl(resolvedUrl)
            }
        } else if (webView.url.isNullOrBlank()) {
            val fallback = getLastCafeUrl() ?: DEFAULT_CAFE_URL
            loadCafeUrl(fallback)
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        var results: Array<Uri>? = null

        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data == null || (data.data == null && data.clipData == null)) {
                cameraPhotoUri?.let { results = arrayOf(it) }
            } else if (data.clipData != null) {
                val count = data.clipData!!.itemCount
                results = Array(count) { i -> data.clipData!!.getItemAt(i).uri }
            } else if (data.data != null) {
                results = arrayOf(data.data!!)
            }
        }
        callback.onReceiveValue(results)
        filePathCallback = null
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            openQrScanner()
        } else {
            Toast.makeText(
                this,
                "QR स्कैन करने के लिए कैमरा अनुमति जरूरी है",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progressBar = findViewById(R.id.progressBar)
        cafeHeaderText = findViewById(R.id.cafeHeaderText)

        val btnScanQr = findViewById<Button>(R.id.btnScanQr)
        val btnCafeMenu = findViewById<Button>(R.id.btnCafeMenu)
        val btnReload = findViewById<Button>(R.id.btnReload)

        swipeRefresh.isEnabled = true
        swipeRefresh.setOnRefreshListener {
            webView.reload()
        }

        configureWebView()

        btnScanQr.setOnClickListener { requestCameraAndScan() }
        btnCafeMenu.setOnClickListener {
            val cafeHome = getLastCafeUrl() ?: DEFAULT_CAFE_URL
            loadCafeUrl(cafeHome)
        }
        btnReload.setOnClickListener { webView.reload() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        val savedUrl = getLastCafeUrl()
        if (START_WITH_SCANNER) {
            requestCameraAndScan()
        } else {
            loadCafeUrl(savedUrl ?: DEFAULT_CAFE_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUrl = request?.url?.toString() ?: return false
                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                    true
                } catch (_: Exception) {
                    false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
                progressBar.visibility = View.GONE
                if (!url.isNullOrBlank()) {
                    cafeHeaderText.text = extractCafeLabel(url)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
                progressBar.progress = newProgress
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                openNativeFileAndCameraChooser(params)
                return true
            }
        }
    }

    private fun openNativeFileAndCameraChooser(params: WebChromeClient.FileChooserParams?) {
        val contentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            val acceptTypes = params?.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray()
            if (!acceptTypes.isNullOrEmpty()) {
                putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes)
            }
            if (params?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        val cameraIntent = createCameraCaptureIntent()
        val chooser = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, contentIntent)
            putExtra(Intent.EXTRA_TITLE, "फाइल या फोटो चुनें")
            if (cameraIntent != null) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
        }
        fileChooserLauncher.launch(chooser)
    }

    private fun createCameraCaptureIntent(): Intent? {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: cacheDir
            val imageFile = File.createTempFile("QRPRINT_${timestamp}_", ".jpg", storageDir)
            val uri = FileProvider.getUriForFile(this, "${applicationId}.fileprovider", imageFile)
            cameraPhotoUri = uri
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestCameraAndScan() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            openQrScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openQrScanner() {
        val intent = Intent(this, QrScannerActivity::class.java)
        qrScanLauncher.launch(intent)
    }

    private fun resolveCafeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        if (trimmed.startsWith("/c/") || trimmed.startsWith("c/")) {
            return "$BASE_URL/${trimmed.removePrefix("/")}"
        }
        return "$BASE_URL/c/$trimmed"
    }

    private fun extractCafeLabel(url: String): String {
        val regex = Regex("/c/([^/?#]+)")
        val slug = regex.find(url)?.groupValues?.getOrNull(1) ?: return "QR Print"
        return slug.replace(Regex("-[a-z0-9]{4}$"), "").replace("-", " ").uppercase()
    }

    private fun saveLastCafeUrl(url: String) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_CAFE_URL, url)
            .apply()
    }

    private fun getLastCafeUrl(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_LAST_CAFE_URL, null)
    }

    private fun loadCafeUrl(url: String) {
        cafeHeaderText.text = extractCafeLabel(url)
        webView.loadUrl(url)
    }
}
