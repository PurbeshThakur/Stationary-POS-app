package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AuditLog
import com.example.ui.InventoryViewModel
import com.example.ui.User
import com.example.ui.UserRole
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SuperAdminScreen(
    viewModel: InventoryViewModel,
    onBack: (() -> Unit)? = null
) {
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val users by viewModel.usersListState.collectAsState()
    val auditLogs by viewModel.auditLogs.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Profiles & Access", "System Audit Trails")

    var showAddDialog by remember { mutableStateOf(false) }
    var userToEdit by remember { mutableStateOf<User?>(null) }
    var showClearLogsConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Super Admin Terminal",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Security & Permissions Hub",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Admin icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .size(28.dp)
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add user")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Profile", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(onClick = { showClearLogsConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Audit logs",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Admin Control Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (index == 0) Icons.Default.Group else Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                when (selectedTab) {
                    0 -> ProfilesTabContent(
                        users = users,
                        loggedInUser = loggedInUser,
                        onEditUser = { userToEdit = it }
                    )
                    1 -> AuditLogsTabContent(
                        logs = auditLogs
                    )
                }
            }
        }
    }

    // Dialogue for Adding new user
    if (showAddDialog) {
        UserEditorDialog(
            user = null,
            loggedInUser = loggedInUser,
            onDismiss = { showAddDialog = false },
            onSave = { newUser ->
                viewModel.addOrUpdateUser(newUser)
                showAddDialog = false
            },
            onDelete = null
        )
    }

    // Dialogue for Editing existing user
    if (userToEdit != null) {
        UserEditorDialog(
            user = userToEdit,
            loggedInUser = loggedInUser,
            onDismiss = { userToEdit = null },
            onSave = { updatedUser ->
                viewModel.addOrUpdateUser(updatedUser)
                userToEdit = null
            },
            onDelete = { username ->
                viewModel.deleteUser(username)
                userToEdit = null
            }
        )
    }

    // Clear confirmation
    if (showClearLogsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearLogsConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error) },
            title = { Text("Clear Audit Log History?") },
            text = { Text("Are you absolutely sure you want to completely erase the system security logs? This action is irreversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAuditLogs()
                        showClearLogsConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Logs")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLogsConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfilesTabContent(
    users: List<User>,
    loggedInUser: User?,
    onEditUser: (User) -> Unit
) {
    if (users.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No user profiles loaded", color = MaterialTheme.colorScheme.outline)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Granular Access Enforcement",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Super Admins can define custom role capabilities, configure password pins, and disable staff access instantly.",
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(users, key = { it.username }) { user ->
            val isCurrent = user.username == loggedInUser?.username
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditUser(user) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (user.isEnabled) MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = if (isCurrent) 1.5.dp else 1.dp,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // User info details
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color(user.avatarColor), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.fullName.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = user.fullName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (isCurrent) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("You", fontSize = 10.sp) },
                                            modifier = Modifier.height(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "@${user.username} • PIN: ${user.pin}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Right-side indicators (Role & Enable status)
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val roleColor = if (user.role == UserRole.ADMIN) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(roleColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = user.role.label,
                                    color = roleColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }

                            if (!user.isEnabled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Disabled",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF00C853).copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Active",
                                        color = Color(0xFF00C853),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Permissions chips
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Assigned Capabilities:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        AdminFlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PermissionChip("Inventory Access", user.canManageInventory, Icons.Default.Inventory)
                            PermissionChip("Reports & Analytics", user.canViewReports, Icons.Default.QueryStats)
                            PermissionChip("POS Sell Terminal", user.canPerformSale, Icons.Default.ShoppingCart)
                            PermissionChip("Gemini AI Advisor", user.canUseAiAdvisor, Icons.Default.AutoAwesome)
                            if (user.isSuperAdmin) {
                                PermissionChip("Super Admin Powers", true, Icons.Default.AdminPanelSettings, isSuper = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Basic dynamic FlowRow implementation since standard FlowRow is part of experimental layout libraries
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val layoutWidth = constraints.maxWidth
        
        var currentY = 0
        var currentX = 0
        var rowHeight = 0
        
        val placements = mutableListOf<Pair<androidx.compose.ui.layout.Placeable, Pair<Int, Int>>>()
        
        placeables.forEach { placeable ->
            if (currentX + placeable.width > layoutWidth) {
                currentX = 0
                currentY += rowHeight + 16 // spacing
                rowHeight = 0
            }
            placements.add(placeable to (currentX to currentY))
            currentX += placeable.width + 16 // spacing
            rowHeight = maxOf(rowHeight, placeable.height)
        }
        
        layout(layoutWidth, maxOf(rowHeight + currentY, 30)) {
            placements.forEach { (placeable, position) ->
                placeable.placeRelative(position.first, position.second)
            }
        }
    }
}

@Composable
fun PermissionChip(
    label: String,
    enabled: Boolean,
    icon: ImageVector,
    isSuper: Boolean = false
) {
    val containerColor = when {
        isSuper -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        enabled -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val contentColor = when {
        isSuper -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(
                width = 0.5.dp,
                color = if (enabled) contentColor.copy(alpha = 0.3f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 10.sp,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Normal
            )
            if (!enabled && !isSuper) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Disabled",
                    tint = contentColor,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

@Composable
fun AuditLogsTabContent(
    logs: List<AuditLog>
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) logs
        else logs.filter {
            it.username.contains(searchQuery, ignoreCase = true) ||
            it.fullName.contains(searchQuery, ignoreCase = true) ||
            it.action.contains(searchQuery, ignoreCase = true) ||
            it.details.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filter audit trail logs...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            )
        )

        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "No logs",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (searchQuery.isEmpty()) "System Audit Logs empty" else "No logs match your search",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredLogs) { log ->
                    AuditLogCard(log = log)
                }
            }
        }
    }
}

@Composable
fun AuditLogCard(log: AuditLog) {
    val sdf = remember { SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }

    val logColor = when {
        log.action.contains("Sale", ignoreCase = true) -> Color(0xFF00C853) // Success Green
        log.action.contains("Created", ignoreCase = true) -> MaterialTheme.colorScheme.primary
        log.action.contains("Deleted", ignoreCase = true) -> MaterialTheme.colorScheme.error
        log.action.contains("Logged In", ignoreCase = true) -> MaterialTheme.colorScheme.secondary
        log.action.contains("Updated", ignoreCase = true) -> Color(0xFFFFAB00) // Warning yellow
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(logColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = log.fullName.take(1).uppercase(),
                            color = logColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = log.fullName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "@${log.username}",
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = formattedTime,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = log.action,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    color = logColor
                )

                if (log.details.isNotBlank()) {
                    Text(
                        text = log.details,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditorDialog(
    user: User?,
    loggedInUser: User?,
    onDismiss: () -> Unit,
    onSave: (User) -> Unit,
    onDelete: ((String) -> Unit)?
) {
    var username by remember { mutableStateOf(user?.username ?: "") }
    var pin by remember { mutableStateOf(user?.pin ?: "") }
    var fullName by remember { mutableStateOf(user?.fullName ?: "") }
    var selectedRole by remember { mutableStateOf(user?.role ?: UserRole.CASHIER) }
    var isEnabled by remember { mutableStateOf(user?.isEnabled ?: true) }

    var canManageInventory by remember { mutableStateOf(user?.canManageInventory ?: true) }
    var canViewReports by remember { mutableStateOf(user?.canViewReports ?: true) }
    var canPerformSale by remember { mutableStateOf(user?.canPerformSale ?: true) }
    var canUseAiAdvisor by remember { mutableStateOf(user?.canUseAiAdvisor ?: true) }
    var isSuperAdmin by remember { mutableStateOf(user?.isSuperAdmin ?: false) }

    var selectedColor by remember { mutableStateOf(user?.avatarColor ?: 0xFF6200EE) }

    val presetColors = listOf(
        0xFF6200EE, 0xFF00C853, 0xFFFFAB00, 0xFF00B0FF, 
        0xFFD500F9, 0xFFFF1744, 0xFF00E676, 0xFF1DE9B6
    )

    var nameError by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf(false) }

    val isEditingSelf = user?.username == loggedInUser?.username

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = if (user == null) "Create Staff Profile" else "Configure User Access") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            nameError = false
                        },
                        label = { Text("Full Name") },
                        isError = nameError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.lowercase().filter { char -> char.isLetterOrDigit() }
                            usernameError = false
                        },
                        label = { Text("Username") },
                        isError = usernameError,
                        enabled = user == null, // Username is key, cannot change
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pin = it
                                pinError = false
                            }
                        },
                        label = { Text("4-Digit Security PIN") },
                        isError = pinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = "Access Role",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRole.values().forEach { r ->
                            val selected = selectedRole == r
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    if (!isEditingSelf) {
                                        selectedRole = r
                                        // Auto adjust standard defaults on role change
                                        if (r == UserRole.ADMIN) {
                                            canManageInventory = true
                                            canViewReports = true
                                            canPerformSale = true
                                            canUseAiAdvisor = true
                                        }
                                    }
                                },
                                label = { Text(r.label) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.weight(1f),
                                enabled = !isEditingSelf
                            )
                        }
                    }
                    if (isEditingSelf) {
                        Text(
                            text = "You cannot modify your own core role to prevent accidental lockout.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            lineHeight = 13.sp
                        )
                    }
                }

                item {
                    Text(
                        text = "Avatar Profile Color",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        presetColors.forEach { colorVal ->
                            val isSelected = selectedColor == colorVal
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(
                                        width = if (isSelected) 2.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = colorVal }
                            )
                        }
                    }
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dynamic Security Permissions",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                item {
                    PermissionToggleRow("Can Manage Inventory & Stock", canManageInventory, { canManageInventory = it })
                }
                item {
                    PermissionToggleRow("Can View Reports & Analytics", canViewReports, { canViewReports = it })
                }
                item {
                    PermissionToggleRow("Can Access POS Sell Terminal", canPerformSale, { canPerformSale = it })
                }
                item {
                    PermissionToggleRow("Can Consult Gemini AI Advisor", canUseAiAdvisor, { canUseAiAdvisor = it })
                }
                item {
                    PermissionToggleRow(
                        label = "Grant Super Admin Power",
                        checked = isSuperAdmin,
                        onCheckedChange = { if (!isEditingSelf) isSuperAdmin = it },
                        enabled = !isEditingSelf
                    )
                }

                item {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Account Connection Status",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Disable user profiles to prevent any login.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { if (!isEditingSelf) isEnabled = it },
                            enabled = !isEditingSelf
                        )
                    }
                    if (isEditingSelf) {
                        Text(
                            text = "Self-disabling is prevented.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isBlank()) nameError = true
                    if (username.isBlank()) usernameError = true
                    if (pin.length != 4) pinError = true

                    if (fullName.isNotBlank() && username.isNotBlank() && pin.length == 4) {
                        onSave(
                            User(
                                username = username,
                                role = selectedRole,
                                pin = pin,
                                fullName = fullName,
                                avatarColor = selectedColor,
                                isEnabled = isEnabled,
                                canManageInventory = canManageInventory,
                                canViewReports = canViewReports,
                                canPerformSale = canPerformSale,
                                canUseAiAdvisor = canUseAiAdvisor,
                                isSuperAdmin = isSuperAdmin || selectedRole == UserRole.ADMIN
                            )
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                if (user != null && onDelete != null && !isEditingSelf) {
                    TextButton(
                        onClick = { onDelete(user.username) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Profile")
                    }
                }
            }
        }
    )
}

@Composable
fun PermissionToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.size(24.dp)
        )
    }
}
