package com.ompster.channelsurf

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
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
    private lateinit var webView: WebView
    private lateinit var controlsLayout: View
    private lateinit var castStatus: TextView

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

        // WebView setup (local fallback)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()

        // Buttons
        btnStart.setOnClickListener { onStart() }
        btnSkip.setOnClickListener { onSkip() }
        btnStop.setOnClickListener { onStop() }

        // Load saved settings
        loadSettings()

        // Cast
        try {
            castContext = CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            castStatus.text = "Cast unavailable"
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
    private fun onStart() {
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

    private fun onStop() {
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
        // Inject settings into localStorage before loading
        val settings = buildSettingsJson()
        webView.loadUrl("file:///android_asset/index.html")
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
