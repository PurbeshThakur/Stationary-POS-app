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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
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
        enableHighRefreshRate()
        setContent {
            MyApplicationTheme {
                MainAppLayout()
            }
        }
    }

    private fun enableHighRefreshRate() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    this.display
                } else {
                    windowManager.defaultDisplay
                }
                val supportedModes = display?.supportedModes
                val highestMode = supportedModes?.maxByOrNull { it.refreshRate }
                if (highestMode != null && highestMode.refreshRate > 60f) {
                    val layoutParams = window.attributes
                    layoutParams.preferredDisplayModeId = highestMode.modeId
                    window.attributes = layoutParams
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to set high refresh rate: ${e.message}")
        }
    }
}

sealed class NavigationItem(val route: String, val title: String, val icon: ImageVector) {
    object POS : NavigationItem("pos", "POS Sell", Icons.Default.ShoppingCart)
    object Inventory : NavigationItem("inventory", "Inventory", Icons.Default.Inventory)
    object Reports : NavigationItem("reports", "Reports", Icons.Default.QueryStats)
    object AIAdvisor : NavigationItem("ai_advisor", "AI Advisor", Icons.Default.AutoAwesome)
    object StoreCredit : NavigationItem("store_credit", "Store Credit", Icons.Default.AccountBalance)
    object CloudSync : NavigationItem("cloud_sync", "Cloud Sync", Icons.Default.Backup)
    object Admin : NavigationItem("super_admin", "Admin", Icons.Default.AdminPanelSettings)
}

@Composable
fun MainAppLayout() {
    val viewModel: InventoryViewModel = viewModel()
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val currentRole by viewModel.currentUserRole.collectAsState()
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
        val navItems = remember(loggedInUser, currentRole) {
            val items = mutableListOf<NavigationItem>()
            val isSuper = loggedInUser?.isSuperAdmin == true

            if (isSuper || loggedInUser?.canPerformSale == true) {
                items.add(NavigationItem.POS)
            }
            if (isSuper || loggedInUser?.canManageInventory == true) {
                items.add(NavigationItem.Inventory)
            }
            if (isSuper || loggedInUser?.canViewReports == true) {
                items.add(NavigationItem.Reports)
            }
            if (isSuper || loggedInUser?.canUseAiAdvisor == true) {
                items.add(NavigationItem.AIAdvisor)
            }
            items.add(NavigationItem.CloudSync)
            items.add(NavigationItem.Admin)
            items.toList()
        }

        val startDest = remember(navItems) {
            navItems.firstOrNull()?.route ?: NavigationItem.POS.route
        }

        var showAdminUnlockDialog by remember { mutableStateOf(false) }
        val lastSentSms by viewModel.lastSentSms.collectAsState()
        
        LaunchedEffect(lastSentSms) {
            if (lastSentSms != null) {
                kotlinx.coroutines.delay(4000)
                viewModel.clearLastSentSms()
            }
        }

        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isTabletOrLandscape = configuration.screenWidthDp >= 600 || 
                configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        Box(modifier = Modifier.fillMaxSize()) {
            if (isTabletOrLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    NeonGlowNavigationRail(
                        navController = navController,
                        viewModel = viewModel,
                        navItems = navItems,
                        currentRoute = currentRoute,
                        onAdminClickWithNoAccess = {
                            showAdminUnlockDialog = true
                        }
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = startDest,
                            modifier = Modifier.fillMaxSize()
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
                            composable(NavigationItem.StoreCredit.route) {
                                com.example.ui.screens.StoreCreditScreen(viewModel = viewModel)
                            }
                            composable(NavigationItem.CloudSync.route) {
                                com.example.ui.screens.CloudSyncScreen(viewModel = viewModel)
                            }
                            composable(NavigationItem.Admin.route) {
                                SuperAdminScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            } else {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        NeonGlowNavigationBar(
                            navController = navController,
                            viewModel = viewModel,
                            navItems = navItems,
                            currentRoute = currentRoute,
                            onAdminClickWithNoAccess = {
                                showAdminUnlockDialog = true
                            }
                        )
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDest,
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
                        composable(NavigationItem.StoreCredit.route) {
                            com.example.ui.screens.StoreCreditScreen(viewModel = viewModel)
                        }
                        composable(NavigationItem.CloudSync.route) {
                            com.example.ui.screens.CloudSyncScreen(viewModel = viewModel)
                        }
                        composable(NavigationItem.Admin.route) {
                            SuperAdminScreen(viewModel = viewModel)
                        }
                    }
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
                        modifier = Modifier
                            .widthIn(max = 500.dp)
                            .fillMaxWidth(0.92f)
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

            // --- Admin PIN Authorization Dialog ---
            if (showAdminUnlockDialog) {
                var unlockPin by remember { mutableStateOf("") }
                var unlockError by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = {
                        showAdminUnlockDialog = false
                        unlockPin = ""
                        unlockError = false
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Unlock",
                                tint = Color(0xFF3A86FF), // Neon Azure Blue
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Admin Authorization",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Accessing the Super Admin Terminal requires Administrator verification. Please enter the Admin PIN.",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )

                            OutlinedTextField(
                                value = unlockPin,
                                onValueChange = {
                                    if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                        unlockPin = it
                                        unlockError = false
                                    }
                                },
                                label = { Text("Enter Admin PIN", color = Color.White.copy(alpha = 0.7f)) },
                                placeholder = { Text("••••", color = Color.White.copy(alpha = 0.4f)) },
                                visualTransformation = PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF3A86FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                    focusedLabelColor = Color(0xFF3A86FF),
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                    cursorColor = Color(0xFF3A86FF)
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                isError = unlockError,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (unlockError) {
                                Text(
                                    text = "Incorrect Admin PIN. Please try again.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val authUser = viewModel.usersList.find { it.pin == unlockPin && it.isEnabled && it.role == com.example.ui.UserRole.ADMIN }
                                if (authUser != null) {
                                    viewModel.setUserRole(com.example.ui.UserRole.ADMIN)
                                    showAdminUnlockDialog = false
                                    unlockPin = ""
                                    unlockError = false
                                    // Successfully elevated, navigate to super_admin screen!
                                    navController.navigate(NavigationItem.Admin.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                } else {
                                    unlockError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A86FF),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Verify & Unlock", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showAdminUnlockDialog = false
                                unlockPin = ""
                                unlockError = false
                            }
                        ) {
                            Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                        }
                    },
                    containerColor = Color(0xFF13131A), // Sleek Dark Grayish Black
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                )
            }
        }
    }
}

@Composable
fun NeonGlowNavigationRail(
    navController: androidx.navigation.NavController,
    viewModel: InventoryViewModel,
    navItems: List<NavigationItem>,
    currentRoute: String?,
    onAdminClickWithNoAccess: () -> Unit
) {
    Surface(
        color = Color(0xFF09090B), // Sleek pitch black background
        modifier = Modifier
            .fillMaxHeight()
            .width(80.dp)
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            ),
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
        shadowElevation = 20.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 16.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sleek icon or branding space at top
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.03f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "App Logo",
                    tint = Color(0xFF00F5D4).copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                
                // Futuristic glowing neon colors
                val neonColor = when (item.route) {
                    "pos" -> Color(0xFF00F5D4)        // Glowing blue-green
                    "inventory" -> Color(0xFFBD00FF)  // Glowing purple
                    "reports" -> Color(0xFFFF9F1C)    // Glowing orange
                    "ai_advisor" -> Color(0xFFFF007F) // Glowing pink
                    "store_credit" -> Color(0xFF3A86FF) // Glowing deep azure
                    "cloud_sync" -> Color(0xFF00E5FF) // Glowing cyan
                    "super_admin" -> Color(0xFF00B4D8) // Glowing light blue
                    else -> Color.White
                }

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1.0f,
                    label = "iconScale"
                )
                val glowAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0.35f else 0.0f,
                    label = "glowAlpha"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            val loggedInUser = viewModel.loggedInUser.value
                            val isAdmin = loggedInUser?.isSuperAdmin == true || loggedInUser?.role == com.example.ui.UserRole.ADMIN
                            if (item.route == "super_admin" && !isAdmin) {
                                onAdminClickWithNoAccess()
                            } else {
                                if (currentRoute != item.route) {
                                    if (currentRoute == "super_admin" && item.route != "super_admin") {
                                        viewModel.restoreBaseUserRole()
                                    }
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    // Glowing background container
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(48.dp)
                    ) {
                        // Ambient radial neon glow
                        if (glowAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                neonColor.copy(alpha = glowAlpha),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }

                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) neonColor else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val translationKey = when (item.route) {
                        "pos" -> "pos_sell"
                        "inventory" -> "inventory"
                        "reports" -> "reports"
                        "ai_advisor" -> "ai_advisor"
                        "cloud_sync" -> "cloud_sync"
                        "super_admin" -> "admin"
                        else -> item.route
                    }

                    Text(
                        text = com.example.util.t(translationKey, viewModel),
                        fontSize = 9.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) neonColor else Color.White.copy(alpha = 0.35f),
                        letterSpacing = 0.1.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = if (isSelected) {
                                androidx.compose.ui.graphics.Shadow(
                                    color = neonColor.copy(alpha = 0.8f),
                                    blurRadius = 8f,
                                    offset = Offset(0f, 0f)
                                )
                            } else null
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun NeonGlowNavigationBar(
    navController: androidx.navigation.NavController,
    viewModel: InventoryViewModel,
    navItems: List<NavigationItem>,
    currentRoute: String?,
    onAdminClickWithNoAccess: () -> Unit
) {
    Surface(
        color = Color(0xFF09090B), // Sleek pitch black background
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    )
                ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEach { item ->
                val isSelected = currentRoute == item.route
                
                // Futuristic glowing neon colors
                val neonColor = when (item.route) {
                    "pos" -> Color(0xFF00F5D4)        // Glowing blue-green
                    "inventory" -> Color(0xFFBD00FF)  // Glowing purple
                    "reports" -> Color(0xFFFF9F1C)    // Glowing orange
                    "ai_advisor" -> Color(0xFFFF007F) // Glowing pink
                    "store_credit" -> Color(0xFF3A86FF) // Glowing deep azure
                    "cloud_sync" -> Color(0xFF00E5FF) // Glowing cyan
                    "super_admin" -> Color(0xFF00B4D8) // Glowing light blue
                    else -> Color.White
                }

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.2f else 1.0f,
                    label = "iconScale"
                )
                val glowAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 0.35f else 0.0f,
                    label = "glowAlpha"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null
                        ) {
                            val loggedInUser = viewModel.loggedInUser.value
                            val isAdmin = loggedInUser?.isSuperAdmin == true || loggedInUser?.role == com.example.ui.UserRole.ADMIN
                            if (item.route == "super_admin" && !isAdmin) {
                                onAdminClickWithNoAccess()
                            } else {
                                if (currentRoute != item.route) {
                                    if (currentRoute == "super_admin" && item.route != "super_admin") {
                                        viewModel.restoreBaseUserRole()
                                    }
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    // Glowing background container
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp)
                    ) {
                        // Ambient radial neon glow
                        if (glowAlpha > 0f) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                neonColor.copy(alpha = glowAlpha),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }

                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) neonColor else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    val translationKey = when (item.route) {
                        "pos" -> "pos_sell"
                        "inventory" -> "inventory"
                        "reports" -> "reports"
                        "ai_advisor" -> "ai_advisor"
                        "cloud_sync" -> "cloud_sync"
                        "super_admin" -> "admin"
                        else -> item.route
                    }

                    Text(
                        text = com.example.util.t(translationKey, viewModel),
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isSelected) neonColor else Color.White.copy(alpha = 0.35f),
                        letterSpacing = 0.2.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = if (isSelected) {
                                androidx.compose.ui.graphics.Shadow(
                                    color = neonColor.copy(alpha = 0.8f),
                                    blurRadius = 8f,
                                    offset = Offset(0f, 0f)
                                )
                            } else null
                        ),
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
