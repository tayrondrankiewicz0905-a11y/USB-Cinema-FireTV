package de.usbcinema.firetv

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.text.InputType
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class Movie(val name: String, val path: String, val url: String)

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var root: LinearLayout
    private lateinit var address: EditText
    private lateinit var status: TextView
    private lateinit var list: ListView
    private var player: ExoPlayer? = null
    private var movies: List<Movie> = emptyList()
    private var server = "http://192.168.178.147:8765"

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(Color.rgb(16, 19, 28))

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(12))
            isFocusableInTouchMode = true
        }
        val title = TextView(this).apply {
            text = "USB CINEMA"
            textSize = 28f
            setTextColor(Color.rgb(98, 213, 250))
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(48)))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        address = EditText(this).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Server-IP, z. B. 192.168.178.147:8765"
            setText("192.168.178.147:8765")
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 16f
            selectAll()
        }
        row.addView(address, LinearLayout.LayoutParams(0, dp(54), 1f))
        val connect = Button(this).apply {
            text = "Verbinden"
            setOnClickListener {
                val entered = address.text.toString().trim()
                server = if (entered.startsWith("http://") || entered.startsWith("https://"))
                    entered.trimEnd('/') else "http://${entered.trimEnd('/')}"
                loadMovies()
            }
        }
        row.addView(connect, LinearLayout.LayoutParams(-2, dp(54)))
        val search = Button(this).apply {
            text = "Server suchen"
            setOnClickListener { discoverServer() }
        }
        row.addView(search, LinearLayout.LayoutParams(-2, dp(54)))
        root.addView(row)

        status = TextView(this).apply {
            text = "Bereit. Verbinde dich mit deinem USB-Cinema-Server."
            textSize = 16f
            setTextColor(Color.LTGRAY)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        list = ListView(this).apply {
            divider = null
            choiceMode = ListView.CHOICE_MODE_SINGLE
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
            setOnItemClickListener { _, _, position, _ ->
                status.text = "Film angeklickt: $position"
                if (position in movies.indices) {
                    playMovie(movies[position])
                } else {
                    status.text = "Ungültige Film-Auswahl: $position"
                }
            }
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        loadMovies()
        list.post { list.requestFocus() }
    }

    private fun getText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2500
        connection.readTimeout = 4000
        connection.requestMethod = "GET"
        return try {
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun loadMovies() {
        status.text = "Verbinde mit $server ..."
        worker.execute {
            try {
                val health = JSONObject(getText("$server/api/health"))
                if (health.optString("status") != "online") error("Server nicht online")
                val json = JSONObject(getText("$server/api/movies"))
                val array = json.optJSONArray("movies") ?: JSONArray()
                val found = (0 until array.length()).mapNotNull { i ->
                    val item = array.optJSONObject(i) ?: return@mapNotNull null
                    val name = item.optString("name")
                    val path = item.optString("path")
                    val videoUrl = item.optString("url")
                    if (name.isBlank() || path.isBlank() || videoUrl.isBlank()) null
                    else Movie(name, path, videoUrl)
                }
                runOnUiThread {
                    movies = found
                    list.adapter = object : ArrayAdapter<Movie>(
                        this, android.R.layout.simple_list_item_1, movies
                    ) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getView(position, convertView, parent) as TextView
                            v.text = "▶   ${getItem(position)?.name ?: ""}"
                            v.textSize = 19f
                            v.setTextColor(Color.WHITE)
                            v.setPadding(dp(12), dp(12), dp(12), dp(12))
                            v.setBackgroundColor(if (position % 2 == 0)
                                Color.rgb(29, 34, 47) else Color.rgb(21, 25, 36))
                            v.isFocusable = true
                            return v
                        }
                    }
                    status.text = if (found.isEmpty())
                        "Server online, aber keine Filme gefunden. Ist der USB-Stick angeschlossen?"
                    else "${found.size} Film(e) gefunden. Mit OK auswählen und abspielen."
                }
            } catch (e: Exception) {
                runOnUiThread {
                    movies = emptyList()
                    list.adapter = null
                    status.text = "Server nicht erreichbar. USB-Stick, Server und IP prüfen. (${e.message ?: "Verbindungsfehler"})"
                }
            }
        }
    }

    private fun discoverServer() {
        status.text = "Suche im Heimnetz ... (du kannst auch die IP manuell eingeben)"
        worker.execute {
            val candidates = linkedSetOf(
                "192.168.178.147:8765",
                "192.168.1.1:8765",
                "192.168.0.1:8765"
            )
            var found: String? = null
            for (candidate in candidates) {
                try {
                    val base = "http://$candidate"
                    val health = JSONObject(getText("$base/api/health"))
                    if (health.optString("status") == "online") {
                        found = base
                        break
                    }
                } catch (_: Exception) { }
            }
            runOnUiThread {
                if (found != null) {
                    server = found!!
                    address.setText(server.removePrefix("http://"))
                    status.text = "USB Cinema gefunden: $server"
                    loadMovies()
                } else {
                    status.text = "Automatische Suche hat nichts gefunden. Bitte Server-IP eingeben und Verbinden drücken."
                    address.requestFocus()
                }
            }
        }
    }

    private fun playMovie(movie: Movie) {
        try {
            val encodedPath = movie.path.split("/").joinToString("/") {
                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
            }
            val url = "$server/video/$encodedPath"
            val playerView = PlayerView(this).apply {
                useController = true
                player = this@MainActivity.player
                setBackgroundColor(Color.BLACK)
            }
            root.removeAllViews()
            root.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
            val back = Button(this).apply {
                text = "← Zurück zur Filmliste"
                setOnClickListener { showLibrary() }
            }
            root.addView(back)
            player?.release()
            player = ExoPlayer.Builder(this).build().also {
                playerView.player = it
                it.setMediaItem(MediaItem.fromUri(url))
                it.prepare()
                it.playWhenReady = true
            }
            playerView.requestFocus()
        } catch (e: Exception) {
            status.text = "Film konnte nicht gestartet werden: ${e.message}"
        }
    }

    private fun showLibrary() {
        player?.release()
        player = null
        root.removeAllViews()
        val title = TextView(this).apply {
            text = "USB CINEMA"
            textSize = 28f
            setTextColor(Color.rgb(98, 213, 250))
        }
        root.addView(title, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(status)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        loadMovies()
        list.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && player != null) {
            showLibrary()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        player?.release()
        worker.shutdownNow()
        super.onDestroy()
    }
}
