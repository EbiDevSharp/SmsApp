package com.petro.smsapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * دیتابیس اصلی اپ - جایگزین همه‌ی SharedPreferences Storeهای قبلی (Favorite/Block/
 * Private/Trash/Pin/Scheduled/Delivery). یه instance واحد و singleton، بدون هیچ کش
 * میانی اضافه - خودِ Room منبعِ واقعیِ داده‌ست و از طریق Flow مستقیم reactive میشه.
 */
@Database(
    entities = [
        FavoriteEntity::class,
        BlockedNumberEntity::class,
        PrivateNumberEntity::class,
        BlockKeywordEntity::class,
        BlockPatternEntity::class,
        BlockedKeywordMessageEntity::class,
        BlockedPatternMessageEntity::class,
        BlockedNonContactMessageEntity::class,
        TrashEntity::class,
        PinEntity::class,
        PinnedMessageEntity::class,
        ScheduledMessageEntity::class,
        DeliveryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun privateNumberDao(): PrivateNumberDao
    abstract fun blockKeywordDao(): BlockKeywordDao
    abstract fun blockPatternDao(): BlockPatternDao
    abstract fun blockedKeywordMessageDao(): BlockedKeywordMessageDao
    abstract fun blockedPatternMessageDao(): BlockedPatternMessageDao
    abstract fun blockedNonContactMessageDao(): BlockedNonContactMessageDao
    abstract fun trashDao(): TrashDao
    abstract fun pinDao(): PinDao
    abstract fun pinnedMessageDao(): PinnedMessageDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun deliveryDao(): DeliveryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_app.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
