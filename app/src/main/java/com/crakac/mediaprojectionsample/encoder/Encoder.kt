package com.crakac.mediaprojectionsample.encoder

interface Encoder {
    fun prepare()
    fun start()
    fun stop()
    fun release()
}

enum class EncoderType { Video, Audio }