package com.hackupc.uoe.jobcam

import android.app.Activity
import android.os.Bundle
import android.view.View
import com.wonderkiln.camerakit.*
import okhttp3.*
import java.io.IOException

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

import java.io.ByteArrayOutputStream
import android.opengl.ETC1.getHeight
import android.opengl.ETC1.getWidth
import android.os.Handler
import android.os.Message
import android.widget.ImageView

import com.hackupc.uoe.jobcam.Components.Boundary
import com.wonderkiln.camerakit.CameraKit.Constants.*
import kotlin.concurrent.thread


class CameraActivity : Activity() {

    private var cameraView: CameraView? = null
    private var imageView: ImageView? = null

    // This is the activity main thread Handler.
    private var updateUIHandler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Handler.
        createUpdateUiHandler()

        cameraView = findViewById(R.id.camera)
        cameraView!!.setMethod(METHOD_STILL);

        val button = findViewById<View>(R.id.floatingActionButton3)
        imageView = findViewById(R.id.imageView)

        button.setOnClickListener {
            thread { capture() }
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

    private val MSG_UPDATE_CANVAS = 1

    /* Create Handler object in main thread. */
    private fun createUpdateUiHandler() {
        if (updateUIHandler == null) {
            updateUIHandler = object : Handler() {
                override fun handleMessage(msg: Message) {
                    // Means the message is sent from child thread.
                    if (msg.what === MSG_UPDATE_CANVAS) {
                        // Update ui in main thread.
                        val bmp = msg.obj as Bitmap
                        imageView!!.setImageBitmap(bmp)
                    }
                }
            }
        }
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
            //post("http://localhost:8000/testpost", image)
            receive("test")
        }
    }

    fun receive(json: String) {
        var drawing = Bitmap.createBitmap(imageView!!.width, imageView!!.height, Bitmap.Config.ARGB_8888)
        var canvas = Canvas(drawing)
        val boundaries = parseBoundaries(json)
        drawBoundaries(boundaries, canvas)

        // Build message object.
        val message = Message()
        // Set message type.
        message.what = MSG_UPDATE_CANVAS
        message.obj = drawing

        updateUIHandler!!.sendMessage(message)
    }

    fun drawBoundaries(boundaries: Array<Boundary>, canvas: Canvas){
        val paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.color = Color.RED
        paint.strokeWidth = 5.0f
        paint.textSize = 30f
        boundaries.forEach {
            canvas.drawCircle(it.centre_x, it.centre_y, it.radius, paint)
            canvas.drawText(it.label, it.centre_x, it.centre_y, paint)
        }
    }

    fun parseBoundaries(json: String) : Array<Boundary> {
        var array = arrayOf(
            Boundary("test", 400.4f, 600.0f, 200.0f)
        )
        return array
    }
}