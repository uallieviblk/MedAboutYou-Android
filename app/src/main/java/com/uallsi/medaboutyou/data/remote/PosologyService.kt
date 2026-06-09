// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.remote

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Source

/**
 * Pulls the "4.2 Posology and method of administration" section of the SmPC into
 * the app — the Android port of the desktop `PosologyService`. For EMA records it
 * resolves the Product Information PDF from the EPAR page, downloads it and
 * extracts §4.2; for AIFA (a SPA whose RCP isn't a plain GET) it reports
 * [Result.Unavailable] so the UI keeps the "open the RCP" link.
 *
 * Best-effort: any failure degrades to [Result.Unavailable]/[Result.Error] and
 * the detail screen still offers the official-document link.
 */
class PosologyService(private val http: Http = Http()) {

    sealed interface Result {
        data class Text(val section: String) : Result
        data object Unavailable : Result
        data class Error(val message: String) : Result
    }

    fun fetch(med: Medicine): Result {
        if (med.source != Source.EMA || med.url.isBlank()) return Result.Unavailable
        val pdfUrl = resolvePdfUrl(med.url) ?: return Result.Unavailable
        val bytes = http.getBytes(pdfUrl) ?: return Result.Error(http.lastError.ifEmpty { "Download failed" })
        if (bytes.size < 5 || !bytes.copyOf(5).toString(Charsets.US_ASCII).startsWith("%PDF")) {
            return Result.Unavailable
        }
        return runCatching { extractPosology(bytes) }
            .getOrNull()
            ?.let { Result.Text(it) }
            ?: Result.Unavailable
    }

    /** Find the Product-Information PDF link on the EPAR page. */
    private fun resolvePdfUrl(eparUrl: String): String? {
        val html = http.getText(eparUrl) ?: return null
        val href = PI_PDF.find(html)?.groupValues?.get(1) ?: return null
        return when {
            href.startsWith("http") -> href
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> "https://www.ema.europa.eu$href"
            else -> "https://www.ema.europa.eu/$href"
        }
    }

    private fun extractPosology(pdf: ByteArray): String? {
        val text = PDDocument.load(pdf).use { PDFTextStripper().getText(it) }
        // The heading appears twice (table of contents + the real section); the
        // last occurrence is the body. Slice up to the next "4.3".
        val heading = SECTION_42.findAll(text).lastOrNull() ?: return null
        val start = heading.range.first
        val end = SECTION_43.find(text, start + 5)?.range?.first ?: minOf(text.length, start + 8000)
        val section = collapseWhitespace(text.substring(start, end))
        return section.takeIf { it.length > 60 }
    }

    private fun collapseWhitespace(s: String): String =
        s.replace(Regex("[ \\t]+\n"), "\n").replace(Regex("\n{3,}"), "\n\n").trim()

    private companion object {
        // A link whose URL mentions "product-information" and ends "_en.pdf".
        private val PI_PDF = Regex(
            """href\s*=\s*["']([^"']*product-information[^"']*_en\.pdf)["']""",
            RegexOption.IGNORE_CASE,
        )
        private val SECTION_42 = Regex("""4\.2\.?\s+Posology[^\n]*""", RegexOption.IGNORE_CASE)
        private val SECTION_43 = Regex("""\n\s*4\.3\.?\s""")
    }
}
