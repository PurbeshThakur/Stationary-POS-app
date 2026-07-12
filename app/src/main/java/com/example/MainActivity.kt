package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.InventoryViewModel
import com.example.ui.screens.AiAdvisorScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SuperAdminScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppLayout()
            }
        }
    }
}

sealed class NavigationItem(val route: String, val title: String, val icon: ImageVector) {
    object POS : NavigationItem("pos", "POS Sell", Icons.Default.ShoppingCart)
    object Inventory : NavigationItem("inventory", "Inventory", Icons.Default.Inventory)
    object Reports : NavigationItem("reports", "Reports", Icons.Default.QueryStats)
    object AIAdvisor : NavigationItem("ai_advisor", "AI Advisor", Icons.Default.AutoAwesome)
    object CloudSync : NavigationItem("cloud_sync", "Cloud Sync", Icons.Default.Backup)
    object Admin : NavigationItem("super_admin", "Admin", Icons.Default.AdminPanelSettings)
}

@Composable
fun MainAppLayout() {
    val viewModel: InventoryViewModel = viewModel()
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val appUpdateRequired by viewModel.appUpdateRequired.collectAsState()
    val appLanguage by viewModel.appLanguage.collectAsState()

    if (appUpdateRequired != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissUpdateDialog() },
            title = {
                Text(
                    text = if (appLanguage == "ne") "नयाँ संस्करण उपलब्ध छ" else "Update Available",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = if (appLanguage == "ne") {
                        "एपको नयाँ संस्करण (${appUpdateRequired}) उपलब्ध छ। कृपया राम्रो अनुभवको लागि एप अपडेट गर्नुहोस्।"
                    } else {
                        "A new version of the app (${appUpdateRequired}) is available. Please update the app for the best experience."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissUpdateDialog() }
                ) {
                    Text(text = if (appLanguage == "ne") "ठीक छ" else "OK")
                }
            }
        )
    }

    if (loggedInUser == null) {
        LoginScreen(
            viewModel = viewModel,
            onLoginSuccess = {}
        )
    } else {
        val navController = rememberNavController()
        val navItems = remember(loggedInUser) {
            val list = mutableListOf(
                NavigationItem.POS,
                NavigationItem.Inventory,
                NavigationItem.Reports,
                NavigationItem.AIAdvisor,
                NavigationItem.CloudSync
            )
            if (loggedInUser?.isSuperAdmin == true || loggedInUser?.role == com.example.ui.UserRole.ADMIN) {
                list.add(NavigationItem.Admin)
            }
            list
        }

        val lastSentSms by viewModel.lastSentSms.collectAsState()
        
        LaunchedEffect(lastSentSms) {
            if (lastSentSms != null) {
                kotlinx.coroutines.delay(4000)
                viewModel.clearLastSentSms()
            }
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    navItems.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = {
                                val translationKey = when (item.route) {
                                    "pos" -> "pos_sell"
                                    "inventory" -> "inventory"
                                    "reports" -> "reports"
                                    "ai_advisor" -> "ai_advisor"
                                    "cloud_sync" -> "cloud_sync"
                                    "super_admin" -> "admin"
                                    else -> item.route
                                }
                                Text(com.example.util.t(translationKey, viewModel))
                            },
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        // Pop up to the start destination of the graph to
                                        // avoid building up a large stack of destinations
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        // Avoid multiple copies of the same destination when
                                        // reselecting the same item
                                        launchSingleTop = true
                                        // Restore state when reselecting a previously selected item
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = NavigationItem.POS.route,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    composable(NavigationItem.POS.route) {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToInventory = {
                                navController.navigate(NavigationItem.Inventory.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(NavigationItem.Inventory.route) {
                        InventoryScreen(viewModel = viewModel)
                    }
                    composable(NavigationItem.Reports.route) {
                        ReportsScreen(viewModel = viewModel)
                    }
                    composable(NavigationItem.AIAdvisor.route) {
                        AiAdvisorScreen(viewModel = viewModel)
                    }
                    composable(NavigationItem.CloudSync.route) {
                        com.example.ui.screens.CloudSyncScreen(viewModel = viewModel)
                    }
                    composable(NavigationItem.Admin.route) {
                        SuperAdminScreen(viewModel = viewModel)
                    }
                }

                // --- Global Simulated SMS Alert Slide-down Card ---
                AnimatedVisibility(
                    visible = lastSentSms != null,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 32.dp)
                        .zIndex(100f)
                ) {
                    lastSentSms?.let { sms ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(0.92f)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = "SMS icon",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "SMS Dispatch Alert 💬",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = sms.type,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "To: ${sms.recipient}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = sms.message,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
