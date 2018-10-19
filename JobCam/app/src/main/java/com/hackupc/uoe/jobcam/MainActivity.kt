package com.hackupc.uoe.jobcam

import android.app.Activity

import android.os.Bundle
import com.wonderkiln.camerakit.*


class MainActivity : Activity() {

    private var cameraView: CameraView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraView = findViewById(R.id.camera)
    }

    override fun onStart() {
        super.onStart()
        cameraView?.start()
    }


    override fun onPause() {
        cameraView?.stop()
        super.onPause()
    }

    override fun onStop() {
        cameraView?.stop()
        super.onStop()
    }

}
