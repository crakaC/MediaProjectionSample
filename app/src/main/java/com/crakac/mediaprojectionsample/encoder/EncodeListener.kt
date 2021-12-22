package com.crakac.mediaprojectionsample.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface EncodeListener {
    fun onFormatChanged(format: MediaFormat)
    fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo)
}