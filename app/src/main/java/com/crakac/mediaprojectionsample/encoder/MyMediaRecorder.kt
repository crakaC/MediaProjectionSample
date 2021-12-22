package com.crakac.mediaprojectionsample.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.view.Surface
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger


private const val NUM_TRACKS = 2 // video, audio

class MyMediaRecorder(
    mediaProjection: MediaProjection,
    fileDescriptor: FileDescriptor,
    width: Int,
    height: Int,
    isStereo: Boolean = true,
) {
    private val muxer = MediaMuxer(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

    /** Before using this surface, must call [prepare]*/
    val surface: Surface
        get() = videoEncoder.inputSurface
    private var isMuxerAvailable = false
    private val trackCount = AtomicInteger(0)
    private val audioListener = object : EncodeListener {
        var audioTrackIndex = 0
        override fun onFormatChanged(format: MediaFormat) {
            audioTrackIndex = muxer.addTrack(format)
            startMuxerIfAvailable()
        }

        override fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if(!isMuxerAvailable) return
            muxer.writeSampleData(audioTrackIndex, buffer, info)
        }
    }

    private val videoListener = object : EncodeListener {
        var videoTrackIndex = 0
        override fun onFormatChanged(format: MediaFormat) {
            videoTrackIndex = muxer.addTrack(format)
            startMuxerIfAvailable()
        }

        override fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
            if(!isMuxerAvailable) return
            muxer.writeSampleData(videoTrackIndex, buffer, info)
        }
    }

    private fun startMuxerIfAvailable(){
        if(trackCount.incrementAndGet() == NUM_TRACKS){
            muxer.start()
            isMuxerAvailable = true
        }
    }

    private val audioEncoder = AudioEncoder(
        mediaProjection = mediaProjection, isStereo = isStereo, listener = audioListener
    )

    private val videoEncoder = VideoEncoder(
        width = width, height = height, listener = videoListener
    )

    fun prepare() {
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