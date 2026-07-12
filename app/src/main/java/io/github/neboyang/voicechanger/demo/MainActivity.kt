package io.github.neboyang.voicechanger.demo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import io.github.neboyang.voicechanger.RealtimeVoiceChanger
import io.github.neboyang.voicechanger.VoiceChanger
import io.github.neboyang.voicechanger.VoiceEffect
import io.github.neboyang.voicechanger.VoicePreset
import io.github.neboyang.voicechanger.VoiceRecorder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var changer: VoiceChanger
    private val realtime = RealtimeVoiceChanger()

    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvPitch: TextView
    private lateinit var tvTempo: TextView
    private lateinit var tvRate: TextView
    private lateinit var amplitudeBar: LinearProgressIndicator
    private lateinit var processProgress: LinearProgressIndicator
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnProcess: MaterialButton
    private lateinit var btnRealtime: MaterialButton
    private lateinit var sliderPitch: Slider
    private lateinit var sliderTempo: Slider
    private lateinit var sliderRate: Slider

    private var hasRecording = false

    /** 权限批准后要执行的动作（录音或实时变声）。 */
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingAction?.invoke()
            else Toast.makeText(this, R.string.toast_permission, Toast.LENGTH_LONG).show()
            pendingAction = null
        }

    private fun withRecordPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        changer = VoiceChanger(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)
        tvPitch = findViewById(R.id.tvPitch)
        tvTempo = findViewById(R.id.tvTempo)
        tvRate = findViewById(R.id.tvRate)
        amplitudeBar = findViewById(R.id.amplitudeBar)
        processProgress = findViewById(R.id.processProgress)
        btnRecord = findViewById(R.id.btnRecord)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        btnProcess = findViewById(R.id.btnProcess)
        btnRealtime = findViewById(R.id.btnRealtime)
        sliderPitch = findViewById(R.id.sliderPitch)
        sliderTempo = findViewById(R.id.sliderTempo)
        sliderRate = findViewById(R.id.sliderRate)

        setupEffectChips()
        setupSliders()

        btnRecord.setOnClickListener { withRecordPermission { startRecording() } }

        btnPause.setOnClickListener {
            when (changer.recorder.state.value) {
                VoiceRecorder.State.RECORDING -> changer.pauseRecording()
                VoiceRecorder.State.PAUSED -> changer.resumeRecording()
                else -> Unit
            }
        }

        btnStop.setOnClickListener { stopRecording() }

        btnProcess.setOnClickListener { processAndPlay() }

        btnRealtime.setOnClickListener {
            if (realtime.isRunning.value) realtime.stop()
            else withRecordPermission { startRealtime() }
        }

        realtime.onError = { t ->
            runOnUiThread { Toast.makeText(this, t.message, Toast.LENGTH_LONG).show() }
        }

        observeRecorder()
        observeRealtime()
    }

    private fun startRealtime() {
        if (changer.recorder.state.value != VoiceRecorder.State.IDLE) return
        changer.stopPlaying()
        realtime.pitchSemiTones = sliderPitch.value
        runCatching { realtime.start() }
            .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
    }

    private fun observeRealtime() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    realtime.isRunning.collect { running ->
                        btnRealtime.setText(
                            if (running) R.string.btn_realtime_stop else R.string.btn_realtime_start)
                        btnRecord.isEnabled = !running
                        btnProcess.isEnabled = !running && hasRecording
                        if (running) tvStatus.setText(R.string.status_realtime)
                        else if (changer.recorder.state.value == VoiceRecorder.State.IDLE)
                            tvStatus.setText(R.string.status_idle)
                    }
                }
                launch {
                    realtime.amplitude.collect { amp ->
                        if (realtime.isRunning.value) amplitudeBar.progress = (amp * 100).toInt()
                    }
                }
            }
        }
    }

    private fun setupEffectChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroup)
        VoicePreset.entries.forEach { preset ->
            chipGroup.addView(Chip(this).apply {
                text = getString(presetName(preset))
                isCheckable = true
                setOnClickListener {
                    val effect = preset.effect
                    sliderPitch.value = effect.pitchSemiTones
                    sliderTempo.value = effect.tempo
                    sliderRate.value = effect.rate
                }
            })
        }
        (chipGroup.getChildAt(0) as Chip).isChecked = true
    }

    private fun presetName(preset: VoicePreset): Int = when (preset) {
        VoicePreset.NONE -> R.string.preset_none
        VoicePreset.KITTY -> R.string.preset_kitty
        VoicePreset.ROSE -> R.string.preset_rose
        VoicePreset.WOMAN -> R.string.preset_woman
        VoicePreset.UNCLE -> R.string.preset_uncle
        VoicePreset.MAN -> R.string.preset_man
        VoicePreset.TOM -> R.string.preset_tom
    }

    private fun setupSliders() {
        val update = {
            tvPitch.text = getString(R.string.label_pitch, sliderPitch.value)
            tvTempo.text = getString(R.string.label_tempo, sliderTempo.value)
            tvRate.text = getString(R.string.label_rate, sliderRate.value)
        }
        listOf(sliderPitch, sliderTempo, sliderRate).forEach { slider ->
            slider.addOnChangeListener { _, _, _ -> update() }
        }
        // 实时变声运行中，音调滑杆即时生效
        sliderPitch.addOnChangeListener { _, value, _ ->
            if (realtime.isRunning.value) realtime.pitchSemiTones = value
        }
        update()
    }

    private fun observeRecorder() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    changer.recorder.state.collect { state ->
                        when (state) {
                            VoiceRecorder.State.IDLE -> {
                                btnRecord.isEnabled = !realtime.isRunning.value
                                btnPause.isEnabled = false
                                btnStop.isEnabled = false
                                btnRealtime.isEnabled = true
                                btnPause.setText(R.string.btn_pause)
                            }
                            VoiceRecorder.State.RECORDING -> {
                                tvStatus.setText(R.string.status_recording)
                                btnRecord.isEnabled = false
                                btnPause.isEnabled = true
                                btnStop.isEnabled = true
                                btnRealtime.isEnabled = false
                                btnPause.setText(R.string.btn_pause)
                            }
                            VoiceRecorder.State.PAUSED -> {
                                tvStatus.setText(R.string.status_paused)
                                btnPause.setText(R.string.btn_resume)
                            }
                        }
                    }
                }
                launch {
                    changer.recorder.amplitude.collect { amp ->
                        amplitudeBar.progress = (amp * 100).toInt()
                    }
                }
            }
        }
    }

    private fun startRecording() {
        changer.stopPlaying()
        runCatching { changer.startRecording() }
            .onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
    }

    private fun stopRecording() {
        btnStop.isEnabled = false
        lifecycleScope.launch {
            runCatching { changer.stopRecording() }
                .onSuccess { result ->
                    hasRecording = true
                    btnProcess.isEnabled = true
                    tvStatus.text = "已录制 %.1f 秒".format(result.durationMs / 1000f)
                }
                .onFailure { Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show() }
        }
    }

    private fun processAndPlay() {
        if (!hasRecording) return
        val effect = runCatching {
            VoiceEffect(
                pitchSemiTones = sliderPitch.value,
                tempo = sliderTempo.value,
                rate = sliderRate.value,
            )
        }.getOrElse {
            Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
            return
        }

        btnProcess.isEnabled = false
        processProgress.visibility = android.view.View.VISIBLE
        tvStatus.setText(R.string.status_processing)
        changer.stopPlaying()

        lifecycleScope.launch {
            runCatching {
                changer.changeVoice(effect) { p ->
                    processProgress.post { processProgress.progress = (p * 100).toInt() }
                }
            }.onSuccess { file ->
                tvResult.text = getString(R.string.result_path, file.absolutePath)
                tvStatus.setText(R.string.status_playing)
                changer.play(file) { tvStatus.setText(R.string.status_done) }
            }.onFailure {
                Toast.makeText(this@MainActivity, it.message, Toast.LENGTH_LONG).show()
                tvStatus.setText(R.string.status_idle)
            }
            btnProcess.isEnabled = true
            processProgress.visibility = android.view.View.INVISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realtime.stop()
        changer.release()
    }
}
