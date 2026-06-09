package com.uallsi.medaboutyou.data.local

import com.uallsi.medaboutyou.model.DoseTime
import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source

/**
 * Serialise the dose-time list as `year:month:dayOfMonth:weekday:hour:minute`
 * entries separated by `;`. Compact, migration-friendly and self-describing.
 */
fun encodeTimes(times: List<DoseTime>): String =
    times.joinToString(";") { "${it.year}:${it.month}:${it.dayOfMonth}:${it.weekday}:${it.hour}:${it.minute}" }

/** Inverse of [encodeTimes]; returns an empty list if [text] is blank/garbled. */
fun decodeTimes(text: String): List<DoseTime> {
    if (text.isBlank()) return emptyList()
    return text.split(";").mapNotNull { entry ->
        val p = entry.split(":")
        if (p.size != 6) return@mapNotNull null
        runCatching {
            DoseTime(
                year = p[0].toInt(),
                month = p[1].toInt(),
                dayOfMonth = p[2].toInt(),
                weekday = p[3].toInt(),
                hour = p[4].toInt(),
                minute = p[5].toInt(),
            )
        }.getOrNull()
    }
}

fun Medicine.toEntity() = MedicineEntity(
    source = source.key,
    extId = extId,
    category = category,
    name = name,
    productNumber = productNumber,
    status = status,
    opinionStatus = opinionStatus,
    inn = inn,
    activeSubstance = activeSubstance,
    therapeuticArea = therapeuticArea,
    atcCode = atcCode,
    pharmacotherapeuticGroup = pharmacotherapeuticGroup,
    therapeuticIndication = therapeuticIndication,
    marketingAuthorisationHolder = marketingAuthorisationHolder,
    species = species,
    pharmaceuticalForm = pharmaceuticalForm,
    route = route,
    prescription = prescription,
    hasRcp = hasRcp,
    rcpUrl = rcpUrl,
    additionalMonitoring = additionalMonitoring,
    advancedTherapy = advancedTherapy,
    biosimilar = biosimilar,
    conditionalApproval = conditionalApproval,
    exceptionalCircumstances = exceptionalCircumstances,
    generic = generic,
    orphan = orphan,
    prime = prime,
    acceleratedAssessment = acceleratedAssessment,
    marketingAuthorisationDate = marketingAuthorisationDate,
    ecDecisionDate = ecDecisionDate,
    revisionNumber = revisionNumber,
    firstPublished = firstPublished,
    lastUpdated = lastUpdated,
    url = url,
)

fun MedicineEntity.toModel() = Medicine(
    source = Source.fromKey(source),
    extId = extId,
    category = category,
    name = name,
    productNumber = productNumber,
    status = status,
    opinionStatus = opinionStatus,
    inn = inn,
    activeSubstance = activeSubstance,
    therapeuticArea = therapeuticArea,
    atcCode = atcCode,
    pharmacotherapeuticGroup = pharmacotherapeuticGroup,
    therapeuticIndication = therapeuticIndication,
    marketingAuthorisationHolder = marketingAuthorisationHolder,
    species = species,
    pharmaceuticalForm = pharmaceuticalForm,
    route = route,
    prescription = prescription,
    hasRcp = hasRcp,
    rcpUrl = rcpUrl,
    additionalMonitoring = additionalMonitoring,
    advancedTherapy = advancedTherapy,
    biosimilar = biosimilar,
    conditionalApproval = conditionalApproval,
    exceptionalCircumstances = exceptionalCircumstances,
    generic = generic,
    orphan = orphan,
    prime = prime,
    acceleratedAssessment = acceleratedAssessment,
    marketingAuthorisationDate = marketingAuthorisationDate,
    ecDecisionDate = ecDecisionDate,
    revisionNumber = revisionNumber,
    firstPublished = firstPublished,
    lastUpdated = lastUpdated,
    url = url,
)

fun Schedule.toEntity(createdAt: String, updatedAt: String) = ScheduleEntity(
    id = id,
    medSource = medSource.key,
    medExtId = medExtId,
    medName = medName,
    startDate = startDate,
    endMode = endMode.name.lowercase(),
    endDate = endDate,
    doseCount = doseCount,
    periodUnit = periodUnit.name.lowercase(),
    periodN = periodN,
    // Keep the legacy columns populated from the first entry for any reader
    // that still consults them; the authoritative set is in `times`.
    hour = hour,
    minute = minute,
    times = encodeTimes(times),
    windowMinutes = windowMinutes,
    notes = notes,
    active = active,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ScheduleEntity.toModel() = Schedule(
    id = id,
    medSource = Source.fromKey(medSource),
    medExtId = medExtId,
    medName = medName,
    startDate = startDate,
    endMode = when (endMode) {
        "never" -> EndMode.NEVER
        "count" -> EndMode.COUNT
        else -> EndMode.DATE
    },
    endDate = endDate,
    doseCount = doseCount,
    periodUnit = when (periodUnit) {
        "once" -> PeriodUnit.ONCE
        "hours" -> PeriodUnit.HOURS
        "weeks" -> PeriodUnit.WEEKS
        "months" -> PeriodUnit.MONTHS
        "years" -> PeriodUnit.YEARS
        else -> PeriodUnit.DAYS
    },
    periodN = periodN,
    // Prefer the serialised list; fall back to the legacy single (hour, minute).
    times = decodeTimes(times).ifEmpty { listOf(DoseTime(hour = hour, minute = minute)) },
    windowMinutes = windowMinutes,
    notes = notes,
    active = active,
)
