package com.custom.intervalalarm

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.custom.intervalalarm.databinding.ActivityAlarmRingingBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlarmRingingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmRingingBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var planId: String = ""
    private var planTitle: String = ""
    private var snoozeMinutes: Int = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure activity wakes up the device & shows over lock screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        binding = ActivityAlarmRingingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        planId = intent.getStringExtra("PLAN_ID") ?: ""
        planTitle = intent.getStringExtra("PLAN_TITLE") ?: "Interval Alarm"
        binding.tvAlarmTitle.text = planTitle

        // Find plan preferences
        val plan = StorageManager(this).getPlans().find { it.id == planId }
        snoozeMinutes = plan?.snoozeMinutes ?: 10
        binding.btnSnooze.text = "Snooze ($snoozeMinutes min)"

        // Update clock readout
        updateClockReadout()

        // Start pulsing animation on the alarm icon
        startPulseAnimation()

        // Start sound & vibration
        startAlarmFeedback(plan)

        binding.btnDismiss.setOnClickListener {
            stopAlarmFeedback()
            finish()
        }

        binding.btnSnooze.setOnClickListener {
            stopAlarmFeedback()
            AlarmScheduler.scheduleSnooze(this, planId, planTitle, snoozeMinutes)
            finish()
        }
    }

    private fun updateClockReadout() {
        val now = Date()
        val timeFormat = SimpleDateFormat("hh:mm", Locale.getDefault())
        val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
        binding.tvCurrentClock.text = timeFormat.format(now)
        binding.tvAmPm.text = amPmFormat.format(now)
    }

    private fun startPulseAnimation() {
        val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
            binding.ivAlarmIcon,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1.15f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.15f)
        ).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        scaleDown.start()
    }

    private fun startAlarmFeedback(plan: AlarmPlan?) {
        // Sound playback
        try {
            var soundUri: Uri? = null
            if (plan?.ringtoneUri != null) {
                soundUri = Uri.parse(plan.ringtoneUri)
            }
            if (soundUri == null) {
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }

            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, soundUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                if (plan?.gradualVolume == true) {
                    setVolume(0.1f, 0.1f)
                    rampUpVolume()
                } else {
                    setVolume(1.0f, 1.0f)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Vibration
        if (plan == null || plan.vibrate) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                    vm.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                val pattern = when (plan?.vibrationPattern) {
                    "Heartbeat" -> longArrayOf(0, 200, 150, 200, 800)
                    "Urgent" -> longArrayOf(0, 500, 200, 500, 200)
                    else -> longArrayOf(0, 1000, 800)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun rampUpVolume() {
        var currentVol = 0.1f
        val interval = 1000L // every second
        val step = 0.05f
        val runnable = object : Runnable {
            override fun run() {
                if (mediaPlayer != null && currentVol < 1.0f) {
                    currentVol = (currentVol + step).coerceAtMost(1.0f)
                    mediaPlayer?.setVolume(currentVol, currentVol)
                    handler.postDelayed(this, interval)
                }
            }
        }
        handler.postDelayed(runnable, interval)
    }

    private fun stopAlarmFeedback() {
        handler.removeCallbacksAndMessages(null)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vibrator?.cancel()
            vibrator = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        stopAlarmFeedback()
        super.onDestroy()
    }
}
