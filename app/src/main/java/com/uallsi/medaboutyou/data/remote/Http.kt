// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Thin OkHttp wrapper — the Android equivalent of the desktop `HttpClient`.
 * Blocking calls are meant to run on a background dispatcher (Dispatchers.IO).
 */
class Http(
    private val client: OkHttpClient = defaultClient,
) {
    var lastError: String = ""
        private set

    fun getText(url: String, headers: Map<String, String> = emptyMap()): String? {
        lastError = ""
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}"
                    return null
                }
                resp.body?.string()
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray? {
        lastError = ""
        return try {
            val builder = Request.Builder().url(url)
            headers.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}"
                    return null
                }
                resp.body?.bytes()
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            null
        }
    }

    companion object {
        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
