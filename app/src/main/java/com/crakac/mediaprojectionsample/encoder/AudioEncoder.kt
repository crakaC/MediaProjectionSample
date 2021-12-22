package com.crakac.mediaprojectionsample.encoder

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.Executors

private const val MIME_TYPE = MediaFormat.MIMETYPE_AUDIO_AAC
private const val SAMPLE_RATE = 44_100
private const val BIT_RATE = 64 * 1024 // 64kbps
private const val TAG = "AudioEncoder"
private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MICROS = 1_000L
private const val BYTES_PER_SAMPLE = 2 // 16bit-PCM

class AudioEncoder(
    mediaProjection: MediaProjection,
    isStereo: Boolean = true,
    dispatcher: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher(),
    private val listener: EncodeListener
) : Encoder {
    private val scope =
        CoroutineScope(CoroutineName(TAG) + dispatcher)
    private val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
        .addMatchingUsage(AudioAttributes.USAGE_GAME)
        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
        .build()

    private val channelCount = if (isStereo) 2 else 1
    private val channelMask =
        if (isStereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO
    private val bytesPerFrame = channelCount * BYTES_PER_SAMPLE

    private val audioBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        channelCount,
        AudioFormat.ENCODING_PCM_16BIT
    )
    private val audioBuffer = ByteArray(audioBufferSize)

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord.Builder()
        .setAudioPlaybackCaptureConfig(playbackConfig)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelMask)
                .build()
        )
        .setBufferSizeInBytes(audioBufferSize)
        .build()

    private val format =
        MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSize)
        }

    private val codec = MediaCodec.createEncoderByType(MIME_TYPE)

    private var isEncoding = false

    private fun createTimestamp(framePosition: Long): Long {
        val audioTimestamp = AudioTimestamp()
        audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
        val referenceFrame = audioTimestamp.framePosition
        val referenceTimestamp = audioTimestamp.nanoTime
        val timestampNanos =
            referenceTimestamp + (framePosition - referenceFrame) * NANOS_PER_SECOND / SAMPLE_RATE
        return timestampNanos / NANOS_PER_MICROS
    }

    override fun prepare() {
        codec.reset()
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    }

    private var recordJob: Job? = null
    override fun start() {
        if (isEncoding) {
            Log.w(TAG, "already starting.")
            return
        }
        isEncoding = true
        audioRecord.startRecording()
        codec.start()
        recordJob = record()
        drain()
    }

    /**
     * Read data from audioRecord and pass to codec
     */
    private fun record() = scope.launch {
        var totalReadFrames = 0L
        while (isActive) {
            val readBytes = audioRecord.read(audioBuffer, 0, audioBufferSize)
            if (readBytes < 0) {
                val msg = when (readBytes) {
                    AudioRecord.ERROR_INVALID_OPERATION -> "Invalid Operation"
                    AudioRecord.ERROR_BAD_VALUE -> "Bad Value"
                    AudioRecord.ERROR_DEAD_OBJECT -> "Dead Object"
                    else -> "Unknown Error"
                }
                Log.e(TAG, msg)
                continue
            }
            val inputBufferId = codec.dequeueInputBuffer(-1)
            val inputBuffer = codec.getInputBuffer(inputBufferId)!!
            inputBuffer.put(audioBuffer)
            codec.queueInputBuffer(
                inputBufferId, 0, readBytes, createTimestamp(totalReadFrames), 0
            )
            totalReadFrames += readBytes / bytesPerFrame
        }
    }

    private fun drain() = scope.launch {
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            val outputBufferId = codec.dequeueOutputBuffer(bufferInfo, -1)
            if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                continue
            } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                Log.d(TAG, "Output format changed")
                listener.onFormatChanged(codec.outputFormat)
            } else {
                val encodedData = codec.getOutputBuffer(outputBufferId)
                    ?: throw RuntimeException("encodedData is null")
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0) {
                    listener.onEncoded(encodedData, bufferInfo)
                    codec.releaseOutputBuffer(outputBufferId, false)
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    Log.d(TAG, "EOS")
                    break
                }
            }
        }
        codec.stop()
        isEncoding = false
    }

    override fun stop() {
        audioRecord.stop()
        recordJob?.cancel()
        val id = codec.dequeueInputBuffer(-1)
        // send EOS to stop encoding
        codec.queueInputBuffer(id, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    override fun release() {
        isEncoding = false
        audioRecord.release()
        codec.release()
        scope.cancel()
    }
}