package com.uallsi.medaboutyou.model

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Data provider a medicine record originated from.
 *
 * Mirrors the C++ `Source` enum in `models/medicine.h`.
 */
enum class Source(val key: String) {
    /** European Medicines Agency (centrally authorised). */
    EMA("ema"),

    /** Agenzia Italiana del Farmaco (Italy, nationally authorised). */
    AIFA("aifa");

    companion object {
        fun fromKey(key: String): Source = if (key == AIFA.key) AIFA else EMA
    }
}

/**
 * A marketed package of a medicine, e.g. "12 compresse" → [units] = 12.
 * Parsed from the AIFA `confezioni` list; EMA records carry none.
 */
data class Pack(val label: String, val units: Int)

/**
 * A single medicine record from one of the supported sources.
 *
 * Faithful port of the C++ `Medicine` struct. Every EMA source field is a
 * string; boolean indicators arrive as "Yes"/"No"; multi-values are
 * ';'-separated.
 */
data class Medicine(
    val source: Source = Source.EMA,
    val extId: String = "",
    val category: String = "",
    val name: String = "",
    val productNumber: String = "",
    val status: String = "",
    val opinionStatus: String = "",
    val inn: String = "",
    val activeSubstance: String = "",
    val therapeuticArea: String = "",
    val atcCode: String = "",
    val pharmacotherapeuticGroup: String = "",
    val therapeuticIndication: String = "",
    val marketingAuthorisationHolder: String = "",
    val species: String = "",
    // AIFA-specific extras (empty for EMA records).
    val pharmaceuticalForm: String = "",
    val route: String = "",
    val prescription: String = "",
    val hasRcp: Boolean = false,
    val rcpUrl: String = "",
    // "Yes"/"No" medicine-type indicators, normalised to bool.
    val additionalMonitoring: Boolean = false,
    val advancedTherapy: Boolean = false,
    val biosimilar: Boolean = false,
    val conditionalApproval: Boolean = false,
    val exceptionalCircumstances: Boolean = false,
    val generic: Boolean = false,
    val orphan: Boolean = false,
    val prime: Boolean = false,
    val acceleratedAssessment: Boolean = false,
    // Key lifecycle dates (kept as the source dd/mm/yyyy strings).
    val marketingAuthorisationDate: String = "",
    val ecDecisionDate: String = "",
    val revisionNumber: String = "",
    val firstPublished: String = "",
    val lastUpdated: String = "",
    val url: String = "",
    // Marketed pack sizes (AIFA only). Transient: used to offer "add a pack"
    // for stock; not persisted to Room.
    val packs: List<Pack> = emptyList(),
) {
    /** True when this record describes a human medicine. */
    val isHuman: Boolean get() = category == "Human"

    companion object {
        /** Build a [Medicine] from one JSON object of the EMA dataset. */
        fun fromEmaJson(obj: JsonObject): Medicine {
            fun field(key: String): String =
                (obj[key]?.jsonPrimitive)?.takeIf { it.isString }?.content ?: ""

            fun flag(key: String): Boolean = field(key) == "Yes"

            fun firstNonEmpty(humanKey: String, vetKey: String): String =
                field(humanKey).ifEmpty { field(vetKey) }

            val productNumber = field("ema_product_number")
            val name = field("name_of_medicine")
            return Medicine(
                source = Source.EMA,
                category = field("category"),
                name = name,
                productNumber = productNumber,
                status = field("medicine_status"),
                opinionStatus = field("opinion_status"),
                inn = field("international_non_proprietary_name_common_name"),
                activeSubstance = field("active_substance"),
                therapeuticArea = field("therapeutic_area_mesh"),
                atcCode = firstNonEmpty("atc_code_human", "atcvet_code_veterinary"),
                pharmacotherapeuticGroup = firstNonEmpty(
                    "pharmacotherapeutic_group_human",
                    "pharmacotherapeutic_group_veterinary",
                ),
                therapeuticIndication = field("therapeutic_indication"),
                marketingAuthorisationHolder =
                    field("marketing_authorisation_developer_applicant_holder"),
                species = field("species_veterinary"),
                additionalMonitoring = flag("additional_monitoring"),
                advancedTherapy = flag("advanced_therapy"),
                biosimilar = flag("biosimilar"),
                conditionalApproval = flag("conditional_approval"),
                exceptionalCircumstances = flag("exceptional_circumstances"),
                generic = flag("generic"),
                orphan = flag("orphan_medicine"),
                prime = flag("prime_priority_medicine"),
                acceleratedAssessment = flag("accelerated_assessment"),
                marketingAuthorisationDate = field("marketing_authorisation_date"),
                ecDecisionDate = field("european_commission_decision_date"),
                revisionNumber = field("revision_number"),
                firstPublished = field("first_published_date"),
                lastUpdated = field("last_updated_date"),
                url = field("medicine_url"),
                extId = productNumber.ifEmpty { name },
            )
        }
    }
}
