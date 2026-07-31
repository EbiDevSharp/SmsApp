package com.petro.smsapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.data.ContactsCache
import com.petro.smsapp.data.ContactsRepository
import com.petro.smsapp.ui.AppDrawerContent
import com.petro.smsapp.ui.AddBlockedNumberScreen
import com.petro.smsapp.ui.AddBlockedSenderScreen
import com.petro.smsapp.ui.AddPrivateNumberScreen
import com.petro.smsapp.ui.BlockScreen
import com.petro.smsapp.ui.BlockKeywordsScreen
import com.petro.smsapp.ui.BlockPatternsScreen
import com.petro.smsapp.ui.BlockSettingsScreen
import com.petro.smsapp.ui.BlockedMessagesScreen
import com.petro.smsapp.ui.BlockedNumbersScreen
import com.petro.smsapp.ui.ContactPickerScreen
import com.petro.smsapp.ui.ConversationListScreen
import com.petro.smsapp.ui.FavoritesScreen
import com.petro.smsapp.ui.NewMessageScreen
import com.petro.smsapp.ui.NoteScreen
import com.petro.smsapp.ui.PlaceholderScreen
import com.petro.smsapp.ui.PrivateMessagesScreen
import com.petro.smsapp.ui.PrivateNumbersScreen
import com.petro.smsapp.ui.PrivatePinScreen
import com.petro.smsapp.ui.PrivatePinSettingsScreen
import com.petro.smsapp.ui.PrivateScreen
import com.petro.smsapp.data.AppSettings
import com.petro.smsapp.ui.NotificationActionsSettingsScreen
import com.petro.smsapp.ui.SettingsScreen
import com.petro.smsapp.ui.SmsAppTheme
import com.petro.smsapp.ui.ThreadScreen
import com.petro.smsapp.ui.TrashScreen
import com.petro.smsapp.ui.ScheduledMessagesScreen
import com.petro.smsapp.viewmodel.SmsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.petro.smsapp.data.ThemeMode
import com.petro.smsapp.ui.SmsAppTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SmsViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            viewModel.loadConversations()
            viewModel.loadSims()
        }
    }

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionsAndLoad()
    }

    private val requestExactAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> handlePickedContact(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmsAppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background)
                    {
                        AppNavigation(
                            viewModel = viewModel,
                            onPickContactClick = {
                                pickContactLauncher.launch(
                                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                )
                            },
                            onOpenContactInfo = { address -> openContactInfo(address) }
                        )
                    }
                }
            }
        }

        if (!DefaultSmsAppHelper.isDefaultSmsApp(this)) {
            requestRoleLauncher.launch(DefaultSmsAppHelper.getRequestRoleIntent(this))
        } else {
            checkPermissionsAndLoad()
        }

        handleNotificationIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppForegrounded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        intent ?: return

        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1L)
        if (threadId == -1L) return

        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME) ?: address

        viewModel.openThreadFromNotification(
            threadId = threadId,
            address = address,
            displayName = displayName
        )
    }

    private fun checkPermissionsAndLoad() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
        requestExactAlarmPermissionIfNeeded()
    }

    private fun requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.app.AlarmManager::class.java)
            if (alarmManager?.canScheduleExactAlarms() == false) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                requestExactAlarmLauncher.launch(intent)
            }
        }
    }

    private fun handlePickedContact(uri: Uri) {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIdx = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
            if (idIdx < 0 || hasPhoneIdx < 0 || cursor.getInt(hasPhoneIdx) <= 0) return@use

            val contactId = cursor.getLong(idIdx)
            val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "" else ""

            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { phoneCursor ->
                if (phoneCursor.moveToFirst()) {
                    val number = phoneCursor.getString(0) ?: return@use
                    viewModel.setPickedContact(ContactInfo(contactId, name.ifBlank { number }, number))
                }
            }
        }
    }

    /**
     * دکمه‌ی مخاطبِ بالای صفحه‌ی چت این تابع رو صدا می‌زنه. اول با یه کوئریِ سبک (روی
     * Dispatchers.IO) چک می‌کنه آیا این آدرس تو مخاطبینِ گوشی هست یا نه:
     * - اگه بود -> Intent.ACTION_VIEW با lookupUri واقعیِ مخاطب، صفحه‌ی کاملِ خودِ اپ
     *   مخاطبین (با عکس، همه‌ی شماره‌ها، ایمیل و بقیه‌ی فیلدها) باز میشه.
     * - اگه نبود -> Intent.ACTION_INSERT با شماره‌ی از پیش پرشده، صفحه‌ی «افزودن مخاطب
     *   جدید»ِ خودِ اپ مخاطبین باز میشه.
     */
    private fun openContactInfo(address: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val lookupUri = ContactsRepository(this@MainActivity).getContactLookupUri(address)
            withContext(Dispatchers.Main) {
                if (lookupUri != null) {
                    startActivity(Intent(Intent.ACTION_VIEW, lookupUri))
                } else {
                    val insertIntent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        type = ContactsContract.RawContacts.CONTENT_TYPE
                        putExtra(ContactsContract.Intents.Insert.PHONE, address)
                    }
                    try {
                        startActivity(insertIntent)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "برنامه‌ی مخاطبین پیدا نشد", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
    }
}

@Composable
fun AppNavigation(
    viewModel: SmsViewModel,
    onPickContactClick: () -> Unit,
    onOpenContactInfo: (String) -> Unit
) {
    val navController = rememberNavController()
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val scheduledMessages by viewModel.scheduledMessages.collectAsState()
    val draftText by viewModel.draftText.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val allContactsForPicker by viewModel.allContactsForPicker.collectAsState()
    val newTarget by viewModel.newConversationTarget.collectAsState()
    val pickedContact by viewModel.pickedContact.collectAsState()
    val pickedContactsBatch by viewModel.pickedContactsBatch.collectAsState()
    val sims by viewModel.sims.collectAsState()
    val noteText by viewModel.noteText.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val pinnedMessageIds by viewModel.pinnedMessageIds.collectAsState()
    val trash by viewModel.trash.collectAsState()
    val blockedNumbers by viewModel.blockedNumbers.collectAsState()
    val blockedMessages by viewModel.blockedMessages.collectAsState()
    val blockKeywords by viewModel.blockKeywords.collectAsState()
    val blockPatterns by viewModel.blockPatterns.collectAsState()
    val appSettings by AppSettings.state.collectAsState()
    val privateNumbers by viewModel.privateNumbers.collectAsState()
    val privateMessages by viewModel.privateMessages.collectAsState()
    val privateUnlocked by viewModel.privateUnlocked.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val allScheduledMessages by viewModel.allScheduledMessages.collectAsState()
    val groupSummaries by viewModel.groupSummaries.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && currentRoute == "private") {
        viewModel.lockPrivate()
        navController.popBackStack()
    }

    LaunchedEffect(Unit) {
        viewModel.loadBlockedMessages()
    }

    LaunchedEffect(operationMessage) {
        operationMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeOperationMessage()
        }
    }

    LaunchedEffect(newTarget) {
        val target = newTarget
        if (target != null) {
            viewModel.loadThread(target.threadId)
            navController.navigate("list") {
                popUpTo("list") { inclusive = true }
            }
            navController.navigate("thread/${target.threadId}/${Uri.encode(target.address)}/${Uri.encode(target.displayName)}")
            viewModel.consumeNewConversationTarget()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                val settings by AppSettings.state.collectAsState()
                val isDarkTheme = when (settings.themeMode) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                }
                AppDrawerContent(
                    currentRoute = currentRoute,
                    onItemClick = { route ->
                        scope.launch {
                            drawerState.close()
                            if (route != currentRoute) {
                                if (currentRoute == "private") {
                                    viewModel.lockPrivate()
                                }
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    },
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        val newMode = if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                        AppSettings.setThemeMode(context, newMode)
                    }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "list") {
            composable("list") {
                ConversationListScreen(
                    conversations = conversations,
                    onConversationClick = { conversation ->
                        if (conversation.address.isNotBlank()) {
                            viewModel.loadThread(conversation.threadId)
                            navController.navigate("thread/${conversation.threadId}/${Uri.encode(conversation.address)}/${Uri.encode(conversation.displayName)}")
                        }
                    },
                    onComposeClick = {
                        viewModel.prepareNewMessage()
                        navController.navigate("new")
                    },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onDeleteConversations = { threadIds -> viewModel.deleteConversations(threadIds) },
                    onBlockConversations = { selectedConversations -> viewModel.blockConversations(selectedConversations) },
                    onMakeConversationsPrivate = { selectedConversations -> viewModel.makeConversationsPrivate(selectedConversations) },
                    onPinConversations = { selectedConversations -> viewModel.pinConversations(selectedConversations) },
                    swipeRightToLeftAction = appSettings.swipeRightToLeftAction,
                    swipeLeftToRightAction = appSettings.swipeLeftToRightAction,
                    swipeDeleteRequiresConfirmation = appSettings.swipeDeleteRequiresConfirmation,
                    onMarkThreadRead = { threadId -> viewModel.markThreadReadFromSwipe(threadId) },
                    onMarkThreadUnread = { threadId -> viewModel.markThreadUnreadFromSwipe(threadId) }
                )
            }
            composable("new") {
                NewMessageScreen(
                    contacts = contacts,
                    sims = sims,
                    pickedContact = pickedContact,
                    onPickedContactConsumed = { viewModel.consumePickedContact() },
                    pickedContactsBatch = pickedContactsBatch,
                    onPickedContactsBatchConsumed = { viewModel.consumePickedContactsBatch() },
                    onOpenContactPicker = {
                        viewModel.loadAllContactsForPicker()
                        navController.navigate("contact_picker")
                    },
                    onSearchChange = { query -> viewModel.searchContacts(query) },
                    onSend = { address, displayName, body, subId ->
                        viewModel.sendNewMessage(address, displayName, body, subId)
                    },
                    onScheduleSend = { address, displayName, body, subId, scheduledAt ->
                        viewModel.scheduleMessage(address, displayName, body, subId, scheduledAt)
                    },
                    onSendToMultiple = { recipients, body, subId ->
                        viewModel.sendNewMessageToMultiple(recipients, body, subId)
                        navController.popBackStack()
                    },
                    onScheduleToMultiple = { recipients, body, subId, scheduledAt ->
                        viewModel.scheduleMessageToMultiple(recipients, body, subId, scheduledAt)
                        navController.popBackStack()
                    },
                    onLeaveWithDraft = { address, displayName, body ->
                        viewModel.saveDraftForNewConversation(address, displayName, body)
                    },
                    groupMessagingEnabled = appSettings.groupMessagingEnabled,
                    groupSummaries = groupSummaries,
                    onLoadGroupMembers = { groupId -> viewModel.getGroupMembers(groupId) },
                    onSaveGroup = { name, members -> viewModel.saveMessageGroup(name, members) },
                    onDeleteGroup = { groupId -> viewModel.deleteMessageGroup(groupId) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("contact_picker") {
                ContactPickerScreen(
                    contacts = allContactsForPicker,
                    onConfirm = { selected ->
                        viewModel.setPickedContactsBatch(selected)
                        navController.popBackStack()
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "thread/{threadId}/{address}/{displayName}",
                arguments = listOf(
                    navArgument("threadId") { type = NavType.LongType },
                    navArgument("address") { type = NavType.StringType },
                    navArgument("displayName") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getLong("threadId") ?: 0L
                val address = backStackEntry.arguments?.getString("address") ?: ""
                val displayName = backStackEntry.arguments?.getString("displayName") ?: address

                // چون ContactsCache تو کل عمرِ اپ کش‌شده، این چک عملاً یه HashMap.get سبکه -
                // نیازی به suspend/withContext نداره (هم‌خانواده‌ی همون استفاده‌ای که
                // ConversationListScreen و بقیه‌ی صفحات برای اسمِ مخاطب ازش می‌کنن)
                val isKnownContact = remember(address) { ContactsCache.getName(context, address) != null }

                LaunchedEffect(threadId, address) {
                    NotificationManagerCompat.from(context).cancel(address.hashCode())
                }

                ThreadScreen(
                    displayName = displayName,
                    address = address,
                    messages = messages,
                    scheduledMessages = scheduledMessages,
                    sims = sims,
                    favoriteIds = favoriteIds,
                    pinnedMessageIds = pinnedMessageIds,
                    initialDraft = draftText,
                    isKnownContact = isKnownContact,
                    onOpenContactInfo = { onOpenContactInfo(address) },
                    onSend = { body, subId -> viewModel.sendMessage(address, body, threadId, subId) },
                    onScheduleSend = { body, subId, at -> viewModel.scheduleMessage(address, displayName, body, subId, at) },
                    onDeleteMessage = { messageId -> viewModel.deleteMessage(threadId, messageId) },
                    onDeleteMessages = { messageIds -> viewModel.deleteMessages(threadId, messageIds) },
                    onResend = { message -> viewModel.resendMessage(message) },
                    onUpdateScheduledTime = { id, newTime -> viewModel.updateScheduledTime(id, threadId, newTime) },
                    onSendScheduledNow = { id -> viewModel.sendScheduledNow(id, threadId) },
                    onCancelScheduledMessage = { id -> viewModel.cancelScheduledMessage(id, threadId) },
                    onLeaveWithDraft = { text -> viewModel.saveDraft(threadId, address, text) },
                    onOpenNote = { text ->
                        viewModel.openNote(text)
                        navController.navigate("note")
                    },
                    onToggleFavorite = { message -> viewModel.toggleFavorite(message, displayName) },
                    onTogglePinMessage = { message -> viewModel.togglePinMessage(message) },
                    onBack = {
                        viewModel.clearOpenThread()
                        navController.popBackStack()
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    onOpenNotificationActions = { navController.navigate("notification_actions") },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("notification_actions") {
                val settingsState by AppSettings.state.collectAsState()
                NotificationActionsSettingsScreen(
                    actions = settingsState.notificationActions,
                    onSave = { updated -> AppSettings.setNotificationActionSettings(context, updated) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("note") {
                NoteScreen(
                    text = noteText ?: "",
                    onBack = {
                        viewModel.consumeNote()
                        navController.popBackStack()
                    }
                )
            }
            composable("favorites") {
                FavoritesScreen(
                    favorites = favorites,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onItemClick = { favorite ->
                        viewModel.loadThread(favorite.threadId)
                        navController.navigate("thread/${favorite.threadId}/${Uri.encode(favorite.address)}/${Uri.encode(favorite.displayName)}")
                    },
                    onRemoveFavorite = { messageId -> viewModel.removeFavorite(messageId) }
                )
            }
            composable("trash") {
                LaunchedEffect(Unit) { viewModel.loadTrash() }
                TrashScreen(
                    trashedMessages = trash,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onRestore = { messageId -> viewModel.restoreFromTrash(messageId) },
                    onPermanentDelete = { messageId -> viewModel.permanentlyDeleteFromTrash(messageId) }
                )
            }
            composable("scheduled") {
                ScheduledMessagesScreen(
                    scheduledMessages = allScheduledMessages,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onUpdateTime = { id, newTime -> viewModel.updateScheduledTimeGlobal(id, newTime) },
                    onSendNow = { id -> viewModel.sendScheduledNowGlobal(id) },
                    onCancel = { id -> viewModel.cancelScheduledMessageGlobal(id) }
                )
            }
            composable("blocked") {
                LaunchedEffect(Unit) {
                    viewModel.loadBlockedMessages()
                }
                BlockScreen(
                    blockedMessageCount = blockedMessages.size,
                    blockedNumberCount = blockedNumbers.size,
                    blockKeywordCount = blockKeywords.size,
                    blockPatternCount = blockPatterns.size,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onOpenBlockedMessages = { navController.navigate("blocked_messages") },
                    onOpenBlockedNumbers = { navController.navigate("blocked_numbers") },
                    onOpenBlockKeywords = { navController.navigate("block_keywords") },
                    onOpenBlockPatterns = { navController.navigate("block_patterns") },
                    onOpenAddSender = { navController.navigate("block_add_sender") },
                    onOpenBlockSettings = { navController.navigate("block_settings") }
                )
            }
            composable("blocked_messages") {
                LaunchedEffect(Unit) { viewModel.loadBlockedMessages() }
                BlockedMessagesScreen(
                    blockedMessages = blockedMessages,
                    favoriteIds = favoriteIds,
                    onBack = { navController.popBackStack() },
                    onDeleteMessages = { messageIds -> viewModel.deleteBlockedMessages(messageIds) },
                    onOpenNote = { text ->
                        viewModel.openNote(text)
                        navController.navigate("note")
                    },
                    onToggleFavorite = { entry -> viewModel.toggleFavorite(entry.message, entry.contactDisplayName) },
                    onResend = { entry -> viewModel.resendMessage(entry.message) }
                )
            }
            composable("blocked_numbers") {
                BlockedNumbersScreen(
                    blockedNumbers = blockedNumbers,
                    onBack = { navController.popBackStack() },
                    onUnblock = { threadId -> viewModel.unblockNumber(threadId) },
                    onAddNumberClick = {
                        viewModel.prepareNewMessage()
                        navController.navigate("block_add_number")
                    }
                )
            }
            composable("block_add_number") {
                AddBlockedNumberScreen(
                    contacts = contacts,
                    pickedContact = pickedContact,
                    onPickedContactConsumed = { viewModel.consumePickedContact() },
                    onPickFromContactsClick = onPickContactClick,
                    onSearchChange = { query -> viewModel.searchContacts(query) },
                    onBlockNumber = { address, displayName -> viewModel.blockNumber(address, displayName) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("block_add_sender") {
                AddBlockedSenderScreen(
                    onBlockSender = { sender -> viewModel.blockNumber(sender, sender) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("block_keywords") {
                BlockKeywordsScreen(
                    keywords = blockKeywords,
                    onBack = { navController.popBackStack() },
                    onAddKeyword = { text -> viewModel.addBlockKeyword(text) },
                    onRemoveKeyword = { id -> viewModel.removeBlockKeyword(id) }
                )
            }
            composable("block_patterns") {
                BlockPatternsScreen(
                    patterns = blockPatterns,
                    onBack = { navController.popBackStack() },
                    onAddPattern = { type, value -> viewModel.addBlockPattern(type, value) },
                    onRemovePattern = { id -> viewModel.removeBlockPattern(id) }
                )
            }
            composable("block_settings") {
                BlockSettingsScreen(
                    showBlockedNotificationsEnabled = appSettings.showBlockedNotificationsEnabled,
                    showBlockedInMessageListEnabled = appSettings.showBlockedInMessageListEnabled,
                    blockNonContactsEnabled = appSettings.blockNonContactsEnabled,
                    onBack = { navController.popBackStack() },
                    onShowBlockedNotificationsChange = { enabled ->
                        AppSettings.setShowBlockedNotificationsEnabled(context, enabled)
                    },
                    onShowBlockedInMessageListChange = { enabled ->
                        AppSettings.setShowBlockedInMessageListEnabled(context, enabled)
                    },
                    onBlockNonContactsChange = { enabled ->
                        AppSettings.setBlockNonContactsEnabled(context, enabled)
                    }
                )
            }
            composable("private") {
                if (privateUnlocked) {
                    LaunchedEffect(Unit) {
                        viewModel.loadPrivateMessages()
                    }
                    PrivateScreen(
                        privateMessageCount = privateMessages.size,
                        privateNumberCount = privateNumbers.size,
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBack = {
                            viewModel.lockPrivate()
                            navController.popBackStack()
                        },
                        onOpenPrivateMessages = { navController.navigate("private_messages") },
                        onOpenPrivateNumbers = { navController.navigate("private_numbers") },
                        onOpenPinSettings = { navController.navigate("private_pin_settings") }
                    )
                } else {
                    PrivatePinScreen(
                        checkHasExistingPin = { viewModel.hasPrivatePin() },
                        onVerifyPin = { pin -> viewModel.verifyPrivatePin(pin) },
                        onSetPin = { pin -> viewModel.setPrivatePin(pin) },
                        onUnlocked = { viewModel.unlockPrivate() },
                        onMenuClick = { scope.launch { drawerState.open() } },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("private_pin_settings") {
                LaunchedEffect(privateUnlocked) {
                    if (!privateUnlocked) navController.popBackStack()
                }
                if (privateUnlocked) {
                    PrivatePinSettingsScreen(
                        onVerifyPin = { pin -> viewModel.verifyPrivatePin(pin) },
                        onChangePin = { newPin -> viewModel.setPrivatePin(newPin) },
                        onRemovePin = { viewModel.removePrivatePin() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
            composable("private_messages") {
                LaunchedEffect(privateUnlocked) {
                    if (!privateUnlocked) {
                        navController.popBackStack()
                    } else {
                        viewModel.loadPrivateMessages()
                    }
                }
                if (privateUnlocked) {
                    PrivateMessagesScreen(
                        privateMessages = privateMessages,
                        favoriteIds = favoriteIds,
                        onBack = { navController.popBackStack() },
                        onDeleteMessages = { messageIds -> viewModel.deletePrivateMessages(messageIds) },
                        onOpenNote = { text ->
                            viewModel.openNote(text)
                            navController.navigate("note")
                        },
                        onToggleFavorite = { entry -> viewModel.toggleFavorite(entry.message, entry.contactDisplayName) },
                        onResend = { entry -> viewModel.resendMessage(entry.message) }
                    )
                }
            }
            composable("private_numbers") {
                if (privateUnlocked) {
                    PrivateNumbersScreen(
                        privateNumbers = privateNumbers,
                        onBack = { navController.popBackStack() },
                        onRemovePrivate = { threadId -> viewModel.removePrivate(threadId) },
                        onAddNumberClick = {
                            viewModel.prepareNewMessage()
                            navController.navigate("private_add_number")
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable("private_add_number") {
                LaunchedEffect(privateUnlocked) {
                    if (!privateUnlocked) navController.popBackStack()
                }
                if (privateUnlocked) {
                    AddPrivateNumberScreen(
                        contacts = contacts,
                        pickedContact = pickedContact,
                        onPickedContactConsumed = { viewModel.consumePickedContact() },
                        onPickFromContactsClick = onPickContactClick,
                        onSearchChange = { query -> viewModel.searchContacts(query) },
                        onMakePrivate = { address, displayName -> viewModel.makePrivateNumber(address, displayName) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}