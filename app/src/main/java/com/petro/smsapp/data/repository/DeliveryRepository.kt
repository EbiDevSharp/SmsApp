package com.petro.smsapp.data.repository

import com.petro.smsapp.data.db.DeliveryDao
import com.petro.smsapp.data.db.DeliveryEntity

/** جایگزین DeliveryStore - زمان دقیق تحویل هر پیام (Sms provider همچین ستونی نداره) */
class DeliveryRepository(private val dao: DeliveryDao) {

    suspend fun setDeliveredAt(messageId: Long, deliveredAtMillis: Long) =
        dao.insert(DeliveryEntity(messageId, deliveredAtMillis))

    suspend fun getDeliveredAt(messageId: Long): Long = dao.getDeliveredAt(messageId) ?: 0L

    suspend fun clear(messageId: Long) = dao.delete(messageId)
}
