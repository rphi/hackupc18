package com.hackupc.uoe.jobcam

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.hackupc.uoe.jobcam.Components.ScreenTouchDetector
import kotlinx.android.synthetic.main.home_screen.*


class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.home_screen)
        var touch_detector : ScreenTouchDetector = home_screen_detector as ScreenTouchDetector
        touch_detector.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                var intent = Intent(this@MainActivity, CameraActivity::class.java)
                startActivity(intent)
            }
        })
    }
}
