package com.hackupc.uoe.jobcam

import android.app.Activity
import android.graphics.Bitmap
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

class ResultsActivity : Activity() {
    private var updateUIHandler: Handler? = null


    private object jobData {
        var shortDescription = "";
        var longDescription = "";
    }

    private val MSG_UPDATE_DATA = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.result_layout)
        back_button.setOnClickListener {this@ResultsActivity.finish();}
        deny_job_button.setOnClickListener {this@ResultsActivity.finish();}
        val keyword : String = intent.getStringExtra("keyword")
        search_term_text.text = "Related to " + keyword
        // Initialize Handler.
        createUpdateUiHandler()
        thread() {
            val response = getSearchResults(keyword)
            if (response != null) {
                val resJSON = JSONObject(response)
                try {
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
                        val m : Message = Message()
                        m.what = MSG_UPDATE_DATA
                        updateUIHandler?.sendMessage(m)
                    }
                } catch (e: JSONException) {
                   finish()
                }
            }

        }
    }

    private var client = OkHttpClient()

    private fun createUpdateUiHandler() {
        if (updateUIHandler == null) {
            updateUIHandler = object : Handler() {
                override fun handleMessage(msg: Message) {
                    // Means the message is sent from child thread.
                    if (msg.what === MSG_UPDATE_DATA) {
                        result_text_short_description.text = jobData.shortDescription
                        result_text_long_description.text = jobData.longDescription
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