package com.petro.smsapp.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.data.CalendarType
import com.petro.smsapp.data.ClockFormat
import com.petro.smsapp.data.NotificationActionSetting
import com.petro.smsapp.data.NotificationActionType
import com.petro.smsapp.data.SwipeAction
import com.petro.smsapp.data.ThemeMode
import com.petro.smsapp.data.db.AppDatabase
import com.petro.smsapp.data.db.DeliveryEntity
import com.petro.smsapp.data.db.FavoriteEntity
import com.petro.smsapp.data.db.FilterGroupEntity
import com.petro.smsapp.data.db.FilterGroupKeywordEntity
import com.petro.smsapp.data.db.FilterGroupMatchedMessageEntity
import com.petro.smsapp.data.db.FilterGroupNumberEntity
import com.petro.smsapp.data.db.FilterGroupPatternEntity
import com.petro.smsapp.data.db.MessageGroupEntity
import com.petro.smsapp.data.db.MessageGroupMemberEntity
import com.petro.smsapp.data.db.PinEntity
import com.petro.smsapp.data.db.PinnedMessageEntity
import com.petro.smsapp.data.db.PrivateNumberEntity
import com.petro.smsapp.data.db.ScheduledMessageEntity
import com.petro.smsapp.data.db.TrashEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class BackupCategory(
    val id: String,
    val title: String,
    val description: String
) {
    APPEARANCE("appearance", "ظاهر", "تم، نوع تقویم و فرمت ساعت"),
    CONVERSATIONS("conversations", "لیست مکالمات", "نمایش شماره، نوار الفبا، سویپ‌ها و سقف پین"),
    MESSAGING("messaging", "پیام‌رسانی", "سطل زباله، گروه‌های پیامکی و تأخیر ارسال"),
    NOTIFICATIONS("notifications", "اعلان‌ها", "دلیوری، دکمه‌های نوتیف و پاپ‌آپ"),
    SMS("sms", "پیامک‌ها", "تمام پیامک‌های موجود در گوشی"),
    APP_DATA("app_data", "داده‌های برنامه", "فیوریت، پین، سطل زباله، گروه‌ها، فیلترها و ...");

    companion object {
        fun fromId(id: String): BackupCategory? = entries.find { it.id == id }
    }
}

object BackupModule {

    private const val SCHEMA_VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_TIMESTAMP = "timestamp"
    private const val KEY_CATEGORIES = "categories"

    suspend fun export(
        context: Context,
        categories: Set<BackupCategory>,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(categories.isNotEmpty()) { "هیچ بخشی انتخاب نشده" }

            val catsJson = JSONObject()
            categories.forEach { cat ->
                catsJson.put(
                    cat.id,
                    when (cat) {
                        BackupCategory.APPEARANCE -> exportAppearance()
                        BackupCategory.CONVERSATIONS -> exportConversations()
                        BackupCategory.MESSAGING -> exportMessaging()
                        BackupCategory.NOTIFICATIONS -> exportNotifications()
                        BackupCategory.SMS -> exportSms(context)
                        BackupCategory.APP_DATA -> exportAppData(context)
                    }
                )
            }

            val root = JSONObject().apply {
                put(KEY_VERSION, SCHEMA_VERSION)
                put(KEY_TIMESTAMP, System.currentTimeMillis())
                put(KEY_CATEGORIES, catsJson)
            }

            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(root.toString(2).toByteArray(Charsets.UTF_8))
            } ?: error("نمی‌توان فایل را نوشت")
        }
    }

    // ─── Appearance / Conversations / Messaging / Notifications ───

    private fun exportAppearance(): JSONObject {
        val s = AppSettings.state.value
        return JSONObject().apply {
            put("themeMode", s.themeMode.name)
            put("calendarType", s.calendarType.name)
            put("clockFormat", s.clockFormat.name)
        }
    }

    private fun exportConversations(): JSONObject {
        val s = AppSettings.state.value
        return JSONObject().apply {
            put("showContactNumberInListEnabled", s.showContactNumberInListEnabled)
            put("alphabetIndexBarEnabled", s.alphabetIndexBarEnabled)
            put("swipeRightToLeftAction", s.swipeRightToLeftAction.id)
            put("swipeLeftToRightAction", s.swipeLeftToRightAction.id)
            put("swipeDeleteRequiresConfirmation", s.swipeDeleteRequiresConfirmation)
            put("maxPinnedConversations", s.maxPinnedConversations)
        }
    }

    private fun exportMessaging(): JSONObject {
        val s = AppSettings.state.value
        return JSONObject().apply {
            put("trashEnabled", s.trashEnabled)
            put("groupMessagingEnabled", s.groupMessagingEnabled)
            put("sendDelaySeconds", s.sendDelaySeconds)
        }
    }

    private fun exportNotifications(): JSONObject {
        val s = AppSettings.state.value
        return JSONObject().apply {
            put("deliveryNotificationsEnabled", s.deliveryNotificationsEnabled)
            put("popupInsteadOfNotificationEnabled", s.popupInsteadOfNotificationEnabled)
            put("markReadOnNotificationDismissEnabled", s.markReadOnNotificationDismissEnabled)
            put("notificationActions", JSONArray().apply {
                s.notificationActions.forEach { action ->
                    put(JSONObject().apply {
                        put("type", action.type.id)
                        put("enabled", action.enabled)
                    })
                }
            })
        }
    }

    // ─── SMS (سیستم) ───

    private fun exportSms(context: Context): JSONObject {
        val messages = JSONArray()
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
                Telephony.Sms.DATE_SENT, Telephony.Sms.TYPE, Telephony.Sms.READ,
                Telephony.Sms.SEEN, Telephony.Sms.STATUS, Telephony.Sms.THREAD_ID,
                Telephony.Sms.PROTOCOL, Telephony.Sms.SERVICE_CENTER
            ),
            null, null, Telephony.Sms.DEFAULT_SORT_ORDER
        )
        cursor?.use {
            val idxAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val idxBody = it.getColumnIndex(Telephony.Sms.BODY)
            val idxDate = it.getColumnIndex(Telephony.Sms.DATE)
            val idxDateSent = it.getColumnIndex(Telephony.Sms.DATE_SENT)
            val idxType = it.getColumnIndex(Telephony.Sms.TYPE)
            val idxRead = it.getColumnIndex(Telephony.Sms.READ)
            val idxSeen = it.getColumnIndex(Telephony.Sms.SEEN)
            val idxStatus = it.getColumnIndex(Telephony.Sms.STATUS)
            val idxThread = it.getColumnIndex(Telephony.Sms.THREAD_ID)
            val idxProtocol = it.getColumnIndex(Telephony.Sms.PROTOCOL)
            val idxService = it.getColumnIndex(Telephony.Sms.SERVICE_CENTER)

            while (it.moveToNext()) {
                messages.put(JSONObject().apply {
                    put("address", it.getString(idxAddress) ?: "")
                    put("body", it.getString(idxBody) ?: "")
                    put("date", it.getLong(idxDate))
                    put("dateSent", if (idxDateSent >= 0) it.getLong(idxDateSent) else 0L)
                    put("type", it.getInt(idxType))
                    put("read", it.getInt(idxRead))
                    put("seen", if (idxSeen >= 0) it.getInt(idxSeen) else 1)
                    put("status", if (idxStatus >= 0) it.getInt(idxStatus) else -1)
                    put("threadId", if (idxThread >= 0) it.getLong(idxThread) else 0L)
                    put("protocol", if (idxProtocol >= 0) it.getInt(idxProtocol) else 0)
                    put("serviceCenter", if (idxService >= 0) it.getString(idxService) else "")
                })
            }
        }
        return JSONObject().put("messages", messages)
    }

    // ─── App Data (Room) ───

    private suspend fun exportAppData(context: Context): JSONObject {
        val db = AppDatabase.getInstance(context)

        // Favorites
        val favorites = db.favoriteDao().observeAll().first()
        val favoritesJson = JSONArray().apply {
            favorites.forEach { f ->
                put(JSONObject().apply {
                    put("messageId", f.messageId)
                    put("threadId", f.threadId)
                    put("address", f.address)
                    put("displayName", f.displayName)
                    put("body", f.body)
                    put("date", f.date)
                })
            }
        }

        // Private numbers
        val privateNumbers = db.privateNumberDao().getAllOnce()
        val privateJson = JSONArray().apply {
            privateNumbers.forEach { p ->
                put(JSONObject().apply {
                    put("normalizedAddress", p.normalizedAddress)
                    put("threadId", p.threadId)
                    put("address", p.address)
                    put("displayName", p.displayName)
                    put("madePrivateAt", p.madePrivateAt)
                })
            }
        }

        // Trash (فقط IDها + timestamp تقریبی)
        val trashIds = db.trashDao().getAllIds()
        val trashJson = JSONArray().apply {
            trashIds.forEach { id ->
                val trashedAt = db.trashDao().getTrashedAt(id) ?: System.currentTimeMillis()
                put(JSONObject().apply {
                    put("messageId", id)
                    put("trashedAt", trashedAt)
                })
            }
        }

        // Pins (conversations)
        val pins = db.pinDao().getAllOnce()
        val pinsJson = JSONArray().apply {
            pins.forEach { p ->
                put(JSONObject().apply {
                    put("threadId", p.threadId)
                    put("pinnedAt", p.pinnedAt)
                })
            }
        }

        // Pinned messages (فقط ID + threadId تقریبی ۰)
        val pinnedMsgIds = db.pinnedMessageDao().observeAllIds().first()
        val pinnedMsgJson = JSONArray().apply {
            pinnedMsgIds.forEach { id ->
                put(JSONObject().apply {
                    put("messageId", id)
                    put("threadId", 0L)
                })
            }
        }

        // Scheduled
        val scheduled = db.scheduledMessageDao().getAllOnce()
        val scheduledJson = JSONArray().apply {
            scheduled.forEach { s ->
                put(JSONObject().apply {
                    put("id", s.id)
                    put("threadId", s.threadId)
                    put("address", s.address)
                    put("displayName", s.displayName)
                    put("body", s.body)
                    put("scheduledAt", s.scheduledAt)
                    put("subscriptionId", s.subscriptionId)
                })
            }
        }

        // Delivery (نیاز به دسترسی مستقیم نداریم – فعلاً خالی می‌ذاریم چون DAO فقط per-id دارد)
        val deliveryJson = JSONArray()

        // Message groups + members
        val groupsWithCount = db.messageGroupDao().observeGroupsWithMemberCount().first()
        val groupsJson = JSONArray()
        groupsWithCount.forEach { g ->
            val members = db.messageGroupDao().getMembers(g.id)
            groupsJson.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("createdAt", g.createdAt)
                put("members", JSONArray().apply {
                    members.forEach { m ->
                        put(JSONObject().apply {
                            put("address", m.address)
                            put("displayName", m.displayName)
                        })
                    }
                })
            })
        }

        // Filter groups + numbers + keywords + patterns + matches
        val filterGroups = db.filterGroupDao().getGroupsOrderedByPriority()
        val filterGroupsJson = JSONArray()
        filterGroups.forEach { g ->
            val numbers = db.filterGroupDao().getNumbersForGroupOnce(g.id)
            val keywords = db.filterGroupDao().getKeywordsForGroupOnce(g.id)
            val patterns = db.filterGroupDao().getPatternsForGroupOnce(g.id)
            val matches = db.filterGroupDao().getMatchesForGroup(g.id)

            filterGroupsJson.put(JSONObject().apply {
                put("id", g.id)
                put("name", g.name)
                put("priority", g.priority)
                put("hideFromMainList", g.hideFromMainList)
                put("showNotifications", g.showNotifications)
                put("blockNonContacts", g.blockNonContacts)
                put("showInNotificationPicker", g.showInNotificationPicker)
                put("isQuickAddTarget", g.isQuickAddTarget)
                put("createdAt", g.createdAt)

                put("numbers", JSONArray().apply {
                    numbers.forEach { n ->
                        put(JSONObject().apply {
                            put("normalizedAddress", n.normalizedAddress)
                            put("address", n.address)
                            put("displayName", n.displayName)
                            put("addedAt", n.addedAt)
                        })
                    }
                })
                put("keywords", JSONArray().apply {
                    keywords.forEach { k ->
                        put(JSONObject().apply {
                            put("id", k.id)
                            put("text", k.text)
                            put("addedAt", k.addedAt)
                        })
                    }
                })
                put("patterns", JSONArray().apply {
                    patterns.forEach { p ->
                        put(JSONObject().apply {
                            put("id", p.id)
                            put("type", p.type)
                            put("value", p.value)
                            put("addedAt", p.addedAt)
                        })
                    }
                })
                put("matches", JSONArray().apply {
                    matches.forEach { m ->
                        put(JSONObject().apply {
                            put("messageId", m.messageId)
                            put("matchType", m.matchType)
                            put("matchedValue", m.matchedValue)
                            put("matchedAt", m.matchedAt)
                        })
                    }
                })
            })
        }

        return JSONObject().apply {
            put("favorites", favoritesJson)
            put("privateNumbers", privateJson)
            put("trash", trashJson)
            put("pins", pinsJson)
            put("pinnedMessages", pinnedMsgJson)
            put("scheduled", scheduledJson)
            put("delivery", deliveryJson)
            put("messageGroups", groupsJson)
            put("filterGroups", filterGroupsJson)
        }
    }

    // ─── Import ───

    suspend fun import(
        context: Context,
        uri: Uri
    ): Result<Set<BackupCategory>> = withContext(Dispatchers.IO) {
        runCatching {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: error("نمی‌توان فایل را خواند")

            val root = JSONObject(json)
            val version = root.optInt(KEY_VERSION, 0)
            if (version > SCHEMA_VERSION) error("نسخه فایل بک‌آپ جدیدتر از اپ است")

            val catsJson = root.optJSONObject(KEY_CATEGORIES)
                ?: error("فرمت فایل نامعتبر است")

            val restored = mutableSetOf<BackupCategory>()
            catsJson.keys().forEach { key ->
                val cat = BackupCategory.fromId(key) ?: return@forEach
                val data = catsJson.getJSONObject(key)
                when (cat) {
                    BackupCategory.APPEARANCE -> importAppearance(context, data)
                    BackupCategory.CONVERSATIONS -> importConversations(context, data)
                    BackupCategory.MESSAGING -> importMessaging(context, data)
                    BackupCategory.NOTIFICATIONS -> importNotifications(context, data)
                    BackupCategory.SMS -> importSms(context, data)
                    BackupCategory.APP_DATA -> importAppData(context, data)
                }
                restored.add(cat)
            }
            if (restored.isEmpty()) error("هیچ بخش معتبری در فایل نبود")
            restored
        }
    }

    private fun importAppearance(context: Context, data: JSONObject) {
        data.optString("themeMode").takeIf { it.isNotBlank() }?.let {
            runCatching { ThemeMode.valueOf(it) }.getOrNull()?.let { AppSettings.setThemeMode(context, it) }
        }
        data.optString("calendarType").takeIf { it.isNotBlank() }?.let {
            runCatching { CalendarType.valueOf(it) }.getOrNull()?.let { AppSettings.setCalendarType(context, it) }
        }
        data.optString("clockFormat").takeIf { it.isNotBlank() }?.let {
            runCatching { ClockFormat.valueOf(it) }.getOrNull()?.let { AppSettings.setClockFormat(context, it) }
        }
    }

    private fun importConversations(context: Context, data: JSONObject) {
        if (data.has("showContactNumberInListEnabled"))
            AppSettings.setShowContactNumberInListEnabled(context, data.getBoolean("showContactNumberInListEnabled"))
        if (data.has("alphabetIndexBarEnabled"))
            AppSettings.setAlphabetIndexBarEnabled(context, data.getBoolean("alphabetIndexBarEnabled"))
        data.optString("swipeRightToLeftAction").takeIf { it.isNotBlank() }?.let {
            AppSettings.setSwipeRightToLeftAction(context, SwipeAction.fromId(it, AppSettings.DEFAULT_SWIPE_RIGHT_TO_LEFT_ACTION))
        }
        data.optString("swipeLeftToRightAction").takeIf { it.isNotBlank() }?.let {
            AppSettings.setSwipeLeftToRightAction(context, SwipeAction.fromId(it, AppSettings.DEFAULT_SWIPE_LEFT_TO_RIGHT_ACTION))
        }
        if (data.has("swipeDeleteRequiresConfirmation"))
            AppSettings.setSwipeDeleteRequiresConfirmation(context, data.getBoolean("swipeDeleteRequiresConfirmation"))
        if (data.has("maxPinnedConversations"))
            AppSettings.setMaxPinnedConversations(context, data.getInt("maxPinnedConversations"))
    }

    private fun importMessaging(context: Context, data: JSONObject) {
        if (data.has("trashEnabled"))
            AppSettings.setTrashEnabled(context, data.getBoolean("trashEnabled"))
        if (data.has("groupMessagingEnabled"))
            AppSettings.setGroupMessagingEnabled(context, data.getBoolean("groupMessagingEnabled"))
        if (data.has("sendDelaySeconds"))
            AppSettings.setSendDelaySeconds(context, data.getInt("sendDelaySeconds"))
    }

    private fun importNotifications(context: Context, data: JSONObject) {
        if (data.has("deliveryNotificationsEnabled"))
            AppSettings.setDeliveryNotificationsEnabled(context, data.getBoolean("deliveryNotificationsEnabled"))
        if (data.has("popupInsteadOfNotificationEnabled"))
            AppSettings.setPopupInsteadOfNotificationEnabled(context, data.getBoolean("popupInsteadOfNotificationEnabled"))
        if (data.has("markReadOnNotificationDismissEnabled"))
            AppSettings.setMarkReadOnNotificationDismissEnabled(context, data.getBoolean("markReadOnNotificationDismissEnabled"))
        if (data.has("notificationActions")) {
            val arr = data.getJSONArray("notificationActions")
            val list = mutableListOf<NotificationActionSetting>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val type = NotificationActionType.fromId(obj.getString("type")) ?: continue
                list.add(NotificationActionSetting(type, obj.getBoolean("enabled")))
            }
            if (list.isNotEmpty()) AppSettings.setNotificationActionSettings(context, list)
        }
    }

    private fun importSms(context: Context, data: JSONObject) {
        val messages = data.optJSONArray("messages") ?: return
        val resolver = context.contentResolver
        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, msg.optString("address"))
                put(Telephony.Sms.BODY, msg.optString("body"))
                put(Telephony.Sms.DATE, msg.optLong("date"))
                put(Telephony.Sms.DATE_SENT, msg.optLong("dateSent"))
                put(Telephony.Sms.TYPE, msg.optInt("type"))
                put(Telephony.Sms.READ, msg.optInt("read"))
                put(Telephony.Sms.SEEN, msg.optInt("seen", 1))
                put(Telephony.Sms.STATUS, msg.optInt("status", -1))
                if (msg.has("threadId")) put(Telephony.Sms.THREAD_ID, msg.optLong("threadId"))
                put(Telephony.Sms.PROTOCOL, msg.optInt("protocol", 0))
                put(Telephony.Sms.SERVICE_CENTER, msg.optString("serviceCenter"))
            }
            try {
                resolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (_: Exception) { }
        }
    }

    private suspend fun importAppData(context: Context, data: JSONObject) {
        val db = AppDatabase.getInstance(context)

        // Favorites
        db.favoriteDao().observeAll().first().forEach { db.favoriteDao().delete(it.messageId) }
        data.optJSONArray("favorites")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.favoriteDao().insert(
                    FavoriteEntity(
                        messageId = o.getLong("messageId"),
                        threadId = o.getLong("threadId"),
                        address = o.optString("address"),
                        displayName = o.optString("displayName"),
                        body = o.optString("body"),
                        date = o.getLong("date")
                    )
                )
            }
        }

        // Private numbers
        db.privateNumberDao().getAllOnce().forEach { db.privateNumberDao().deleteByKey(it.normalizedAddress) }
        data.optJSONArray("privateNumbers")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.privateNumberDao().insertIfAbsent(
                    PrivateNumberEntity(
                        normalizedAddress = o.getString("normalizedAddress"),
                        threadId = o.getLong("threadId"),
                        address = o.optString("address"),
                        displayName = o.optString("displayName"),
                        madePrivateAt = o.getLong("madePrivateAt")
                    )
                )
            }
        }

        // Trash
        db.trashDao().getAllIds().forEach { db.trashDao().delete(it) }
        data.optJSONArray("trash")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.trashDao().insert(
                    TrashEntity(
                        messageId = o.getLong("messageId"),
                        trashedAt = o.getLong("trashedAt")
                    )
                )
            }
        }

        // Pins
        db.pinDao().getAllOnce().forEach { db.pinDao().delete(it.threadId) }
        data.optJSONArray("pins")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.pinDao().insert(
                    PinEntity(
                        threadId = o.getLong("threadId"),
                        pinnedAt = o.getLong("pinnedAt")
                    )
                )
            }
        }

        // Pinned messages
        db.pinnedMessageDao().observeAllIds().first().forEach { db.pinnedMessageDao().delete(it) }
        data.optJSONArray("pinnedMessages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.pinnedMessageDao().insert(
                    PinnedMessageEntity(
                        messageId = o.getLong("messageId"),
                        threadId = o.optLong("threadId", 0L)
                    )
                )
            }
        }

        // Scheduled
        db.scheduledMessageDao().getAllOnce().forEach { db.scheduledMessageDao().delete(it.id) }
        data.optJSONArray("scheduled")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                db.scheduledMessageDao().insert(
                    ScheduledMessageEntity(
                        id = o.getLong("id"),
                        threadId = o.getLong("threadId"),
                        address = o.optString("address"),
                        displayName = o.optString("displayName"),
                        body = o.optString("body"),
                        scheduledAt = o.getLong("scheduledAt"),
                        subscriptionId = o.optInt("subscriptionId", -1)
                    )
                )
            }
        }

        // Message groups
        val existingGroups = db.messageGroupDao().observeGroupsWithMemberCount().first()
        existingGroups.forEach {
            db.messageGroupDao().deleteMembers(it.id)
            db.messageGroupDao().deleteGroup(it.id)
        }
        data.optJSONArray("messageGroups")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val newId = db.messageGroupDao().insertGroup(
                    MessageGroupEntity(
                        name = o.getString("name"),
                        createdAt = o.getLong("createdAt")
                    )
                )
                val membersArr = o.optJSONArray("members") ?: continue
                val members = mutableListOf<MessageGroupMemberEntity>()
                for (j in 0 until membersArr.length()) {
                    val m = membersArr.getJSONObject(j)
                    members.add(
                        MessageGroupMemberEntity(
                            groupId = newId,
                            address = m.optString("address"),
                            displayName = m.optString("displayName")
                        )
                    )
                }
                if (members.isNotEmpty()) db.messageGroupDao().insertMembers(members)
            }
        }

        // Filter groups (کامل)
        val existingFilterGroups = db.filterGroupDao().getGroupsOrderedByPriority()
        existingFilterGroups.forEach { g ->
            // پاک کردن زیرمجموعه‌ها
            db.filterGroupDao().getNumbersForGroupOnce(g.id).forEach {
                db.filterGroupDao().deleteNumber(g.id, it.normalizedAddress)
            }
            db.filterGroupDao().getKeywordsForGroupOnce(g.id).forEach {
                db.filterGroupDao().deleteKeyword(it.id)
            }
            db.filterGroupDao().getPatternsForGroupOnce(g.id).forEach {
                db.filterGroupDao().deletePattern(it.id)
            }
            db.filterGroupDao().getMatchesForGroup(g.id).forEach {
                db.filterGroupDao().deleteMatch(it.messageId)
            }
            db.filterGroupDao().deleteGroup(g.id)
        }

        data.optJSONArray("filterGroups")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val newId = db.filterGroupDao().insertGroup(
                    FilterGroupEntity(
                        name = o.getString("name"),
                        priority = o.getInt("priority"),
                        hideFromMainList = o.getBoolean("hideFromMainList"),
                        showNotifications = o.getBoolean("showNotifications"),
                        blockNonContacts = o.getBoolean("blockNonContacts"),
                        showInNotificationPicker = o.optBoolean("showInNotificationPicker", true),
                        isQuickAddTarget = o.optBoolean("isQuickAddTarget", false),
                        createdAt = o.getLong("createdAt")
                    )
                )

                // numbers
                o.optJSONArray("numbers")?.let { narr ->
                    for (j in 0 until narr.length()) {
                        val n = narr.getJSONObject(j)
                        db.filterGroupDao().insertNumberIfAbsent(
                            FilterGroupNumberEntity(
                                groupId = newId,
                                normalizedAddress = n.getString("normalizedAddress"),
                                address = n.optString("address"),
                                displayName = n.optString("displayName"),
                                addedAt = n.getLong("addedAt")
                            )
                        )
                    }
                }

                // keywords
                o.optJSONArray("keywords")?.let { karr ->
                    for (j in 0 until karr.length()) {
                        val k = karr.getJSONObject(j)
                        db.filterGroupDao().insertKeyword(
                            FilterGroupKeywordEntity(
                                id = k.getString("id"),
                                groupId = newId,
                                text = k.getString("text"),
                                addedAt = k.getLong("addedAt")
                            )
                        )
                    }
                }

                // patterns
                o.optJSONArray("patterns")?.let { parr ->
                    for (j in 0 until parr.length()) {
                        val p = parr.getJSONObject(j)
                        db.filterGroupDao().insertPattern(
                            FilterGroupPatternEntity(
                                id = p.getString("id"),
                                groupId = newId,
                                type = p.getString("type"),
                                value = p.getString("value"),
                                addedAt = p.getLong("addedAt")
                            )
                        )
                    }
                }

                // matches
                o.optJSONArray("matches")?.let { marr ->
                    val matches = mutableListOf<FilterGroupMatchedMessageEntity>()
                    for (j in 0 until marr.length()) {
                        val m = marr.getJSONObject(j)
                        matches.add(
                            FilterGroupMatchedMessageEntity(
                                messageId = m.getLong("messageId"),
                                groupId = newId,
                                matchType = m.getString("matchType"),
                                matchedValue = m.optString("matchedValue").takeIf { it.isNotBlank() },
                                matchedAt = m.getLong("matchedAt")
                            )
                        )
                    }
                    if (matches.isNotEmpty()) db.filterGroupDao().insertMatches(matches)
                }
            }
        }
    }

    fun suggestedFileName(): String {
        val date = java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "smsapp_backup_$date.json"
    }
}