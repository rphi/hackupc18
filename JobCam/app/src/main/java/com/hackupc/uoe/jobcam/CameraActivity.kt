package com.hackupc.uoe.jobcam

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.hackupc.uoe.jobcam.Components.Boundary
import com.hackupc.uoe.jobcam.Components.MLresponse
import com.hackupc.uoe.jobcam.Components.MLresults
import com.wonderkiln.camerakit.CameraKit
import com.wonderkiln.camerakit.CameraKit.Constants.METHOD_STILL
import com.wonderkiln.camerakit.CameraView
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.lang.Math.*
import kotlin.concurrent.thread
import kotlin.math.sqrt


class CameraActivity : Activity() {

    private var cameraView: CameraView? = null
    private var imageView: ImageView? = null

    private var boundaries: Array<Boundary>? = null

    // This is the activity main thread Handler.
    private var updateUIHandler: Handler? = null

    var drawing: Bitmap? = null
    var canvas: Canvas? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Handler.
        createUpdateUiHandler()

        cameraView = findViewById(R.id.camera)
        cameraView!!.setMethod(METHOD_STILL)

        val button = findViewById<View>(R.id.floatingActionButton3)
        imageView = findViewById(R.id.imageView)
        imageView!!.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldRight, oldTop, oldBottom ->
            val viewWidth = right - left
            if (viewWidth > 0) {
                thread {
                    drawing = Bitmap.createBitmap(imageView!!.width, imageView!!.height, Bitmap.Config.ARGB_8888)
                    canvas = Canvas(drawing)

                }
            }
        }

        imageView!!.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                when (event?.action) {
                    MotionEvent.ACTION_DOWN -> overlayClick(event)//Do Something
                }

                return v?.onTouchEvent(event) ?: true
            }
        })

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
    fun post(url: String, byteArray: ByteArray) {
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
        receive(json = response.body()!!.string())
    }

    fun capture() {
        cameraView?.captureImage {
            val stream = ByteArrayOutputStream()
            it.bitmap.compress(Bitmap.CompressFormat.JPEG, 50, stream)
            val image = stream.toByteArray()
            post("http://192.168.42.78:12345/", image)
            //receive("test")
        }
    }

    fun receive(json: String) {

        val boundaries = parseBoundaries(json)
        drawBoundaries(boundaries, canvas!!)

        // Build message object.
        val message = Message()
        // Set message type.
        message.what = MSG_UPDATE_CANVAS
        message.obj = drawing

        updateUIHandler!!.sendMessage(message)
    }

    fun drawBoundaries(boundaries: Array<Boundary>, canvas: Canvas?){
        if (canvas == null) {return}

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)


        val paint = Paint()
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(200,100,100)
        paint.strokeWidth = 5.0f
        paint.textSize = 30f
        var fontPaint = Paint()
        fontPaint.textAlign = Paint.Align.CENTER
        fontPaint.style = Paint.Style.FILL_AND_STROKE
        fontPaint.color = Color.WHITE
        fontPaint.strokeWidth = 1.0f
        fontPaint.textSize = 50f
        boundaries.forEach {
            canvas.drawCircle(it.centre_x, it.centre_y, it.radius, paint)
            canvas.drawText(it.label, it.centre_x, it.centre_y, fontPaint)
        }
        this.boundaries = boundaries
    }

    fun parseBoundaries(json: String) : Array<Boundary> {

        val resJSON = JSONObject(json)

        var boundaryList = arrayListOf<Boundary>()

        val results = resJSON!!.getJSONArray("results")

        for (i in 0..(results.length() - 1)) {
            val item = results.getJSONObject(i);
            boundaryList.add(Boundary(label = item.getJSONArray("classes").getString(0),
                    centre_x = (item.getJSONArray("bbox").getDouble(0) * imageView!!.width).toFloat(),
                    centre_y = (item.getJSONArray("bbox").getDouble(1) * imageView!!.height).toFloat(),
                    radius = (min(item.getJSONArray("bbox").getDouble(2), item.getJSONArray("bbox").getDouble(3)) * imageView!!.width) .toFloat()
            ))
        }
        return boundaryList.toTypedArray()
    }

    fun overlayClick(event: MotionEvent?) {
        if (boundaries == null) { return } else if (boundaries!!.isEmpty()) { return }

        val x = event!!.x
        val y = event!!.y
        val viewCoords = IntArray(2)
        imageView!!.getLocationOnScreen(viewCoords)
        val imageX = x - viewCoords[0] // viewCoords[0] is the X coordinate
        val imageY = y - viewCoords[1] // viewCoords[1] is the y coordinate

        var dist: Double? = null
        var tapped: String? = null

        boundaries!!.forEach {
            val distFromCentre = sqrt(pow(imageX.toDouble() - it.centre_x, 2.0) + pow(imageY.toDouble() - it.centre_y, 2.0))

            if ( dist == null ) {
                dist = distFromCentre
            }
            if ( distFromCentre < it.radius.toDouble() && distFromCentre <= dist!!) {
                tapped = it.label
            }
        }

        if (tapped != null) {
            // we've found a tap on an object
            tappedOn(tapped!!)
        }
    }

    fun tappedOn(tapped: String){
        print(tapped)
    }
}