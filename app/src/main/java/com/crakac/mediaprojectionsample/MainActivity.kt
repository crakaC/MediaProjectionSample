package com.crakac.mediaprojectionsample

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.crakac.mediaprojectionsample.ui.theme.MyTheme

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private val launcher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK || result.data == null) {
            Toast.makeText(this@MainActivity, "Not permitted", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        startCaptureService(result.data!!)
    }

    private fun startCaptureService(data: Intent) {
        val intent = ScreenRecordService.createIntent(this, data)
        startForegroundService(intent)
    }

    private fun isPermissionGranted() =
        (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED)

    private fun onClickStart() {
        if (!isPermissionGranted()) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO), 1001
            )
        } else {
            launcher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    private fun onClickStop() {
        val intent =
            Intent(this, ScreenRecordService::class.java)
        stopService(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mediaProjectionManager =
            getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            MyTheme {
                Surface {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Button(
                            modifier = Modifier
                                .width(120.dp)
                                .align(CenterHorizontally),
                            onClick = ::onClickStart
                        ) {
                            Text(text = "Start")
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            modifier = Modifier
                                .width(120.dp)
                                .align(CenterHorizontally),
                            onClick = ::onClickStop
                        ) {
                            Text(text = "Stop")
                        }
                    }
                }
            }
        }
    }
}
