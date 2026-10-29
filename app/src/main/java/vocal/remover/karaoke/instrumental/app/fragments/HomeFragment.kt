package vocal.remover.karaoke.instrumental.app.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import vocal.remover.karaoke.instrumental.app.*
import vocal.remover.karaoke.instrumental.app.activities.DownloadListActivity
import vocal.remover.karaoke.instrumental.app.databinding.FragmentHomeBinding
import vocal.remover.karaoke.instrumental.app.utils_java.AppUtils.showCustomDialog
import java.io.File
import java.io.FileOutputStream

public class HomeFragment : Fragment() {
    lateinit var binding: FragmentHomeBinding
    private var selectedMp3Uri: Uri? = null
    val dialog = CustomProgressDialog.getInstance();
    var mp: MediaPlayer? = null
    private var totalTime: Int = 0
    lateinit var r: Runnable

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view: View = binding.root

        binding.btnSelectMp3.setOnClickListener { selectMp3File() }
        binding.btnExtractMp3.setOnClickListener { uploadMp3() }
        binding.btnPlay.setOnClickListener { playSelectedSong() }
        binding.btnDownloadPage.setOnClickListener {
           pauseMpPlayer()
            startActivity(Intent(activity, DownloadListActivity::class.java))
        }

        return view
    }

    private fun pauseMpPlayer() {
        if (mp != null) {
            if (mp!!.isPlaying) {
                mp?.pause()
                binding.btnPlay.setBackgroundResource(R.drawable.ic_baseline_play_circle_filled_24)
            }
        }
    }

    private fun playSelectedSong() {
        val player = mp
        if (player == null) {
            Toast.makeText(activity, "A prévia deste formato não está disponível neste aparelho. O arquivo ainda pode ser processado.", Toast.LENGTH_LONG).show()
            return
        }

        if (player.isPlaying) {
            //pause
            mp?.pause()
            binding.btnPlay.setBackgroundResource(R.drawable.ic_baseline_play_circle_filled_24)
        } else {
            //start
            mp?.start()
            binding.btnPlay.setBackgroundResource(R.drawable.ic_baseline_pause_circle_filled_24)
        }

    }

    private fun stopAndReleaseMediaPlayer() {
        if (this::r.isInitialized) handler.removeCallbacks(r)
        val player = mp
        mp = null
        if (player != null) {
            try { if (player.isPlaying) player.stop() } catch (_: IllegalStateException) { }
            try { player.release() } catch (_: Exception) { }
        }
    }

    override fun onDestroyView() {
        stopAndReleaseMediaPlayer()
        super.onDestroyView()
    }

    private fun selectMp3File() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "audio/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_MP3)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("TAG", "onActivityResult: ghgff")
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_PICK_MP3 -> {
                    selectedMp3Uri = data?.data
                    binding.tvFileName.text = getMp3FileName(selectedMp3Uri)
                    binding.btnExtractMp3.visibility = View.VISIBLE
                    binding.tvFileName.visibility = View.VISIBLE
                    binding.tvFileNameTitle.visibility = View.VISIBLE
                    binding.lyMp3Details.visibility = View.VISIBLE

                    initMediaPlayer(selectedMp3Uri)
                    playSelectedSong()
                }
            }
        }

    }

    private fun initMediaPlayer(selectedMp3Uri: Uri?) {
        stopPlaying()
        mp = try {
            MediaPlayer.create(activity, selectedMp3Uri)
        } catch (_: Exception) {
            null
        }
        val player = mp
        if (player == null) {
            binding.playerSeekbar.max = 0
            binding.btnPlay.isEnabled = false
            return
        }
        binding.btnPlay.isEnabled = true
        player.setVolume(0.5f, 0.5f)
        totalTime = player.duration

        binding.playerSeekbar.max = totalTime
        binding.playerSeekbar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            mp?.seekTo(progress)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {

                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {

                    }

                }
        )

        r = Runnable {
            while (mp != null) {
                try {
                    var msg = Message()
                    msg.what = mp?.currentPosition!!
                    handler.sendMessage(msg)
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    Log.e("TAG", "initMediaPlayer: " + e.message)
                }
            }
        }

        Thread(r).start()

    }

    @SuppressLint("HandlerLeak")
    var handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            var currentPosition = msg.what

            //update the seekbar
            binding.playerSeekbar.progress = currentPosition
        }
    }

    private fun uploadMp3() {
        val uri = selectedMp3Uri
        if (uri == null) {
            binding.layoutRoot.snackbar("Selecione primeiro um arquivo de áudio")
            return
        }

        val resolver = activity?.contentResolver ?: return
        val displayName = resolver.getFileName(uri).ifBlank { "audio" }
        val extension = displayName.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
        val supported = setOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "opus")
        if (extension !in supported) {
            showCustomDialog(activity, "Formato não reconhecido. Use MP3, WAV, FLAC, M4A, AAC, OGG ou OPUS.")
            return
        }

        val workDir = File(requireContext().cacheDir, "local-separation").apply { mkdirs() }
        val inputFile = File(workDir, "source_input.$extension")
        val wavFile = File(workDir, "source.wav")
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(inputFile).use { output -> input.copyTo(output) }
            } ?: run {
                binding.layoutRoot.snackbar("Não foi possível ler o arquivo selecionado")
                return
            }
        } catch (e: Exception) {
            showCustomDialog(activity, "Não foi possível abrir o arquivo: ${e.message}")
            return
        }

        dialog.ShowProgress(activity, "Preparando áudio para a separação local…", false)
        // Float PCM avoids an extra integer quantization step. The model runtime resamples internally as needed.
        val command = "-y -i ${quoteFfmpegPath(inputFile.absolutePath)} -map 0:a:0 -vn -ac 2 -ar 44100 -c:a pcm_f32le -f wav ${quoteFfmpegPath(wavFile.absolutePath)}"
        com.arthenica.ffmpegkit.FFmpegKit.executeAsync(command) { session ->
            activity?.runOnUiThread {
                dialog.hideProgress()
                inputFile.delete()
                if (com.arthenica.ffmpegkit.ReturnCode.isSuccess(session.returnCode) && wavFile.isFile && wavFile.length() > 44) {
                    val intent = Intent(requireContext(), vocal.remover.karaoke.instrumental.app.activities.LocalSeparatorActivity::class.java).apply {
                        putExtra(vocal.remover.karaoke.instrumental.app.activities.LocalSeparatorActivity.EXTRA_WAV_PATH, wavFile.absolutePath)
                        putExtra(vocal.remover.karaoke.instrumental.app.activities.LocalSeparatorActivity.EXTRA_DISPLAY_NAME, displayName)
                    }
                    startActivity(intent)
                } else {
                    showCustomDialog(activity, "Não foi possível preparar o áudio. Tente outro arquivo com uma faixa de áudio válida.")
                }
            }
        }
    }

    private fun quoteFfmpegPath(path: String): String = "'$path'"

    private fun getMp3FileName(selectedMp3Uri: Uri?): String? {
        val file = File(activity?.cacheDir, activity?.contentResolver?.getFileName(selectedMp3Uri!!))
        return file.name
    }

    companion object {
        const val REQUEST_CODE_PICK_MP3 = 101
        private const val TAG = "HomeFragment"
    }

    fun showTimeOutDialog(msg: String?) {
        val host = activity ?: return
        val timeoutDialog = Dialog(host)
        timeoutDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        timeoutDialog.setCancelable(true)
        timeoutDialog.setContentView(R.layout.custom_dialog)
        timeoutDialog.findViewById<TextView>(R.id.text_dialog)?.text = msg
        val dialogButton = timeoutDialog.findViewById<Button>(R.id.btn_dialog)
        dialogButton.setText("Retry")
        dialogButton.setOnClickListener {
            timeoutDialog.dismiss()
            uploadMp3()
        }
        timeoutDialog.show()
    }

    private fun stopPlaying() {
        if (mp != null) {
            mp?.stop()
            mp?.release()
            mp = null
        }
    }
}
