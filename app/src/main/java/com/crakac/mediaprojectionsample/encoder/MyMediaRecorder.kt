package com.crakac.mediaprojectionsample.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer


private const val NUM_TRACKS = 2 // video, audio
private const val UNINITIALIZED = -1

class MyMediaRecorder(
    mediaProjection: MediaProjection,
    fileDescriptor: FileDescriptor,
    width: Int,
    height: Int,
    isStereo: Boolean = true,
) : EncodeListener {
    private val muxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    /** Before using this surface, must call [prepare]*/
    val surface: Surface
        get() = videoEncoder.inputSurface
    private var isMuxerAvailable = false
    private var trackCount = 0
    private val trackIds = MutableList(NUM_TRACKS) { UNINITIALIZED }

    override fun onFormatChanged(format: MediaFormat, type: EncoderType) {
        val index = type.ordinal
        synchronized(this) {
            if (trackIds[index] == UNINITIALIZED) {
                trackIds[index] = muxer.addTrack(format)
                trackCount++
                if (trackCount == NUM_TRACKS) {
                    muxer.start()
                    isMuxerAvailable = true
                }
            }
        }
    }

    override fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: EncoderType) {
        if (!isMuxerAvailable) return
        val trackId = trackIds[type.ordinal]
        muxer.writeSampleData(trackId, buffer, info)
    }

    private val audioEncoder = AudioEncoder(
        mediaProjection = mediaProjection, isStereo = isStereo, listener = this
    )

    private val videoEncoder = VideoEncoder(
        width = width, height = height, listener = this
    )

    fun prepare() {
        trackCount = 0
        trackIds.replaceAll { UNINITIALIZED }
        audioEncoder.prepare()
        videoEncoder.prepare()
    }

    fun start() {
        audioEncoder.start()
        videoEncoder.start()
    }

    fun stop() {
        audioEncoder.stop()
        videoEncoder.stop()
        muxer.stop()
    }

    fun release() {
        audioEncoder.release()
        videoEncoder.release()
        muxer.release()
    }
}