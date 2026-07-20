package com.petro.smsapp

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.petro.smsapp.ui.ConversationListScreen
import com.petro.smsapp.ui.NewMessageScreen
import com.petro.smsapp.ui.ThreadScreen
import com.petro.smsapp.viewmodel.SmsViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SmsViewModel by viewModels()

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            viewModel.loadConversations()
        }
    }

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkPermissionsAndLoad()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    AppNavigation(viewModel)
                }
            }
        }

        if (!DefaultSmsAppHelper.isDefaultSmsApp(this)) {
            requestRoleLauncher.launch(DefaultSmsAppHelper.getRequestRoleIntent(this))
        } else {
            checkPermissionsAndLoad()
        }
    }

    private fun checkPermissionsAndLoad() {
        val permissions = mutableListOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionsLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun AppNavigation(viewModel: SmsViewModel) {
    val navController = rememberNavController()
    val conversations by viewModel.conversations.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val newTarget by viewModel.newConversationTarget.collectAsState()

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

    NavHost(navController = navController, startDestination = "list") {
        composable("list") {
            ConversationListScreen(
                conversations = conversations,
                onConversationClick = { conversation ->
                    viewModel.loadThread(conversation.threadId)
                    navController.navigate("thread/${conversation.threadId}/${conversation.address}/${conversation.displayName}")
                },
                onComposeClick = {
                    viewModel.searchContacts("")
                    navController.navigate("new")
                }
            )
        }
        composable("new") {
            NewMessageScreen(
                contacts = contacts,
                onSearchChange = { query -> viewModel.searchContacts(query) },
                onSend = { address, body -> viewModel.sendNewMessage(address, body) },
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

            ThreadScreen(
                displayName = displayName,
                messages = messages,
                onSend = { body -> viewModel.sendMessage(address, body, threadId) },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
