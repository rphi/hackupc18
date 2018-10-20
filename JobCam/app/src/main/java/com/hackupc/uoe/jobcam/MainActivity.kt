package com.hackupc.uoe.jobcam

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.view.View
import com.hackupc.uoe.jobcam.Components.ScreenTouchDetector
import kotlinx.android.synthetic.main.home_screen.*
import android.content.DialogInterface
import android.os.Build


class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)
        var touch_detector : ScreenTouchDetector = home_screen_detector as ScreenTouchDetector
        touch_detector.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO), 1)
                } else {
                    showCameraActivity()
                }
            }
        })
    }

    private fun showCameraActivity() {
        var intent = Intent(this@MainActivity, CameraActivity::class.java)
        startActivity(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    showCameraActivity()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    val builder: AlertDialog.Builder  = AlertDialog.Builder(this)
                    builder.setTitle("We really need your camera!")
                            .setMessage("Access to your camera is required for JobCam to work.")
                            .setNeutralButton(android.R.string.ok) { _, _ -> }
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show()
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

}
