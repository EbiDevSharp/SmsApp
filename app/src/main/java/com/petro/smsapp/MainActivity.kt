package com.petro.smsapp

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.app.NotificationManagerCompat
import com.petro.smsapp.data.ContactInfo
import com.petro.smsapp.ui.AppDrawerContent
import com.petro.smsapp.ui.ConversationListScreen
import com.petro.smsapp.ui.FavoritesScreen
import com.petro.smsapp.ui.NewMessageScreen
import com.petro.smsapp.ui.NoteScreen
import com.petro.smsapp.ui.PlaceholderScreen
import com.petro.smsapp.ui.SettingsScreen
import com.petro.smsapp.ui.SmsAppTheme
import com.petro.smsapp.ui.ThreadScreen
import com.petro.smsapp.ui.TrashScreen
import com.petro.smsapp.viewmodel.SmsViewModel
import kotlinx.coroutines.launch

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
                // اپ فعلاً فقط فارسیه، پس صرف‌نظر از زبان گوشی چیدمان رو راست‌به‌چپ می‌کنیم.
                // اعداد (ساعت/تاریخ/شماره تلفن) به‌خاطر الگوریتم بایدای یونیکد خودشون چپ‌به‌راست می‌مونن.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier) {
                        AppNavigation(
                            viewModel = viewModel,
                            onPickContactClick = {
                                pickContactLauncher.launch(
                                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                                )
                            }
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

        // اگه اپ از طریق کلیک روی نوتیف پیامک باز شده، مستقیم برو صفحه چت همون مخاطب
        handleNotificationIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        // اپ رفت بک‌گراند - حتی اگه هنوز روی صفحه‌ی چت باشیم، دیگه کاربر واقعاً نمی‌بینتش،
        // پس نوتیف پیام‌های بعدی باید دوباره نشون داده بشه
        viewModel.onAppBackgrounded()
    }

    override fun onResume() {
        super.onResume()
        // برگشتیم فورگراند - اگه هنوز همون thread باز بود، دوباره ساکتش کن
        viewModel.onAppForegrounded()
    }

    /**
     * وقتی اپ از قبل باز باشه (launchMode="singleTop") و کاربر روی یه نوتیف دیگه بزنه،
     * onCreate دوباره صدا زده نمیشه - این تابع همون کار رو برای اون حالت انجام میده.
     */
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
    }

    /** خواندن نام و شماره‌ی مخاطبی که از اپ مخاطبین سیستم انتخاب شده */
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

    companion object {
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"
    }
}

@Composable
fun AppNavigation(viewModel: SmsViewModel, onPickContactClick: () -> Unit) {
    val navController = rememberNavController()
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val newTarget by viewModel.newConversationTarget.collectAsState()
    val pickedContact by viewModel.pickedContact.collectAsState()
    val sims by viewModel.sims.collectAsState()
    val noteText by viewModel.noteText.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val favoriteIds by viewModel.favoriteIds.collectAsState()
    val trash by viewModel.trash.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // لود اولیه‌ی لیست فیوریت‌ها - همون اول که برنامه بالا میاد
    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    // پیام‌های یک‌بارمصرف (مثل «این پیام قفله») به‌صورت Toast نشون داده میشن
    LaunchedEffect(operationMessage) {
        operationMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeOperationMessage()
        }
    }

    // وقتی از صفحه پیام جدید، پیام ارسال و thread ساخته/پیدا شد، برو صفحه چت همون thread با آدرس درست
    LaunchedEffect(newTarget) {
        val target = newTarget
        if (target != null) {
            viewModel.loadThread(target.threadId)
            navController.navigate("list") {
                popUpTo("list") { inclusive = true }
            }
            navController.navigate("thread/${target.threadId}/${target.address}/${target.displayName}")
            viewModel.consumeNewConversationTarget()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                AppDrawerContent(onItemClick = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route)
                })
            }
        }
    ) {
        NavHost(navController = navController, startDestination = "list") {
            composable("list") {
                ConversationListScreen(
                    conversations = conversations,
                    onConversationClick = { conversation ->
                        viewModel.loadThread(conversation.threadId)
                        navController.navigate("thread/${conversation.threadId}/${conversation.address}/${conversation.displayName}")
                    },
                    onComposeClick = {
                        viewModel.prepareNewMessage()
                        navController.navigate("new")
                    },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onDeleteConversations = { threadIds -> viewModel.deleteConversations(threadIds) }
                )
            }
            composable("new") {
                NewMessageScreen(
                    contacts = contacts,
                    sims = sims,
                    pickedContact = pickedContact,
                    onPickedContactConsumed = { viewModel.consumePickedContact() },
                    onPickFromContactsClick = onPickContactClick,
                    onSearchChange = { query -> viewModel.searchContacts(query) },
                    onSend = { address, displayName, body, subId ->
                        viewModel.sendNewMessage(address, displayName, body, subId)
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

                // اگه نوتیف این مخاطب هنوز بالاست (کاربر کنارش نزده)، با ورود به همین چت پاکش کن -
                // notificationId توی SmsDeliverReceiver همون address.hashCode() هست
                LaunchedEffect(threadId, address) {
                    NotificationManagerCompat.from(context).cancel(address.hashCode())
                }

                ThreadScreen(
                    displayName = displayName,
                    messages = messages,
                    sims = sims,
                    favoriteIds = favoriteIds,
                    onSend = { body, subId -> viewModel.sendMessage(address, body, threadId, subId) },
                    onDeleteMessage = { messageId -> viewModel.deleteMessage(threadId, messageId) },
                    onDeleteMessages = { messageIds -> viewModel.deleteMessages(threadId, messageIds) },
                    onOpenNote = { text ->
                        viewModel.openNote(text)
                        navController.navigate("note")
                    },
                    onToggleFavorite = { message -> viewModel.toggleFavorite(message, displayName) },
                    onBack = {
                        viewModel.clearOpenThread()
                        navController.popBackStack()
                    }
                )
            }
            composable("settings") {
                SettingsScreen(onBack = { navController.popBackStack() })
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
                    onBack = { navController.popBackStack() },
                    onItemClick = { favorite ->
                        viewModel.loadThread(favorite.threadId)
                        navController.navigate("thread/${favorite.threadId}/${favorite.address}/${favorite.displayName}")
                    },
                    onRemoveFavorite = { messageId -> viewModel.removeFavorite(messageId) }
                )
            }
            composable("trash") {
                LaunchedEffect(Unit) { viewModel.loadTrash() }
                TrashScreen(
                    trashedMessages = trash,
                    onBack = { navController.popBackStack() },
                    onRestore = { messageId -> viewModel.restoreFromTrash(messageId) },
                    onPermanentDelete = { messageId -> viewModel.permanentlyDeleteFromTrash(messageId) }
                )
            }
            composable("scheduled") {
                PlaceholderScreen(title = "زمان‌بندی‌شده", onBack = { navController.popBackStack() })
            }
            composable("blocked") {
                PlaceholderScreen(title = "مسدودشده‌ها", onBack = { navController.popBackStack() })
            }
            composable("private") {
                PlaceholderScreen(title = "خصوصی", onBack = { navController.popBackStack() })
            }
        }
    }
}
