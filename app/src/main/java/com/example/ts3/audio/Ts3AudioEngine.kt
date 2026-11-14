package com.example.ts3.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import com.github.manevolent.ts3j.audio.Microphone
import com.github.manevolent.ts3j.enums.CodecType
import java.util.concurrent.LinkedBlockingQueue

/**
 * 双向语音引擎：AudioRecord 采集 → Opus 编码 → 喂给 ts3j（实现 [Microphone]）；
 * ts3j 收到的语音包 → Opus 解码 → AudioTrack 播放。
 * 采集/播放各占一条高优先级线程；PTT 按下时才算「准备就绪」。
 */
class Ts3AudioEngine(private val appContext: Context) : Microphone {

    private val encoder = NativeOpusEncoder()
    private val decoder = NativeOpusDecoder()

    @Volatile private var micMuted = false
    @Volatile private var pttPressed = false
    @Volatile private var speakerMuted = false

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private val playbackQueue = LinkedBlockingQueue<ShortArray>(PLAYBACK_QUEUE_CAPACITY)

    @Volatile private var running = false
    @Volatile private var latestFrame: ByteArray? = null
    private var capturedFrames = 0
    private var providedFrames = 0
    private var receivedFrames = 0

    // ---- ts3j Microphone ----

    override fun isReady(): Boolean = !micMuted && pttPressed

    override fun getCodec(): CodecType = CodecType.OPUS_VOICE

    override fun provide(): ByteArray {
        val frame = latestFrame
        latestFrame = null
        if (frame != null && frame.isNotEmpty()) {
            providedFrames++
            if (providedFrames % 50 == 1) {
                Log.d(TAG, "语音帧已发送给 ts3j（第 $providedFrames 帧，${frame.size}B）")
            }
        }
        return frame ?: EMPTY_FRAME
    }

    // ---- 状态控制 ----

    fun setMicMuted(muted: Boolean) {
        micMuted = muted
        if (muted) latestFrame = null
    }

    fun setSpeakerMuted(muted: Boolean) {
        speakerMuted = muted
    }

    fun setPtt(pressed: Boolean) {
        pttPressed = pressed
        if (!pressed) latestFrame = null
    }

    // ---- 生命周期 ----

    @Synchronized
    fun start() {
        if (running) return
        running = true
        latestFrame = null
        playbackQueue.clear()
        setCommunicationMode(true)
        startCapture()
        startPlayback()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        stopCapture()
        stopPlayback()
        setCommunicationMode(false)
    }

    /** 收到远端语音包（ts3j 网络线程回调）。 */
    fun onVoicePacket(codec: CodecType, data: ByteArray) {
        if (!running || speakerMuted) return
        if (codec != CodecType.OPUS_VOICE && codec != CodecType.OPUS_MUSIC) return
        receivedFrames++
        if (receivedFrames % 50 == 1) {
            Log.d(TAG, "收到远端语音包（第 $receivedFrames 包，${data.size}B）")
        }
        try {
            val pcm = decoder.decode(data, FRAME_SAMPLES)
            if (pcm.isNotEmpty()) playbackQueue.offer(pcm)
        } catch (e: Exception) {
            Log.w(TAG, "Opus 解码失败", e)
        }
    }

    // ---- 采集 ----

    private fun startCapture() {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf * 2, FRAME_SAMPLES * 2 * 2),
        )
        record.startRecording()
        audioRecord = record
        captureThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val buffer = ShortArray(FRAME_SAMPLES)
            while (running) {
                val read = record.read(buffer, 0, FRAME_SAMPLES)
                if (read != FRAME_SAMPLES) continue
                try {
                    latestFrame = encoder.encode(buffer)
                    capturedFrames++
                    if (capturedFrames % 50 == 1) {
                        Log.d(TAG, "采集+编码正常（第 $capturedFrames 帧，${latestFrame?.size ?: 0}B）")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Opus 编码失败", e)
                }
            }
        }, "ts3-capture").apply { start() }
    }

    // ---- 播放 ----

    private fun startPlayback() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf * 2, FRAME_SAMPLES * 2 * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        audioTrack = track
        playbackThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (running) {
                val pcm = try {
                    playbackQueue.take()
                } catch (e: InterruptedException) {
                    break
                }
                if (!running) break
                try {
                    track.write(pcm, 0, pcm.size)
                } catch (e: Exception) {
                    Log.w(TAG, "播放失败", e)
                }
            }
        }, "ts3-playback").apply { start() }
    }

    private fun stopCapture() {
        captureThread?.interrupt()
        captureThread = null
        audioRecord?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        audioRecord = null
    }

    private fun stopPlayback() {
        playbackThread?.interrupt()
        playbackThread = null
        audioTrack?.let {
            runCatching { it.stop() }
            runCatching { it.release() }
        }
        audioTrack = null
    }

    private fun setCommunicationMode(on: Boolean) {
        runCatching {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (on) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            } else {
                am.mode = AudioManager.MODE_NORMAL
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = false
            }
        }
    }

    private companion object {
        private const val TAG = "Ts3AudioEngine"
        private const val SAMPLE_RATE = 48_000
        private const val FRAME_SAMPLES = 960
        private const val PLAYBACK_QUEUE_CAPACITY = 100
        private val EMPTY_FRAME = ByteArray(0)
    }
}
