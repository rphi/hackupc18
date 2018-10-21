package com.hackupc.uoe.jobcam

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import com.hackupc.uoe.jobcam.Components.ViewerState
import kotlinx.android.synthetic.main.result_layout.*

import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.thread
import android.support.customtabs.CustomTabsIntent
import android.R.string.cancel
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Camera


class ResultsActivity : Activity() {
    private var updateUIHandler: Handler? = null


    private object jobData {
        var shortDescription = ""
        var longDescription = ""
        var url = ""
    }

    private val MSG_UPDATE_DATA = 1
    private val MSG_ERR_NO_JOBS = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.result_layout)
        back_button.setOnClickListener {this@ResultsActivity.finish();}
        deny_job_button.setOnClickListener {this@ResultsActivity.finish();}
        accept_job_button.setOnClickListener{
            val builder = CustomTabsIntent.Builder()
            builder.setToolbarColor(3898290)
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(this, Uri.parse(jobData.url))
        }
        val keyword : String = intent.getStringExtra("keyword")
        search_term_text.text = "Related to " + keyword
        // Initialize Handler.
        createUpdateUiHandler()
        thread() {
            val response = getSearchResults(keyword)
            if (response != null) {
                val resJSON = JSONObject(response)
                //try {
                if (resJSON.getInt("currentResults") == 0) {
                    // we don't have any results
                    val m = Message()
                    m.what = MSG_ERR_NO_JOBS
                    updateUIHandler?.sendMessage(m)
                } else {
                    val offer = resJSON.getJSONArray("offers").getJSONObject(0)
                    if (offer != null) {
                        val title = offer.get("title")
                        if (title != null) {
                            jobData.shortDescription = title as String
                        }
                        val longDescr = offer.get("requirementMin")
                        if (longDescr != null) {
                            jobData.longDescription = longDescr as String
                            if (longDescr.length > 250) {
                                jobData.longDescription = longDescr.substring(0,250) + "..."
                            }
                        }
                        jobData.url = offer.getString("link")
                        val m : Message = Message()
                        m.what = MSG_UPDATE_DATA
                        updateUIHandler?.sendMessage(m)
                    }
                }
                //} catch (e: JSONException) {
                //    logging
                //    finish()
                //}
            }

        }
    }

    private var client = OkHttpClient()

    private fun createUpdateUiHandler() {
        if (updateUIHandler == null) {
            updateUIHandler = @SuppressLint("HandlerLeak")
            object : Handler() {
                override fun handleMessage(msg: Message) {
                    // Means the message is sent from child thread.
                    if (msg.what === MSG_UPDATE_DATA) {
                        result_text_short_description.text = jobData.shortDescription
                        result_text_long_description.text = jobData.longDescription
                    }
                    if (msg.what === MSG_ERR_NO_JOBS) {
                        // 1. Instantiate an <code><a href="/reference/android/app/AlertDialog.Builder.html">AlertDialog.Builder</a></code> with its constructor
                        val builder = AlertDialog.Builder(this@ResultsActivity)

                        // 2. Chain together various setter methods to set the dialog characteristics
                        builder?.setMessage("We couldn't find any jobs related to that object.")?.setTitle("Oops")
                        builder?.setCancelable(true)
                        builder?.setNeutralButton(android.R.string.ok
                        ) { dialog, _ ->
                            finish()
                            dialog.cancel()
                        }

                        // 3. Get the <code><a href="/reference/android/app/AlertDialog.html">AlertDialog</a></code> from <code><a href="/reference/android/app/AlertDialog.Builder.html#create()">create()</a></code>
                        builder?.show()
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    fun getSearchResults(searchWord: String): String? {
        val request = Request.Builder()
                .url("https://api.infojobs.net/api/1/offer?q=" + searchWord)
                .addHeader("Authorization", "basic " + resources.getString(R.string.infojob_api_key))
                .build()

        val response = client.newCall(request).execute()
        return response.body()!!.string()

    }
}