package com.uallsi.medaboutyou.data.remote

import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Pack
import com.uallsi.medaboutyou.model.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Live search against the AIFA (Italian Medicines Agency) public API — faithful
 * port of the desktop `AifaSource`.
 */
class AifaSource(private val http: Http = Http()) {

    var lastError: String = ""
        private set

    fun search(query: String): List<Medicine> {
        lastError = ""
        val url = "$API_BASE/formadosaggio/ricerca?query=${urlEncode(query)}"
        val response = http.getText(url)
        if (response == null) {
            lastError = "AIFA search failed: ${http.lastError}"
            return emptyList()
        }

        return try {
            val root = json.parseToJsonElement(response).jsonObject
            val content = (root["data"] as? JsonObject)?.get("content")?.jsonArray
                ?: return emptyList()

            content.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val medObj = obj["medicinale"] as? JsonObject ?: JsonObject(emptyMap())

                val name = medObj.str("denominazioneMedicinale")
                if (name.isEmpty()) return@mapNotNull null

                val status = when {
                    obj.long("revocato") != 0L -> "Revoked"
                    obj.long("sospeso") != 0L -> "Suspended"
                    else -> "Authorised"
                }

                var url = ""
                var rcpUrl = ""
                var hasRcp = false
                var productNumber = ""
                val sis = medObj.long("codiceSis")
                val aic6 = medObj.long("aic6")
                if (sis != 0L && aic6 != 0L) {
                    url = "$PORTAL_BASE/organizzazione/$sis/farmaci/$aic6"
                    rcpUrl = "$url/stampati/RCP"
                    hasRcp = true
                    productNumber = "AIC $aic6"
                }

                // Supply classification from the first package, if present.
                var prescription = ""
                (obj["confezioni"] as? JsonArray)?.firstOrNull()?.jsonObject?.let { pack ->
                    val rf = joinStrings(pack["descrizioneRf"])
                    prescription = rf.ifEmpty { pack.str("classeFornitura") }
                }

                Medicine(
                    source = Source.AIFA,
                    extId = "aifa:${obj.str("id")}",
                    category = "Human",
                    name = name,
                    activeSubstance = joinStrings(obj["principiAttiviIt"]),
                    atcCode = joinStrings(obj["codiceAtc"]),
                    marketingAuthorisationHolder = medObj.str("aziendaTitolare"),
                    pharmaceuticalForm = tidyCaps(obj.str("descrizioneFormaDosaggio")),
                    route = tidyCaps(joinStrings(obj["vieSomministrazione"])),
                    orphan = obj.long("orfano") != 0L,
                    prescription = prescription,
                    status = status,
                    url = url,
                    rcpUrl = rcpUrl,
                    hasRcp = hasRcp,
                    productNumber = productNumber,
                    packs = parsePacks(obj["confezioni"]),
                )
            }
        } catch (e: Exception) {
            lastError = "Could not parse AIFA response: ${e.message}"
            emptyList()
        }
    }

    /**
     * Parse the marketed pack sizes from AIFA `confezioni`. Each
     * `denominazionePackage` ends with "<n> <unit>" (e.g. `… " 12 COMPRESSE`);
     * we take the last such number as the units in the pack. Deduplicated.
     */
    private fun parsePacks(element: kotlinx.serialization.json.JsonElement?): List<Pack> {
        val arr = element as? JsonArray ?: return emptyList()
        val byLabel = LinkedHashMap<String, Pack>()
        for (item in arr) {
            val den = (item as? JsonObject)?.str("denominazionePackage").orEmpty()
            if (den.isBlank()) continue
            val m = PACK_REGEX.findAll(den).lastOrNull() ?: continue
            val units = m.groupValues[1].toIntOrNull()?.takeIf { it > 0 } ?: continue
            val label = "$units ${tidyCaps(m.groupValues[2])}"
            byLabel.putIfAbsent(label, Pack(label, units))
        }
        return byLabel.values.sortedBy { it.units }
    }

    private fun JsonObject.str(key: String): String {
        val p = this[key] as? JsonPrimitive ?: return ""
        return if (p.isString) p.content else ""
    }

    private fun JsonObject.long(key: String): Long =
        (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

    private fun joinStrings(element: kotlinx.serialization.json.JsonElement?): String {
        val arr = element as? JsonArray ?: return ""
        return arr.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .joinToString(", ")
    }

    /** Title-case an ALL-CAPS string ("ORALE" -> "Orale"). */
    private fun tidyCaps(text: String): String {
        val out = StringBuilder(text.length)
        var start = true
        for (ch in text) {
            when {
                ch in 'A'..'Z' && !start -> out.append(ch + ('a' - 'A'))
                ch == ' ' || ch == ',' || ch == '/' || ch == '-' -> {
                    out.append(ch); start = true; continue
                }
                else -> out.append(ch)
            }
            start = false
        }
        return out.toString()
    }

    private fun urlEncode(text: String): String {
        val sb = StringBuilder(text.length * 3)
        for (b in text.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            ) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0x0F])
            }
        }
        return sb.toString()
    }

    companion object {
        const val API_BASE = "https://api.aifa.gov.it/aifa-bdf-eif-be/1.0.0"
        const val PORTAL_BASE = "https://medicinali.aifa.gov.it/#"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        // "<number> <word>" — the pack quantity is the last such pair in the
        // package description ("…1000 MG COMPRESSE … 12 COMPRESSE" -> 12).
        private val PACK_REGEX = Regex("""(\d+)\s+(\p{L}+)""")
    }
}
