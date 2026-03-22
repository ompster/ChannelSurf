package com.ompster.channelsurf

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.*
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        private const val NAMESPACE = "urn:x-cast:com.channelsurf"
        private const val PREFS = "channelsurf_prefs"
    }

    // UI
    private lateinit var playlistInput: EditText
    private lateinit var clipSlider: Slider
    private lateinit var clipValue: TextView
    private lateinit var transitionSpinner: Spinner
    private lateinit var fadeSlider: Slider
    private lateinit var fadeValue: TextView
    private lateinit var crtSwitch: SwitchMaterial
    private lateinit var channelSwitch: SwitchMaterial
    private lateinit var titleSwitch: SwitchMaterial
    private lateinit var btnStart: Button
    private lateinit var btnSkip: Button
    private lateinit var btnStop: Button
    private lateinit var btnSignIn: Button
    private lateinit var webView: WebView
    private lateinit var controlsLayout: View
    private lateinit var castStatus: TextView
    private lateinit var signInStatus: TextView

    // Cast
    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    private var playing = false

    private val transitionValues = arrayOf(
        "static", "fade", "glitch", "channel", "vhs", "pixel", "crt", "flash", "scan", "random"
    )

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            onCastConnected()
        }
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            onCastConnected()
        }
        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
            onCastDisconnected()
        }
        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {
            castSession = null
            castStatus.text = "Cast failed to connect"
        }
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "ChannelSurf"

        // Handle edge-to-edge insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_layout)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top, bottom = systemBars.bottom)
            insets
        }

        // Bind views
        playlistInput = findViewById(R.id.playlist_input)
        clipSlider = findViewById(R.id.clip_slider)
        clipValue = findViewById(R.id.clip_value)
        transitionSpinner = findViewById(R.id.transition_spinner)
        fadeSlider = findViewById(R.id.fade_slider)
        fadeValue = findViewById(R.id.fade_value)
        crtSwitch = findViewById(R.id.crt_switch)
        channelSwitch = findViewById(R.id.channel_switch)
        titleSwitch = findViewById(R.id.title_switch)
        btnStart = findViewById(R.id.btn_start)
        btnSkip = findViewById(R.id.btn_skip)
        btnStop = findViewById(R.id.btn_stop)
        webView = findViewById(R.id.webview)
        controlsLayout = findViewById(R.id.controls_scroll)
        castStatus = findViewById(R.id.cast_status)

        // Transition spinner
        val adapter = ArrayAdapter.createFromResource(
            this, R.array.transition_labels, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        transitionSpinner.adapter = adapter

        // Slider labels
        clipSlider.addOnChangeListener { _, value, _ ->
            clipValue.text = "${value.toInt()}s"
        }
        fadeSlider.addOnChangeListener { _, value, _ ->
            fadeValue.text = "${String.format("%.1f", value)}s"
        }

        // WebView setup (local fallback — needs full browser capabilities for YouTube)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
        }
        // Enable third-party cookies (required for YouTube auth/embeds)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        // Hardware acceleration for video
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // Sign in button
        btnSignIn = findViewById(R.id.btn_sign_in)
        signInStatus = findViewById(R.id.sign_in_status)
        btnSignIn.setOnClickListener { onYouTubeSignIn() }

        // Buttons
        btnStart.setOnClickListener { onStartSurfing() }
        btnSkip.setOnClickListener { onSkip() }
        btnStop.setOnClickListener { onStopSurfing() }

        // Load saved settings
        loadSettings()

        // Check if already signed into YouTube
        checkYouTubeSignIn()

        // Cast — request nearby devices permission on Android 12+
        requestNearbyPermission()
        try {
            castContext = CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            castStatus.text = "Cast unavailable"
        }
    }

    private fun requestNearbyPermission() {
        val permsNeeded = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: need NEARBY_WIFI_DEVICES
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED) {
                permsNeeded.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        // All versions: location helps with Cast/mDNS discovery
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permsNeeded.toTypedArray(), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.addSessionManagerListener(
            sessionManagerListener, CastSession::class.java
        )
        castSession = castContext?.sessionManager?.currentCastSession
        if (castSession?.isConnected == true) {
            onCastConnected()
        }
    }

    override fun onPause() {
        super.onPause()
        castContext?.sessionManager?.removeSessionManagerListener(
            sessionManagerListener, CastSession::class.java
        )
        saveSettings()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val mediaRouteButton = menu.findItem(R.id.media_route_menu_item)
            ?.actionView as? MediaRouteButton
        castContext?.let {
            CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item)
        }
        return true
    }

    // --- Cast connection ---
    private fun onCastConnected() {
        castStatus.text = "Connected to ${castSession?.castDevice?.friendlyName ?: "Chromecast"}"
        webView.visibility = View.GONE
        controlsLayout.visibility = View.VISIBLE
    }

    private fun onCastDisconnected() {
        castStatus.text = "Not connected"
        if (playing) {
            // Switch to local WebView playback
            showWebView()
        }
    }

    // --- Actions ---
    private fun onStartSurfing() {
        val playlistId = playlistInput.text.toString().trim()
        if (playlistId.isEmpty()) {
            playlistInput.error = "Enter a playlist ID"
            return
        }
        saveSettings()
        playing = true
        updateButtons()

        if (castSession?.isConnected == true) {
            sendCastMessage(buildStartMessage())
        } else {
            showWebView()
        }
    }

    private fun onSkip() {
        if (castSession?.isConnected == true) {
            sendCastMessage("""{"type":"SKIP"}""")
        } else {
            webView.evaluateJavascript("document.getElementById('btn-skip')?.click()", null)
        }
    }

    private fun onStopSurfing() {
        playing = false
        updateButtons()

        if (castSession?.isConnected == true) {
            sendCastMessage("""{"type":"STOP"}""")
        } else {
            webView.evaluateJavascript("document.getElementById('btn-stop')?.click()", null)
            webView.visibility = View.GONE
            controlsLayout.visibility = View.VISIBLE
        }
    }

    private fun updateButtons() {
        btnStart.visibility = if (playing) View.GONE else View.VISIBLE
        btnSkip.visibility = if (playing) View.VISIBLE else View.GONE
        btnStop.visibility = if (playing) View.VISIBLE else View.GONE
    }

    // --- WebView local playback ---
    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebView() {
        webView.visibility = View.VISIBLE
        controlsLayout.visibility = View.GONE
        // YouTube IFrame API requires HTTP, can't run from file://
        val settings = buildSettingsJson()
        webView.loadUrl("https://ompster.github.io/ChannelSurf/")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Set settings and auto-start
                val js = """
                    localStorage.setItem('channelsurf', JSON.stringify($settings));
                    setTimeout(() => document.getElementById('btn-start')?.click(), 1000);
                """.trimIndent()
                webView.evaluateJavascript(js, null)
            }
        }
    }

    // --- YouTube Sign In ---
    private fun onYouTubeSignIn() {
        webView.visibility = View.VISIBLE
        controlsLayout.visibility = View.GONE
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // If we've landed back on YouTube or our app, sign-in is done
                if (url?.contains("youtube.com") == true && !url.contains("accounts.google.com")) {
                    checkYouTubeSignIn()
                    // Go back to controls after a moment
                    webView.postDelayed({
                        webView.visibility = View.GONE
                        controlsLayout.visibility = View.VISIBLE
                    }, 1500)
                }
            }
        }
        webView.loadUrl("https://accounts.google.com/ServiceLogin?continue=https://www.youtube.com/")
    }

    private fun checkYouTubeSignIn() {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://www.youtube.com/") ?: ""
        val signedIn = cookies.contains("SID=") || cookies.contains("LOGIN_INFO=")
        if (signedIn) {
            signInStatus.text = "✓ YouTube signed in (Premium active)"
            signInStatus.setTextColor(0xFF4CAF50.toInt())
            btnSignIn.text = "Sign Out"
            btnSignIn.setOnClickListener {
                cookieManager.removeAllCookies(null)
                signInStatus.text = "Not signed in — ads will play"
                signInStatus.setTextColor(0xFF888888.toInt())
                btnSignIn.text = "Sign into YouTube"
                btnSignIn.setOnClickListener { onYouTubeSignIn() }
            }
        } else {
            signInStatus.text = "Not signed in — ads will play"
            signInStatus.setTextColor(0xFF888888.toInt())
            btnSignIn.text = "Sign into YouTube"
        }
    }

    // --- Cast messaging ---
    private fun sendCastMessage(json: String) {
        try {
            castSession?.sendMessage(NAMESPACE, json)
        } catch (e: Exception) {
            castStatus.text = "Send failed: ${e.message}"
        }
    }

    private fun buildStartMessage(): String {
        val settings = JSONObject().apply {
            put("playlistId", playlistInput.text.toString().trim())
            put("clipLength", clipSlider.value.toInt())
            put("fadeLength", fadeSlider.value.toDouble())
            put("transition", transitionValues[transitionSpinner.selectedItemPosition])
            put("crtMode", crtSwitch.isChecked)
            put("showChannel", channelSwitch.isChecked)
            put("showTitle", titleSwitch.isChecked)
        }
        return JSONObject().apply {
            put("type", "START")
            put("settings", settings)
        }.toString()
    }

    private fun buildSettingsJson(): String {
        return JSONObject().apply {
            put("playlistId", playlistInput.text.toString().trim())
            put("clipLength", clipSlider.value.toInt())
            put("fadeLength", fadeSlider.value.toDouble())
            put("transition", transitionValues[transitionSpinner.selectedItemPosition])
            put("crtMode", crtSwitch.isChecked)
            put("showChannel", channelSwitch.isChecked)
            put("showTitle", titleSwitch.isChecked)
            put("autoHide", true)
        }.toString()
    }

    // --- Settings persistence ---
    private fun saveSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().apply {
            putString("playlistId", playlistInput.text.toString())
            putFloat("clipLength", clipSlider.value)
            putInt("transition", transitionSpinner.selectedItemPosition)
            putFloat("fadeLength", fadeSlider.value)
            putBoolean("crtMode", crtSwitch.isChecked)
            putBoolean("showChannel", channelSwitch.isChecked)
            putBoolean("showTitle", titleSwitch.isChecked)
            apply()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        playlistInput.setText(prefs.getString("playlistId", "PLCgY5X6keprecXzwA9jsi92DhiNZ-_KoY"))
        clipSlider.value = prefs.getFloat("clipLength", 45f)
        clipValue.text = "${clipSlider.value.toInt()}s"
        transitionSpinner.setSelection(prefs.getInt("transition", 9)) // Random default
        fadeSlider.value = prefs.getFloat("fadeLength", 1.5f)
        fadeValue.text = "${String.format("%.1f", fadeSlider.value)}s"
        crtSwitch.isChecked = prefs.getBoolean("crtMode", false)
        channelSwitch.isChecked = prefs.getBoolean("showChannel", true)
        titleSwitch.isChecked = prefs.getBoolean("showTitle", true)
    }
}
