package vocal.remover.karaoke.instrumental.app.activities

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Runs UVR-compatible MDX-Net separation in the WebView on the device.
 * The audio itself is served from app-private cache; only the model/runtime are downloaded.
 */
class LocalSeparatorActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var sourceWav: File
    private lateinit var displayName: String
    private lateinit var status: TextView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_WAV_PATH)
        if (path.isNullOrBlank()) {
            Toast.makeText(this, "Arquivo de áudio não encontrado.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        sourceWav = File(path)
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty()
        if (!sourceWav.isFile) {
            Toast.makeText(this, "O WAV temporário não existe mais. Selecione o áudio novamente.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 18, 24))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val back = Button(this).apply {
            text = "Voltar"
            setOnClickListener { finish() }
        }
        status = TextView(this).apply {
            text = "Preparando separação local…"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(12), 0, 0, 0)
        }
        top.addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        top.addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        webView.addJavascriptInterface(LocalFileBridge(this, displayName) { message ->
            runOnUiThread {
                status.text = message
                if (message.startsWith("Salvo:")) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }, "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url ?: return null
                if (url.host == "appassets.androidplatform.net" && url.path == "/input/source.wav") {
                    return try {
                        WebResourceResponse("audio/wav", null, FileInputStream(sourceWav))
                    } catch (_: Exception) {
                        WebResourceResponse("text/plain", "UTF-8", "Não foi possível abrir o áudio".byteInputStream())
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        val html = assets.open("local_separator.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        webView.loadDataWithBaseURL("https://appassets.androidplatform.net/", html, "text/html", "UTF-8", null)
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.removeJavascriptInterface("AndroidBridge")
            webView.stopLoading()
            webView.destroy()
        }
        if (::sourceWav.isInitialized) sourceWav.delete()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_WAV_PATH = "local_separator_wav_path"
        const val EXTRA_DISPLAY_NAME = "local_separator_display_name"
    }
}

private class LocalFileBridge(
    private val context: Context,
    private val sourceName: String,
    private val notify: (String) -> Unit
) {
    private val tempDir = File(context.cacheDir, "local-separation/exports").apply { mkdirs() }
    private val activeWrites = ConcurrentHashMap<String, File>()
    private val lock = Any()

    @JavascriptInterface
    fun getDisplayName(): String = sourceName.substringBeforeLast('.', sourceName).ifBlank { "audio" }
        .replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(80)

    @JavascriptInterface
    fun beginSave(name: String) {
        val safeName = safeName(name)
        synchronized(lock) {
            val file = File(tempDir, safeName)
            if (file.exists()) file.delete()
            file.createNewFile()
            activeWrites[safeName] = file
        }
    }

    @JavascriptInterface
    fun writeChunk(name: String, base64: String) {
        val safeName = safeName(name)
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        synchronized(lock) {
            val file = activeWrites[safeName] ?: throw IllegalStateException("Exportação não iniciada")
            FileOutputStream(file, true).use { it.write(bytes) }
        }
    }

    @JavascriptInterface
    fun finishSave(name: String) {
        val safeName = safeName(name)
        val file = synchronized(lock) { activeWrites.remove(safeName) }
        if (file == null || !file.isFile) {
            notify("Erro ao salvar $safeName")
            return
        }
        try {
            val savedPath = saveToMusic(file, safeName)
            file.delete()
            notify("Salvo: $savedPath")
        } catch (e: Exception) {
            notify("Erro ao salvar: ${e.localizedMessage ?: "falha de armazenamento"}")
        }
    }

    @JavascriptInterface
    fun finishSaveAsFlac(wavName: String, flacName: String) {
        val safeWavName = safeName(wavName)
        val safeFlacName = safeName(flacName).substringBeforeLast('.', safeName(flacName)) + ".flac"
        val wavFile = synchronized(lock) { activeWrites.remove(safeWavName) }
        if (wavFile == null || !wavFile.isFile) {
            notify("Erro: WAV temporário não encontrado para conversão FLAC")
            return
        }
        val flacFile = File(tempDir, safeFlacName)
        val command = "-y -i '${wavFile.absolutePath}' -map 0:a:0 -vn -c:a flac -compression_level 8 '${flacFile.absolutePath}'"
        try {
            // JavaScript bridge calls run off the UI thread; wait here so the UI can confirm completion.
            val session = com.arthenica.ffmpegkit.FFmpegKit.execute(command)
            if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && flacFile.isFile && flacFile.length() > 0) {
                val savedPath = saveToMusic(flacFile, safeFlacName, "audio/flac")
                notify("Salvo em FLAC: $savedPath")
            } else {
                notify("Não foi possível converter para FLAC. Tente salvar como WAV.")
            }
        } catch (e: Exception) {
            notify("Erro ao salvar FLAC: ${e.localizedMessage ?: "falha de armazenamento"}")
        } finally {
            wavFile.delete()
            flacFile.delete()
        }
    }

    @JavascriptInterface
    fun reportStatus(message: String) {
        notify(message.take(180))
    }

    private fun saveToMusic(source: File, name: String, mimeType: String = "audio/wav"): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AI Vocal Remover")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val uri: Uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Não foi possível criar o arquivo no armazenamento")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output) }
                } ?: throw IllegalStateException("Não foi possível escrever o arquivo")
                val ready = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, ready, null, null)
                return "Música/AI Vocal Remover/$name"
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        }
        val folder = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "AI Vocal Remover").apply { mkdirs() }
        val outputFile = File(folder, name)
        FileInputStream(source).use { input -> FileOutputStream(outputFile).use { output -> input.copyTo(output) } }
        return outputFile.absolutePath
    }

    private fun safeName(name: String): String = name.substringAfterLast('/').substringAfterLast('\\')
        .replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(100).ifBlank { "stem.wav" }
}
