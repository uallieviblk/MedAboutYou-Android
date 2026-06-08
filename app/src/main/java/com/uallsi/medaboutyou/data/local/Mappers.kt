package com.uallsi.medaboutyou.data.local

import com.uallsi.medaboutyou.model.EndMode
import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.PeriodUnit
import com.uallsi.medaboutyou.model.Schedule
import com.uallsi.medaboutyou.model.Source

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
    hour = hour,
    minute = minute,
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
        "hours" -> PeriodUnit.HOURS
        "weeks" -> PeriodUnit.WEEKS
        else -> PeriodUnit.DAYS
    },
    periodN = periodN,
    hour = hour,
    minute = minute,
    windowMinutes = windowMinutes,
    notes = notes,
    active = active,
)
