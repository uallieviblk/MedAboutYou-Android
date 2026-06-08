package com.uallsi.medaboutyou.data.local

import com.uallsi.medaboutyou.model.Medicine
import com.uallsi.medaboutyou.model.Source

/** Cached EMA records, the meta block and stock — the local medicines store. */
class MedicineStore(db: MedDatabase) {
    private val medicineDao = db.medicineDao()
    private val metaDao = db.metaDao()
    private val inventoryDao = db.inventoryDao()

    suspend fun upsertAll(meds: List<Medicine>) =
        medicineDao.upsertAll(meds.map { it.toEntity() })

    suspend fun search(source: Source, query: String, humanOnly: Boolean, limit: Int): List<Medicine> =
        medicineDao.search(source.key, query, if (humanOnly) 1 else 0, limit).map { it.toModel() }

    suspend fun count(source: Source): Int = medicineDao.count(source.key)

    suspend fun find(source: Source, extId: String): Medicine? =
        medicineDao.find(source.key, extId)?.toModel()

    suspend fun getMeta(key: String): String? = metaDao.get(key)
    suspend fun setMeta(key: String, value: String) = metaDao.set(MetaEntity(key, value))

    // --- Stock (mirrors MedicineDatabase inventory; key = "source:ext" or "name:<lower>") ---

    private fun stockKey(source: Source, ext: String, name: String): String =
        if (ext.isNotEmpty()) "${source.key}:$ext" else "name:${name.lowercase()}"

    suspend fun availableDoses(source: Source, ext: String, name: String): Int =
        inventoryDao.doses(stockKey(source, ext, name)) ?: 0

    suspend fun setDoses(source: Source, ext: String, name: String, count: Int) =
        inventoryDao.set(InventoryEntity(stockKey(source, ext, name), maxOf(0, count)))

    suspend fun adjustDoses(source: Source, ext: String, name: String, delta: Int) {
        val key = stockKey(source, ext, name)
        val current = inventoryDao.doses(key) ?: 0
        inventoryDao.set(InventoryEntity(key, maxOf(0, current + delta)))
    }
}
