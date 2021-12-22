package com.crakac.mediaprojectionsample

import android.app.Activity.RESULT_OK
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import com.crakac.mediaprojectionsample.encoder.MyMediaRecorder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class ScreenRecordService : Service() {
    private lateinit var virtualDisplay: VirtualDisplay
    private lateinit var recorder: MyMediaRecorder
    private lateinit var projection: MediaProjection
    private lateinit var contentUri: Uri
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            return super.onStartCommand(intent, flags, startId)
        }
        val data = intent.getParcelableExtra<Intent>(KEY_DATA) ?: return START_NOT_STICKY
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "MediaProjection"
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Media Projection",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }
        val notification = Notification.Builder(applicationContext, channelId)
            .setContentTitle("MediaProjection")
            .setContentText("Now recording...")
            .setSmallIcon(R.drawable.ic_cast)
            .build()
        val notificationId = 1 // can be arbitrary?
        startForeground(notificationId, notification)
        startRecording(data)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }

    private fun startRecording(data: Intent) {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = projectionManager.getMediaProjection(RESULT_OK, data)

        val metrics = resources.displayMetrics
        val rawWidth = metrics.widthPixels
        val rawHeight = metrics.heightPixels

        val scale = if(maxOf(rawWidth, rawHeight) > 960){
            960f / maxOf(rawWidth, rawHeight)
        } else 1f

        val width = (rawWidth * scale).roundToInt()
        val height = (rawHeight * scale).roundToInt()

        contentUri = createContentUri()
        val contentFd = contentResolver.openFileDescriptor(contentUri, "w")!!.fileDescriptor

        recorder = MyMediaRecorder(
            projection,
            contentFd,
            width,
            height,
            true
        )
        recorder.prepare()
        virtualDisplay = projection.createVirtualDisplay(
            "Projection",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            recorder.surface,
            null,
            null
        )
        recorder.start()
    }

    private fun stopRecording() {
        recorder.stop()
        recorder.release()
        virtualDisplay.release()
        projection.stop()

        val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        contentResolver.update(contentUri, values, null, null)
    }

    private fun createContentUri(): Uri {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val formattedTime = sdf.format(Date())
        val filename = "$formattedTime.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.TITLE, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/avc")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val storageUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return contentResolver.insert(storageUri, values)!!
    }

    companion object {
        private const val TAG = "ScreenRecordService"
        const val KEY_DATA = "data"
        fun createIntent(context: Context, data: Intent): Intent {
            val intent = Intent(context, ScreenRecordService::class.java)
            intent.putExtra(KEY_DATA, data)
            return intent
        }
    }
}