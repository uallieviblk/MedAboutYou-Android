// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.remote

import com.uallsi.medaboutyou.model.Medicine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Best-effort packaging/illustration image lookup. Android adaptation of the
 * desktop `ImageService`: without an API key it falls back to a Wikipedia
 * thumbnail (which may be a chemical structure rather than a photo).
 */
class ImageService(private val http: Http = Http()) {

    /** Returns a directly-loadable image URL, or null if none was found. */
    fun fetchImageUrl(medicine: Medicine): String? {
        val title = medicine.inn.ifEmpty { medicine.activeSubstance.ifEmpty { medicine.name } }
            .substringBefore(";").substringBefore(",").trim()
        if (title.isEmpty()) return null
        val url = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
            java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8")
        val body = http.getText(url, mapOf("accept" to "application/json")) ?: return null
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val thumb = (root["thumbnail"] as? JsonObject) ?: (root["originalimage"] as? JsonObject)
            thumb?.get("source")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true;
            isLenient = true
        }
    }
}
