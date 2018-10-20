package com.hackupc.uoe.jobcam

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.wonderkiln.camerakit.*
import okhttp3.*
import java.io.IOException

import android.graphics.Bitmap

import java.io.ByteArrayOutputStream

class CameraActivity : Activity() {

    private var cameraView: CameraView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        cameraView = findViewById(R.id.camera)

        val button = findViewById<View>(R.id.floatingActionButton3)

        button.setOnClickListener {
            capture()
        }
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

    val JSON = MediaType.parse("application/json; charset=utf-8")
    var client = OkHttpClient()

    @Throws(IOException::class)
    fun post(url: String, byteArray: ByteArray): String {
        var body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", "", RequestBody.create(MediaType.parse("image/*"), byteArray))
                .build()
        val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("X-secret", "hi")
                .build()
        val response = client.newCall(request).execute()
        return response.body()!!.string()
    }

    fun capture() {
        cameraView?.captureImage {
            val stream = ByteArrayOutputStream()
            it.bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            val image = stream.toByteArray()
            post("http://localhost:8000/testpost", image)
        }
    }
}