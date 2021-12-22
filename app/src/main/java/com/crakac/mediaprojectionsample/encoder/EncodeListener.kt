package com.crakac.mediaprojectionsample.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

interface EncodeListener {
    fun onFormatChanged(format: MediaFormat, type: EncoderType)
    fun onEncoded(buffer: ByteBuffer, info: MediaCodec.BufferInfo, type: EncoderType)
}