package com.crakac.mediaprojectionsample.encoder

fun ShortArray.copyToByteArray(byteArray: ByteArray, size: Int) {
    for (i in 0 until size) {
        val s = get(i).toInt()
        byteArray[i * 2] = s.and(0xFF).toByte()
        byteArray[i * 2 + 1] = s.shr(8).toByte()
    }
}