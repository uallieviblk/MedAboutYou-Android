// SPDX-License-Identifier: AGPL-3.0-or-later
package com.uallsi.medaboutyou.data.local

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** The refill shopping list — medicines the user flagged to buy/refill. */
class ShoppingStore(db: MedDatabase) {
    private val dao = db.shoppingDao()

    suspend fun all(): List<ShoppingItemEntity> = dao.all()

    suspend fun add(medKey: String, medName: String) =
        dao.upsert(
            ShoppingItemEntity(
                medKey = medKey,
                medName = medName,
                // Same timestamp format as every other store (ScheduleRepository.nowIso).
                addedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            ),
        )

    suspend fun remove(medKey: String) = dao.remove(medKey)
}
