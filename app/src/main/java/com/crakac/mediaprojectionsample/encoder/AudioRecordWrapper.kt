package com.crakac.mediaprojectionsample.encoder

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.util.Log

private const val NANOS_PER_SECOND = 1_000_000_000L
private const val NANOS_PER_MICROS = 1_000L
private const val BYTES_PER_SAMPLE = 2 // 16bit-PCM

private const val TAG = "AudioRecordWrapper"

fun AudioRecord.wrap(bufferSize: Int) = AudioRecordWrapper(this, bufferSize)

class AudioRecordWrapper(
    private val audioRecord: AudioRecord,
    bufferSizeInBytes: Int
) {
    init {
        require(audioRecord.format.encoding == AudioFormat.ENCODING_PCM_16BIT) { "AudioRecordWrapper cannot wrap format except PCM_16BIT" }
    }

    private val buffer = ShortArray(bufferSizeInBytes / 2)
    private val sampleRate = audioRecord.sampleRate
    private val bytesPerFrame = audioRecord.channelCount * BYTES_PER_SAMPLE // 16bit PCM
    private var totalReadFrames = 0L

    // https://github.com/google/mediapipe/blob/e6c19885c6d3c6f410c730952aeed2852790d306/mediapipe/java/com/google/mediapipe/components/MicrophoneHelper.java#L269
    fun createTimestamp(): Long {
        val audioTimestamp = AudioTimestamp()
        audioRecord.getTimestamp(audioTimestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
        val referenceFrame = audioTimestamp.framePosition
        val referenceTimestamp = audioTimestamp.nanoTime
        val timestampNanos =
            referenceTimestamp + (totalReadFrames - referenceFrame) * NANOS_PER_SECOND / sampleRate
        return timestampNanos / NANOS_PER_MICROS
    }

    fun startRecording() {
        totalReadFrames = 0L
        audioRecord.startRecording()
    }

    fun stop() {
        audioRecord.stop()
    }

    fun release() {
        audioRecord.release()
    }

    fun read(): ReadResult {
        val readBytes = audioRecord.read(buffer, 0, buffer.size)
        if (readBytes < 0) {
            val msg = when (readBytes) {
                AudioRecord.ERROR_INVALID_OPERATION -> "Invalid Operation"
                AudioRecord.ERROR_BAD_VALUE -> "Bad Value"
                AudioRecord.ERROR_DEAD_OBJECT -> "Dead Object"
                else -> "Unknown Error"
            }
            Log.e(TAG, msg)
            return ReadResult(buffer, readBytes, isSuccess = false)
        }
        totalReadFrames += readBytes / bytesPerFrame
        return ReadResult(buffer, readBytes, isSuccess = true)
    }

    class ReadResult(
        val data: ShortArray,
        val readShorts: Int,
        val isSuccess: Boolean,
    )
}