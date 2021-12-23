package com.crakac.mediaprojectionsample.encoder

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.util.Log
import kotlinx.coroutines.*

private const val MIME_TYPE_AAC = MediaFormat.MIMETYPE_AUDIO_AAC
private const val SAMPLE_RATE = 44_100
private const val BIT_RATE = 64 * 1024 // 64kbps
private const val TAG = "AudioEncoder"

class AudioEncoder(
    mediaProjection: MediaProjection,
    isStereo: Boolean = true,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val callback: EncoderCallback
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

    private val audioBufferSizeInBytes = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        channelCount,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private val synthesizedData = ShortArray(audioBufferSizeInBytes / 2)
    private val synthesizedByteArray = ByteArray(audioBufferSizeInBytes)

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.CAMCORDER,
        SAMPLE_RATE,
        channelMask,
        AudioFormat.ENCODING_PCM_16BIT,
        audioBufferSizeInBytes
    ).wrap(audioBufferSizeInBytes)

    @SuppressLint("MissingPermission")
    private val audioPlayback = AudioRecord.Builder()
        .setAudioPlaybackCaptureConfig(playbackConfig)
        .setAudioFormat(
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelMask)
                .build()
        )
        .setBufferSizeInBytes(audioBufferSizeInBytes)
        .build()
        .wrap(audioBufferSizeInBytes)

    private val format =
        MediaFormat.createAudioFormat(MIME_TYPE_AAC, SAMPLE_RATE, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, audioBufferSizeInBytes)
        }

    private val codec = MediaCodec.createEncoderByType(MIME_TYPE_AAC)

    private var isEncoding = false

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
        audioPlayback.startRecording()
        codec.start()
        recordJob = record()
        drain()
    }

    /**
     * Read data from audioRecord and pass to codec
     */
    private fun record() = scope.launch {
        while (isActive) {
            val deferredVoice = async { audioRecord.read() }
            val deferredPlayback = async { audioPlayback.read() }

            val voice = deferredVoice.await()
            val playback = deferredPlayback.await()
            if (!voice.isSuccess || !playback.isSuccess) continue
            if (voice.readShorts != playback.readShorts) {
                Log.w(
                    TAG,
                    "voice.readBytes != playback.readBytes (${voice.readShorts}, ${playback.readShorts})"
                )
            }
            val dataSizeInShorts = minOf(voice.readShorts, playback.readShorts)
            for (i in 0 until dataSizeInShorts) {
                synthesizedData[i] =
                    minOf(voice.data[i] + playback.data[i], Short.MAX_VALUE.toInt()).toShort()
            }
            val inputBufferId = codec.dequeueInputBuffer(-1)
            val inputBuffer = codec.getInputBuffer(inputBufferId)!!
            synthesizedData.copyToByteArray(synthesizedByteArray, dataSizeInShorts)
            inputBuffer.put(synthesizedByteArray)
            codec.queueInputBuffer(
                inputBufferId, 0, dataSizeInShorts * 2, audioPlayback.createTimestamp(), 0
            )
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
                callback.onFormatChanged(codec.outputFormat, EncoderType.Audio)
            } else {
                val encodedData = codec.getOutputBuffer(outputBufferId)
                    ?: throw RuntimeException("encodedData is null")
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    bufferInfo.size = 0
                }
                if (bufferInfo.size > 0) {
                    callback.onEncoded(encodedData, bufferInfo, EncoderType.Audio)
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
        audioPlayback.stop()
        recordJob?.cancel()
        val id = codec.dequeueInputBuffer(-1)
        // send EOS to stop encoding
        codec.queueInputBuffer(id, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
    }

    override fun release() {
        isEncoding = false
        audioRecord.release()
        audioPlayback.release()
        codec.release()
        scope.cancel()
    }
}