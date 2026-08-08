package com.petro.smsapp.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * دیتابیس اصلی اپ - جایگزین همه‌ی SharedPreferences Storeهای قبلی + گروه‌های پیامکی +
 * ماژولِ عمومیِ «گروهِ فیلتر» (جایگزینِ بخشِ قدیمیِ «بلاک»).
 */
@Database(
    entities = [
        FavoriteEntity::class,
        PrivateNumberEntity::class,
        TrashEntity::class,
        PinEntity::class,
        PinnedMessageEntity::class,
        ScheduledMessageEntity::class,
        DeliveryEntity::class,
        MessageGroupEntity::class,
        MessageGroupMemberEntity::class,
        FilterGroupEntity::class,
        FilterGroupNumberEntity::class,
        FilterGroupKeywordEntity::class,
        FilterGroupPatternEntity::class,
        FilterGroupMatchedMessageEntity::class,
        TemplateEntity::class,
        ShortcutContactEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun privateNumberDao(): PrivateNumberDao
    abstract fun trashDao(): TrashDao
    abstract fun pinDao(): PinDao
    abstract fun pinnedMessageDao(): PinnedMessageDao
    abstract fun scheduledMessageDao(): ScheduledMessageDao
    abstract fun deliveryDao(): DeliveryDao
    abstract fun messageGroupDao(): MessageGroupDao
    abstract fun filterGroupDao(): FilterGroupDao
    abstract fun templateDao(): TemplateDao
    abstract fun shortcutContactDao(): ShortcutContactDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null


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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pinned_messages ADD COLUMN threadId INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * نسخه‌ی ۳ -> ۴: کلِ بخشِ قدیمیِ «بلاک» (blocked_numbers، block_keywords،
         * block_patterns، و سه جدولِ ردیابیِ پیام‌های بلاک‌شده) حذف و جاش ماژولِ عمومیِ
         * «گروهِ فیلتر» نشست. چون این یه بازطراحیِ کاملِ اسکیماست (نه یه تغییرِ کوچیک)
         * و طبقِ گفته‌ی خودِ پروژه هنوز پروداکت نرفته، به‌جای نوشتنِ Migration دستیِ
         * پیچیده برای انتقالِ داده‌های قدیمیِ بلاک، از fallbackToDestructiveMigration
         * استفاده شده - یعنی موقعِ بالا اومدنِ اپ با این نسخه‌ی جدید، دیتابیسِ محلیِ قبلی
         * پاک و از نو ساخته میشه (فقط دیتابیسِ SMS/دستگاه دست‌نخورده می‌مونه، چون اون
         * اصلاً تو Room نیست).
         */

        /**
         * نسخه‌ی ۴ -> ۵: افزودنِ ستونِ showInNotificationPicker به filter_groups - تعیینِ
         * اینکه یه گروه تویِ شیتِ انتخابِ گروهِ دکمه‌ی «افزودن به گروه»ِ روی نوتیفیکیشن
         * نشون داده بشه یا نه. پیش‌فرض ۱ (true) تا گروه‌های از قبل ساخته‌شده هم مثلِ قبل
         * تویِ اون شیت دیده بشن.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE filter_groups ADD COLUMN showInNotificationPicker INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * نسخه‌ی ۵ -> ۶: افزودنِ ستونِ isQuickAddTarget به filter_groups - مشخص می‌کنه
         * کدوم گروه مقصدِ دکمه‌ی جدیدِ «افزودن سریع به گروه»ِ روی نوتیفه (بدونِ بازشدنِ
         * اپ یا نمایشِ شیتِ انتخابِ گروه). پیش‌فرض ۰ (false) - یعنی بعد از آپدیت، تا
         * وقتی کاربر از تنظیماتِ خودِ یکی از گروه‌ها این گزینه رو روشن نکنه، هیچ گروهی
         * هدف نیست و اون دکمه عملاً کاری انجام نمیده.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE filter_groups ADD COLUMN isQuickAddTarget INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * نسخه‌ی ۶ -> ۷: جدول تمپلیت‌های متنی برای استفاده در باکس ارسال پیام.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_templates` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`body` TEXT NOT NULL, " +
                            "`createdAt` INTEGER NOT NULL, " +
                            "`updatedAt` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * نسخه‌ی ۷ -> ۸: جدول مخاطبین شورتکات لانچر (لانگ‌کلیک روی آیکون اپ).
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `shortcut_contacts` (" +
                            "`normalizedAddress` TEXT NOT NULL, " +
                            "`address` TEXT NOT NULL, " +
                            "`displayName` TEXT NOT NULL, " +
                            "`addedAt` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`normalizedAddress`))"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_app.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }

    }
}
