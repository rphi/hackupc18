package com.hackupc.uoe.jobcam

import android.app.Activity
import android.os.Bundle

import okhttp3.*
import java.io.IOException

class ResultsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = "api.infojobs.net"
        val endpoint = "/api/1/offer"
    }

    @Throws(IOException::class)
    fun getSearchResults(searchWord: String) {
        val request = Request.Builder()
                .url("https://api.infojobs.net/api/1/offer")
                .addHeader("","")


    }
}