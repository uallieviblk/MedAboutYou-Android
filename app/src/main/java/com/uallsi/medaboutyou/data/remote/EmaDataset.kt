package com.uallsi.medaboutyou.data.remote

import com.uallsi.medaboutyou.model.Medicine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Result of downloading and parsing the EMA dataset. */
data class EmaResult(
    val medicines: List<Medicine>,
    val timestamp: String,
)

/**
 * Downloads and parses the EMA "Medicine pages" JSON feed. Android port of the
 * desktop `MedicineRepository` download/parse path; the parsed records are then
 * cached in the local Room database (no on-disk JSON copy needed).
 */
class EmaDataset(private val http: Http = Http()) {

    var lastError: String = ""
        private set

    fun refresh(): EmaResult? {
        lastError = ""
        val body = http.getText(DATASET_URL)
        if (body == null) {
            lastError = "EMA download failed: ${http.lastError}"
            return null
        }
        return parse(body)
    }

    private fun parse(jsonText: String): EmaResult? {
        return try {
            val root = json.parseToJsonElement(jsonText).jsonObject
            val timestamp = (root["meta"] as? JsonObject)
                ?.get("timestamp")?.jsonPrimitive?.takeIf { it.isString }?.content
                ?: ""
            val data = root["data"]?.jsonArray ?: return EmaResult(emptyList(), timestamp)
            val meds = data.mapNotNull { el ->
                (el as? JsonObject)?.let { Medicine.fromEmaJson(it) }
            }
            EmaResult(meds, timestamp)
        } catch (e: Exception) {
            lastError = "Could not parse dataset: ${e.message}"
            null
        }
    }

    companion object {
        const val DATASET_URL =
            "https://www.ema.europa.eu/en/documents/report/" +
                "medicines-output-medicines_json-report_en.json"

        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
