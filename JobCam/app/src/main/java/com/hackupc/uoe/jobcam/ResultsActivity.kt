package com.hackupc.uoe.jobcam

import android.app.Activity
import android.os.Bundle
import android.util.Log

import okhttp3.*
import java.io.IOException
import kotlin.concurrent.thread

class ResultsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.result_layout)
        thread() {
            // Do Search Here
        }
    }

    private var client = OkHttpClient()

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