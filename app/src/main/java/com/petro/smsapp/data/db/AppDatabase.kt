package com.petro.smsapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * دیتابیس اصلی اپ - جایگزین همه‌ی SharedPreferences Storeهای قبلی (Favorite/Block/
 * Private/Trash/Pin/Scheduled/Delivery) + گروه‌های پیامکی. یه instance واحد و
 * singleton، بدون هیچ کش میانی اضافه - خودِ Room منبعِ واقعیِ داده‌ست و از طریق
 * Flow مستقیم reactive میشه.
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
        DeliveryEntity::class,
        MessageGroupEntity::class,
        MessageGroupMemberEntity::class
    ],
    version = 3,
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
    abstract fun messageGroupDao(): MessageGroupDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * نسخه‌ی ۱ -> ۲: اضافه شدنِ جدول‌های «گروه‌های پیامکی» (message_groups) و
         * «اعضای گروه» (message_group_members). عمداً یه Migration دستی نوشته شده
         * (نه fallbackToDestructiveMigration) چون پاک کردنِ کل دیتابیس یعنی از دست
         * رفتنِ فیوریت‌ها/بلاک‌ها/پین‌ها/... کاربرهایی که از قبل از اپ استفاده می‌کردن.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_groups` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_group_members` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`groupId` INTEGER NOT NULL, " +
                            "`address` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL)"
                )
            }
        }

        /**
         * نسخه‌ی ۲ -> ۳: اضافه شدنِ ستونِ threadId به جدولِ pinned_messages (پین یه پیامِ
         * خاص داخل چت). قبلاً این جدول فقط messageId داشت و هیچ‌جا مشخص نمی‌کرد این پیامِ
         * پین‌شده مالِ کدوم مکالمه‌ست - برای فیلترِ جدیدِ «دارای پیام سنجاق‌شده» توی
         * آکاردئونِ درآور، نیاز شد بشه بدونِ کوئری اضافه به Telephony Provider فهمید کدوم
         * threadId ها حداقل یه پیامِ پین‌شده دارن.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pinned_messages ADD COLUMN threadId INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_app.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }
    }
}