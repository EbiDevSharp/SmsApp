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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.petro.smsapp.data.ConversationFilterContext
import com.petro.smsapp.data.ConversationFilterType
import com.petro.smsapp.data.ConversationSortType
import com.petro.smsapp.data.TimeFilterSelection
import com.petro.smsapp.data.applyConversationFilters
import com.petro.smsapp.data.applySort
import com.petro.smsapp.data.applyTimeFilter
import com.petro.smsapp.ui.AppDrawerContent
import com.petro.smsapp.ui.AddFilterGroupNumberScreen
import com.petro.smsapp.ui.AddFilterGroupSenderScreen
import com.petro.smsapp.ui.AddPrivateNumberScreen
import com.petro.smsapp.ui.FilterGroupsScreen
import com.petro.smsapp.ui.FilterGroupDetailScreen
import com.petro.smsapp.ui.FilterGroupKeywordsScreen
import com.petro.smsapp.ui.FilterGroupMessagesScreen
import com.petro.smsapp.ui.FilterGroupNumbersScreen
import com.petro.smsapp.ui.FilterGroupPatternsScreen
import com.petro.smsapp.ui.GroupPickerSheet
import com.petro.smsapp.ui.ContactPickerScreen
import com.petro.smsapp.ui.ConversationListScreen
import com.petro.smsapp.ui.FavoritesScreen
import com.petro.smsapp.ui.NewMessageScreen
import com.petro.smsapp.ui.NoteScreen
import com.petro.smsapp.ui.PrivateMessagesScreen
import com.petro.smsapp.ui.PrivateNumbersScreen
import com.petro.smsapp.ui.PrivatePinScreen
import com.petro.smsapp.ui.PrivatePinSettingsScreen
import com.petro.smsapp.ui.PrivateScreen
import com.petro.smsapp.ui.SearchScreen
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
        (application as SmsApplication).ensureContactsObserverRegistered()
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SmsAppTheme {
                // خطِ اطمینان: preload اصلی حالا توی SmsApplication.ensureContactsObserverRegistered
                // (خیلی زودتر، قبل از این فریم) صدا زده میشه؛ این خط فقط برای حالتِ نادری
                // که مجوزِ مخاطبین بینِ اون لحظه و این‌جا گرفته شده باشه یه بک‌آپه.
                // preload() خودش idempotent هست پس تکرارش هزینه‌ای نداره.
                LaunchedEffect(Unit) {
                    ContactsCache.preload(this@MainActivity)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background)
                    {
                        AppNavigation(
                            viewModel = viewModel,
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
        handleQuickGroupPickIntent(intent)
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
        handleQuickGroupPickIntent(intent)
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

    /** دکمه‌ی «افزودن به گروه» روی نوتیف اپ رو با این اینتنت باز می‌کنه - اینجا فقط ViewModel رو خبر می‌کنیم تا شیتِ انتخابِ گروه نشون داده بشه */
    private fun handleQuickGroupPickIntent(intent: Intent?) {
        intent ?: return
        val address = intent.getStringExtra(EXTRA_QUICK_GROUP_ADDRESS) ?: return
        val displayName = intent.getStringExtra(EXTRA_QUICK_GROUP_DISPLAY_NAME) ?: address
        viewModel.requestQuickGroupPick(address, displayName)
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
        const val EXTRA_QUICK_GROUP_ADDRESS = "extra_quick_group_address"
        const val EXTRA_QUICK_GROUP_DISPLAY_NAME = "extra_quick_group_display_name"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppNavigation(
    viewModel: SmsViewModel,
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
    val pinnedMessageThreadIds by viewModel.pinnedMessageThreadIds.collectAsState()
    val favoriteThreadIds by viewModel.favoriteThreadIds.collectAsState()
    val sim1ThreadIds by viewModel.sim1ThreadIds.collectAsState()
    val sim2ThreadIds by viewModel.sim2ThreadIds.collectAsState()
    val outgoingThreadIds by viewModel.outgoingThreadIds.collectAsState()
    val incomingThreadIds by viewModel.incomingThreadIds.collectAsState()
    val trash by viewModel.trash.collectAsState()
    val filterGroupSummaries by viewModel.filterGroupSummaries.collectAsState()
    val notificationPickerGroups by viewModel.notificationPickerGroups.collectAsState()
    val filterGroupMessages by viewModel.filterGroupMessages.collectAsState()
    val appSettings by AppSettings.state.collectAsState()
    val privateNumbers by viewModel.privateNumbers.collectAsState()
    val privateMessages by viewModel.privateMessages.collectAsState()
    val privateUnlocked by viewModel.privateUnlocked.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val allScheduledMessages by viewModel.allScheduledMessages.collectAsState()
    val groupSummaries by viewModel.groupSummaries.collectAsState()
    val pendingGroupPickTargets by viewModel.pendingGroupPickTargets.collectAsState()
    val quickGroupPickTarget by viewModel.quickGroupPickTarget.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedFilterIds by remember { mutableStateOf(setOf<String>()) }
    var timeSelection by remember { mutableStateOf<TimeFilterSelection>(TimeFilterSelection.None) }
    var sortType by remember { mutableStateOf<ConversationSortType?>(null) }
    val filteredConversations = remember(
        conversations,
        selectedFilterIds,
        pinnedMessageThreadIds,
        favoriteThreadIds,
        sim1ThreadIds,
        sim2ThreadIds,
        outgoingThreadIds,
        incomingThreadIds,
        timeSelection,
        sortType
    ) {
        conversations.applyConversationFilters(
            selectedFilterIds.mapNotNull { ConversationFilterType.fromId(it) }.toSet(),
            ConversationFilterContext(
                pinnedMessageThreadIds = pinnedMessageThreadIds,
                favoriteThreadIds = favoriteThreadIds,
                sim1ThreadIds = sim1ThreadIds,
                sim2ThreadIds = sim2ThreadIds,
                outgoingThreadIds = outgoingThreadIds,
                incomingThreadIds = incomingThreadIds
            )
        )
            .applyTimeFilter(timeSelection)
            .applySort(sortType)
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    BackHandler(enabled = !drawerState.isOpen && currentRoute == "private") {
        viewModel.lockPrivate()
        navController.popBackStack()
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

    // شیتِ انتخابِ گروه - از سویپ/منویِ لیستِ مکالمات (چند مکالمه‌ی هدف هم‌زمان) - همه‌ی گروه‌ها نشون داده میشن
    if (pendingGroupPickTargets != null) {
        val targets = pendingGroupPickTargets!!
        val label = if (targets.size == 1) targets.first().displayName else "${targets.size} مخاطب"
        GroupPickerSheet(
            targetLabel = label,
            groups = filterGroupSummaries,
            onPick = { groupId -> viewModel.addConversationsToGroup(groupId, targets) },
            onCreateAndPick = { name -> viewModel.createFilterGroupAndAddConversations(name, targets) },
            onDismiss = { viewModel.consumeGroupPickTargets() }
        )
    }

    // شیتِ انتخابِ گروه - از دکمه‌ی روی نوتیف (یه شماره‌ی هدف) - فقط گروه‌هایی که
    // showInNotificationPicker روشنه نشون داده میشن
    if (quickGroupPickTarget != null) {
        val target = quickGroupPickTarget!!
        GroupPickerSheet(
            targetLabel = target.displayName,
            groups = notificationPickerGroups,
            onPick = { groupId -> viewModel.addAddressToGroupQuick(groupId, target.address, target.displayName) },
            onCreateAndPick = { name -> viewModel.createFilterGroupAndAddAddress(name, target.address, target.displayName) },
            onDismiss = { viewModel.consumeQuickGroupPick() }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                val settings by AppSettings.state.collectAsState()
                val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
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
                    themeMode = settings.themeMode,
                    onCycleTheme = { center ->
                        // چرخه: روشن → تاریک → سیستم → روشن
                        val nextMode = when (settings.themeMode) {
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        }
                        // رنگ پس‌زمینهٔ فعلی (تم قدیم) برای overlay بیرونِ دایره
                        val currentDark = when (settings.themeMode) {
                            ThemeMode.LIGHT -> false
                            ThemeMode.DARK -> true
                            ThemeMode.SYSTEM -> isSystemDark
                        }
                        // تم همان لحظه عوض می‌شود؛ داخل دایره UI واقعی دیده می‌شود
                        // (بدون recreate — سبک و بدون هنگ)
                        com.petro.smsapp.ui.ThemeRevealController.request(
                            center = center,
                            oldBackground = com.petro.smsapp.ui.themeBackgroundColor(currentDark),
                            applyMode = nextMode
                        )
                    },
                    selectedFilterIds = selectedFilterIds,
                    onToggleFilter = { filterType ->
                        selectedFilterIds = if (selectedFilterIds.contains(filterType.id)) {
                            selectedFilterIds - filterType.id
                        } else {
                            selectedFilterIds + filterType.id
                        }
                    },
                    timeSelection = timeSelection,
                    onTimeSelectionChange = { timeSelection = it },
                    sortType = sortType,
                    onSortTypeChange = { sortType = it },
                    sims = sims
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "list") {
            composable("list") {
                ConversationListScreen(
                    conversations = filteredConversations,
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
                    onSearchClick = { navController.navigate("search") },
                    hasActiveFilter = selectedFilterIds.isNotEmpty() || timeSelection != TimeFilterSelection.None,
                    onClearFilters = {
                        selectedFilterIds = emptySet()
                        timeSelection = TimeFilterSelection.None
                    },
                    filterSelectedIds = selectedFilterIds,
                    onToggleFilter = { filterType ->
                        selectedFilterIds = if (selectedFilterIds.contains(filterType.id)) {
                            selectedFilterIds - filterType.id
                        } else {
                            selectedFilterIds + filterType.id
                        }
                    },
                    filterTimeSelection = timeSelection,
                    onFilterTimeSelectionChange = { timeSelection = it },
                    filterSortType = sortType,
                    onFilterSortTypeChange = { sortType = it },
                    sims = sims,
                    onDeleteConversations = { threadIds -> viewModel.deleteConversations(threadIds) },
                    onAddToGroupClick = { selectedConversations -> viewModel.requestAddConversationsToGroup(selectedConversations) },
                    onMakeConversationsPrivate = { selectedConversations -> viewModel.makeConversationsPrivate(selectedConversations) },
                    onPinConversations = { selectedConversations -> viewModel.pinConversations(selectedConversations) },
                    swipeRightToLeftAction = appSettings.swipeRightToLeftAction,
                    swipeLeftToRightAction = appSettings.swipeLeftToRightAction,
                    swipeDeleteRequiresConfirmation = appSettings.swipeDeleteRequiresConfirmation,
                    showContactNumberEnabled = appSettings.showContactNumberInListEnabled,
                    alphabetIndexBarEnabled = appSettings.alphabetIndexBarEnabled,
                    alphabetBubbleSizeDp = appSettings.alphabetBubbleSizeDp,
                    alphabetOffsetXDp = appSettings.alphabetOffsetXDp,
                    alphabetOffsetYDp = appSettings.alphabetOffsetYDp,
                    onMarkThreadRead = { threadId -> viewModel.markThreadReadFromSwipe(threadId) },
                    onMarkThreadUnread = { threadId -> viewModel.markThreadUnreadFromSwipe(threadId) }
                )
            }
            composable("search") {
                SearchScreen(
                    conversations = conversations,
                    pinnedMessageThreadIds = pinnedMessageThreadIds,
                    favoriteThreadIds = favoriteThreadIds,
                    sim1ThreadIds = sim1ThreadIds,
                    sim2ThreadIds = sim2ThreadIds,
                    outgoingThreadIds = outgoingThreadIds,
                    incomingThreadIds = incomingThreadIds,
                    sims = sims,
                    showContactNumberEnabled = appSettings.showContactNumberInListEnabled,
                    searchMessageThreads = { q, outgoingOnly, incomingOnly ->
                        viewModel.searchThreadsByMessageBody(q, outgoingOnly, incomingOnly)
                    },
                    onBack = { navController.popBackStack() },
                    onConversationClick = { conversation ->
                        if (conversation.address.isNotBlank()) {
                            viewModel.loadThread(conversation.threadId)
                            navController.navigate("thread/${conversation.threadId}/${Uri.encode(conversation.address)}/${Uri.encode(conversation.displayName)}")
                        }
                    }
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
                    onRenameGroup = { groupId, newName -> viewModel.renameMessageGroup(groupId, newName) },
                    onUpdateGroupMembers = { groupId, members -> viewModel.updateMessageGroupMembers(groupId, members) },
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
                    onOpenPopupSettings = { navController.navigate("popup_settings") },
                    onOpenAlphabetIndexSettings = { navController.navigate("alphabet_index_settings") },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("alphabet_index_settings") {
                com.petro.smsapp.ui.AlphabetIndexSettingsScreen(
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
            composable("popup_settings") {
                com.petro.smsapp.ui.PopupSettingsScreen(
                    onOpenPopupActions = { navController.navigate("popup_actions") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable("popup_actions") {
                val settingsState by AppSettings.state.collectAsState()
                com.petro.smsapp.ui.PopupActionsSettingsScreen(
                    actions = settingsState.popupActions,
                    displayMode = settingsState.popupActionDisplayMode,
                    onSaveActions = { updated -> AppSettings.setPopupActionSettings(context, updated) },
                    onSaveDisplayMode = { mode -> AppSettings.setPopupActionDisplayMode(context, mode) },
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
                    onOpenNote = { text ->
                        viewModel.openNote(text)
                        navController.navigate("note")
                    },
                    onRestore = { messageId -> viewModel.restoreFromTrash(messageId) },
                    onRestoreMultiple = { messageIds -> viewModel.restoreMultipleFromTrash(messageIds) },
                    onPermanentDelete = { messageId -> viewModel.permanentlyDeleteFromTrash(messageId) },
                    onPermanentDeleteMultiple = { messageIds -> viewModel.permanentlyDeleteMultipleFromTrash(messageIds) }
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

            // ==================================================================
            // گروهِ فیلتر - جایگزینِ عمومیِ روت‌های قدیمیِ «blocked/...»
            // ==================================================================

            composable("filter_groups") {
                FilterGroupsScreen(
                    groups = filterGroupSummaries,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onBack = { navController.popBackStack() },
                    onOpenGroup = { groupId -> navController.navigate("filter_group/$groupId") },
                    onCreateGroup = { name, hide, notify, nonContacts, notifPicker ->
                        viewModel.createFilterGroup(name, hide, notify, nonContacts, notifPicker)
                    },
                    onDeleteGroup = { groupId -> viewModel.deleteFilterGroup(groupId) },
                    onReorder = { orderedIds -> viewModel.reorderFilterGroups(orderedIds) },
                    onGlobalShowNotificationsChange = { enabled -> viewModel.setAllFilterGroupsShowNotifications(enabled) },
                    onGlobalShowInMainListChange = { show -> viewModel.setAllFilterGroupsShowInMainList(show) }
                )
            }
            composable(
                route = "filter_group/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                if (summary != null) {
                    FilterGroupDetailScreen(
                        summary = summary,
                        onBack = { navController.popBackStack() },
                        onOpenNumbers = { navController.navigate("filter_group_numbers/$groupId") },
                        onOpenKeywords = { navController.navigate("filter_group_keywords/$groupId") },
                        onOpenPatterns = { navController.navigate("filter_group_patterns/$groupId") },
                        onOpenMessages = {
                            viewModel.loadFilterGroupMessages(groupId)
                            navController.navigate("filter_group_messages/$groupId")
                        },
                        onSave = { name, hide, notify, nonContacts, notifPicker ->
                            viewModel.updateFilterGroup(groupId, name, hide, notify, nonContacts, notifPicker)
                        },
                        onSetQuickAddTarget = { checked ->
                            viewModel.setQuickAddTargetGroup(if (checked) groupId else null)
                        }
                    )
                } else {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                }
            }
            composable(
                route = "filter_group_numbers/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                val numbers by viewModel.observeFilterGroupNumbers(groupId).collectAsState(initial = emptyList())
                FilterGroupNumbersScreen(
                    groupName = summary?.group?.name ?: "",
                    numbers = numbers,
                    onBack = { navController.popBackStack() },
                    onRemove = { address -> viewModel.removeNumberFromFilterGroup(groupId, address) },
                    onAddNumberClick = { navController.navigate("add_filter_group_number/$groupId") }
                )
            }
            composable(
                route = "add_filter_group_number/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                AddFilterGroupNumberScreen(
                    groupName = summary?.group?.name ?: "",
                    contacts = contacts,
                    pickedContactsBatch = pickedContactsBatch,
                    onPickedContactsBatchConsumed = { viewModel.consumePickedContactsBatch() },
                    onOpenContactPicker = {
                        viewModel.loadAllContactsForPicker()
                        navController.navigate("contact_picker")
                    },
                    onSearchChange = { query -> viewModel.searchContacts(query) },
                    onAddNumber = { address, displayName -> viewModel.addNumberToFilterGroup(groupId, address, displayName) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "add_filter_group_sender/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                AddFilterGroupSenderScreen(
                    groupName = summary?.group?.name ?: "",
                    onAddSender = { sender -> viewModel.addNumberToFilterGroup(groupId, sender, sender) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "filter_group_keywords/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                val keywords by viewModel.observeFilterGroupKeywords(groupId).collectAsState(initial = emptyList())
                FilterGroupKeywordsScreen(
                    groupName = summary?.group?.name ?: "",
                    keywords = keywords,
                    onBack = { navController.popBackStack() },
                    onAddKeyword = { text -> viewModel.addFilterGroupKeyword(groupId, text) },
                    onRemoveKeyword = { id -> viewModel.removeFilterGroupKeyword(id) }
                )
            }
            composable(
                route = "filter_group_patterns/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                val patterns by viewModel.observeFilterGroupPatterns(groupId).collectAsState(initial = emptyList())
                FilterGroupPatternsScreen(
                    groupName = summary?.group?.name ?: "",
                    patterns = patterns,
                    onBack = { navController.popBackStack() },
                    onAddPattern = { type, value -> viewModel.addFilterGroupPattern(groupId, type, value) },
                    onRemovePattern = { id -> viewModel.removeFilterGroupPattern(id) }
                )
            }
            composable(
                route = "filter_group_messages/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.LongType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getLong("groupId") ?: 0L
                val summary = filterGroupSummaries.find { it.group.id == groupId }
                LaunchedEffect(groupId) { viewModel.loadFilterGroupMessages(groupId) }
                FilterGroupMessagesScreen(
                    groupName = summary?.group?.name ?: "",
                    messages = filterGroupMessages,
                    favoriteIds = favoriteIds,
                    onBack = { navController.popBackStack() },
                    onDeleteMessages = { messageIds -> viewModel.deleteFilterGroupMessages(messageIds, groupId) },
                    onOpenNote = { text ->
                        viewModel.openNote(text)
                        navController.navigate("note")
                    },
                    onToggleFavorite = { entry -> viewModel.toggleFavorite(entry.message, entry.contactDisplayName) },
                    onResend = { entry -> viewModel.resendMessage(entry.message) }
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
                        pickedContactsBatch = pickedContactsBatch,
                        onPickedContactsBatchConsumed = { viewModel.consumePickedContactsBatch() },
                        onOpenContactPicker = {
                            viewModel.loadAllContactsForPicker()
                            navController.navigate("contact_picker")
                        },
                        onSearchChange = { query -> viewModel.searchContacts(query) },
                        onMakePrivate = { address, displayName -> viewModel.makePrivateNumber(address, displayName) },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}