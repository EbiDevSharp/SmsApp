package com.petro.smsapp.data

import android.content.Context
import com.petro.smsapp.data.db.AppDatabase
import com.petro.smsapp.data.repository.BlockRepository
import com.petro.smsapp.data.repository.DeliveryRepository
import com.petro.smsapp.data.repository.FavoriteRepository
import com.petro.smsapp.data.repository.MessageGroupRepository
import com.petro.smsapp.data.repository.PinRepository
import com.petro.smsapp.data.repository.PrivateRepository
import com.petro.smsapp.data.repository.ScheduledMessageRepository
import com.petro.smsapp.data.repository.TrashRepository

/**
 * نقطه‌ی واحدِ ساختِ Repositoryها - چون پروژه از قبل هیچ DI framework ای (مثل Hilt)
 * نداره و سبک غالبش singleton object هاست، به‌جای اضافه‌کردن یه فریم‌ورک جدید، همون
 * الگو رو برای Repositoryهای جدید هم رعایت کردیم. همه‌جا (ViewModel، BroadcastReceiver ها)
 * از همینجا Repository می‌گیرن، پس همیشه دقیقاً یه instance از هرکدوم وجود داره.
 */
object AppContainer {
    @Volatile private var db: AppDatabase? = null

    private fun database(context: Context): AppDatabase =
        db ?: synchronized(this) {
            db ?: AppDatabase.getInstance(context.applicationContext).also { db = it }
        }

    fun favoriteRepository(context: Context) = FavoriteRepository(database(context).favoriteDao())

    fun blockRepository(context: Context): BlockRepository {
        val d = database(context)
        return BlockRepository(
            numberDao = d.blockedNumberDao(),
            keywordDao = d.blockKeywordDao(),
            patternDao = d.blockPatternDao(),
            keywordMessageDao = d.blockedKeywordMessageDao(),
            patternMessageDao = d.blockedPatternMessageDao(),
            nonContactMessageDao = d.blockedNonContactMessageDao()
        )
    }

    fun privateRepository(context: Context) = PrivateRepository(database(context).privateNumberDao())

    fun trashRepository(context: Context) = TrashRepository(database(context).trashDao())

    fun pinRepository(context: Context): PinRepository {
        val d = database(context)
        return PinRepository(d.pinDao(), d.pinnedMessageDao())
    }

    fun scheduledMessageRepository(context: Context) = ScheduledMessageRepository(database(context).scheduledMessageDao())

    fun deliveryRepository(context: Context) = DeliveryRepository(database(context).deliveryDao())

    fun messageGroupRepository(context: Context) = MessageGroupRepository(database(context).messageGroupDao())
}
