package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.CloudBackupState
import com.example.ui.InventoryViewModel
import java.text.SimpleDateFormat
import java.util.*
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudSyncScreen(
    viewModel: InventoryViewModel
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val cloudVaultId by viewModel.cloudVaultId.collectAsState()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsState()
    val cloudBackupState by viewModel.cloudBackupState.collectAsState()
    val shopName by viewModel.shopName.collectAsState()

    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val googleAccountName by viewModel.googleAccountName.collectAsState()
    val googleDriveBackupState by viewModel.googleDriveBackupState.collectAsState()

    val scope = rememberCoroutineScope()

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    viewModel.setGoogleAccount(account.email, account.displayName)
                    Toast.makeText(context, "Google Account connected: ${account.email}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CloudSyncScreen", "Google Sign-In failed", e)
                Toast.makeText(context, "Connection failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val products by viewModel.allProducts.collectAsState()
    val sales by viewModel.allSales.collectAsState()

    var inputVaultId by remember { mutableStateOf("") }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }
    var showReportViewerDialog by remember { mutableStateOf(false) }

    // Sync input with state if updated
    LaunchedEffect(cloudVaultId) {
        if (cloudVaultId.isNotBlank() && inputVaultId.isBlank()) {
            inputVaultId = cloudVaultId
        }
    }

    // Dynamic feedback messages based on state
    LaunchedEffect(cloudBackupState) {
        when (cloudBackupState) {
            is CloudBackupState.Success -> {
                Toast.makeText(context, (cloudBackupState as CloudBackupState.Success).message, Toast.LENGTH_LONG).show()
                viewModel.resetCloudBackupState()
            }
            is CloudBackupState.Error -> {
                Toast.makeText(context, (cloudBackupState as CloudBackupState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetCloudBackupState()
            }
            else -> {}
        }
    }

    LaunchedEffect(googleDriveBackupState) {
        when (googleDriveBackupState) {
            is CloudBackupState.Success -> {
                Toast.makeText(context, (googleDriveBackupState as CloudBackupState.Success).message, Toast.LENGTH_LONG).show()
                viewModel.resetGoogleDriveBackupState()
            }
            is CloudBackupState.Error -> {
                Toast.makeText(context, (googleDriveBackupState as CloudBackupState.Error).message, Toast.LENGTH_LONG).show()
                viewModel.resetGoogleDriveBackupState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = "Cloud Storage",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                "Cloud Vault",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Storage, Reports & Backups",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Connection Status & Status Banner
            item {
                val isConnected = cloudVaultId.isNotBlank()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(
                                    if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = "Sync Status",
                                tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isConnected) "Vault Connected & Active" else "Cloud Storage Offline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (isConnected) {
                                    "Connected to Vault: $cloudVaultId"
                                } else {
                                    "Initialize a new Vault to securely back up your database."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Connection Setup Control Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Cloud Connection Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "To sync stationery data or restore previous states across instances, create a new Cloud Storage Vault, or enter an existing Vault ID.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = inputVaultId,
                            onValueChange = { inputVaultId = it },
                            label = { Text("Cloud Vault ID") },
                            placeholder = { Text("Enter 18+ digit unique vault token") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = "Key ID", tint = MaterialTheme.colorScheme.outline)
                            },
                            trailingIcon = {
                                if (inputVaultId.isNotBlank()) {
                                    IconButton(onClick = { inputVaultId = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear Input")
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    if (inputVaultId.isNotBlank()) {
                                        viewModel.setCloudVaultId(inputVaultId)
                                        Toast.makeText(context, "Cloud Vault ID set!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Please enter a valid Vault ID first.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Link, contentDescription = "Connect")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect ID")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    // Passing empty Vault ID will prompt creation of a brand new cloud backup vault on Server
                                    viewModel.setCloudVaultId("")
                                    viewModel.backupToCloud()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Create New")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Vault")
                            }
                        }

                        if (cloudVaultId.isNotBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Cloud Vault ID", cloudVaultId)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Vault ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Vault ID")
                                }

                                 TextButton(
                                    onClick = {
                                        viewModel.setCloudVaultId("")
                                        inputVaultId = ""
                                        Toast.makeText(context, "Cloud Vault Disconnected.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Disconnect")
                                }
                            }
                        }
                    }
                }
            }

            // Google Drive Cloud Backup Card
            item {
                val isGoogleConnected = googleAccountEmail.isNotBlank()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Google Drive",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Google Drive Backup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (isGoogleConnected) {
                            Text(
                                text = "Your Google account is connected. You can perform full database backups and restores directly using your personal Google Drive storage space.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Display connected email details
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = "Account",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = if (googleAccountName.isNotBlank()) googleAccountName else "Google User",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = googleAccountEmail,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        googleSignInClient.signOut().addOnCompleteListener {
                                            viewModel.setGoogleAccount(null, null)
                                            Toast.makeText(context, "Google Account disconnected.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Logout, contentDescription = "Disconnect")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Disconnect", fontSize = 11.sp)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val account = GoogleSignIn.getLastSignedInAccount(context)
                                                if (account == null) {
                                                    Toast.makeText(context, "Please connect your Google Account first.", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                val token = withContext(Dispatchers.IO) {
                                                    GoogleAuthUtil.getToken(context, account.email!!, "oauth2:https://www.googleapis.com/auth/drive.file")
                                                }
                                                if (token != null) {
                                                    viewModel.backupToGoogleDrive(token)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("CloudSyncScreen", "Failed to obtain auth token", e)
                                                Toast.makeText(context, "Authorization failed. Please try connecting your account again.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = "Back Up")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Back Up")
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val account = GoogleSignIn.getLastSignedInAccount(context)
                                                if (account == null) {
                                                    Toast.makeText(context, "Please connect your Google Account first.", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                val token = withContext(Dispatchers.IO) {
                                                    GoogleAuthUtil.getToken(context, account.email!!, "oauth2:https://www.googleapis.com/auth/drive.file")
                                                }
                                                if (token != null) {
                                                    viewModel.restoreFromGoogleDrive(token)
                                                }
                                            } catch (e: Exception) {
                                                Log.e("CloudSyncScreen", "Failed to obtain auth token", e)
                                                Toast.makeText(context, "Authorization failed. Please try connecting your account again.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Restore")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restore")
                                }
                            }
                        } else {
                            Text(
                                text = "Connect your Google account to back up and sync your store's inventory, sales records, and report documents directly into your Google Drive securely.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    val signInIntent = googleSignInClient.signInIntent
                                    googleSignInLauncher.launch(signInIntent)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.Login, contentDescription = "Connect Google")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Google Account")
                            }
                        }
                    }
                }
            }

            // Cloud Synced Actions Panel (Only active when Vault is set, otherwise display overlay)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Synchronize & Backups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Auto backup config toggle
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = "Auto Sync", tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("Auto-Sync on Checkouts", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Syncs inventory after each successful bill", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Switch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && cloudVaultId.isBlank()) {
                                        Toast.makeText(context, "Please configure/connect a Vault ID first.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.setAutoBackupEnabled(enabled)
                                        Toast.makeText(context, if (enabled) "Auto Sync enabled!" else "Auto Sync disabled.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        // Local Data stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Products Count", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("${products.size}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Sales Logged", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                    Text("${sales.size}", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }

                        // Manual push and pull buttons
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val isSyncing = cloudBackupState is CloudBackupState.Syncing

                            Button(
                                onClick = {
                                    viewModel.backupToCloud()
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("Syncing Cloud...")
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = "Backup Now")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Back Up Now (Save DB to Cloud)")
                                }
                            }

                            Button(
                                onClick = {
                                    if (cloudVaultId.isBlank()) {
                                        Toast.makeText(context, "Please enter/connect a Vault ID to restore.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        showConfirmRestoreDialog = true
                                    }
                                },
                                enabled = !isSyncing,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Restore From Cloud")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore Database (Download Backup)")
                            }
                        }
                    }
                }
            }

            // Cloud Transaction Reports Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Cloud Transactions Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Securely generate and store your local transaction reports and financials in the cloud. You can fetch and audit these reports anywhere.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = {
                                if (cloudVaultId.isBlank()) {
                                    Toast.makeText(context, "Please connect a Cloud Vault first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    showReportViewerDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = "Report")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("View & Share Cloud Reports")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    // Restore database warning dialog
    if (showConfirmRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRestoreDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Warning", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Overwrite Local Data?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("Warning: Restoring the database will completely overwrite all local products, inventory levels, sales, and transaction logs on this device with the cloud backup contents. This action cannot be undone.\n\nDo you wish to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmRestoreDialog = false
                        viewModel.restoreFromCloud(cloudVaultId)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Overwrite & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRestoreDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cloud Report Viewer dialog
    if (showReportViewerDialog) {
        val totalRevenue = sales.sumOf { it.totalAmount }
        val totalProfit = sales.sumOf { it.totalProfit }
        val dateStr = remember { SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date()) }

        val compiledReport = remember(sales, shopName) {
            val sb = StringBuilder()
            val paddedShop = shopName.uppercase().padEnd(30).take(30)
            sb.append("========================================\n")
            sb.append("       ${paddedShop}       \n")
            sb.append("         Cloud Transaction Audit        \n")
            sb.append("========================================\n")
            sb.append("Vault ID: $cloudVaultId\n")
            sb.append("Export Time: $dateStr\n")
            sb.append("Store Outlet: $shopName\n")
            sb.append("----------------------------------------\n")
            sb.append("Total Sales Transactions: ${sales.size}\n")
            sb.append("Total Revenue: NPR ${String.format("%.2f", totalRevenue)}\n")
            sb.append("Total Net Profit: NPR ${String.format("%.2f", totalProfit)}\n")
            sb.append("----------------------------------------\n")
            sb.append(String.format("%-10s %-12s %12s\n", "Sale ID", "Payment Mode", "Bill Total"))
            sb.append("----------------------------------------\n")
            for (sale in sales.take(15)) {
                sb.append(String.format("Sale #%-5d %-12s %12.2f\n", 1000 + sale.id, sale.paymentMode, sale.totalAmount))
            }
            if (sales.size > 15) {
                sb.append("... [Showing first 15 records] ...\n")
            }
            sb.append("========================================\n")
            sb.toString()
        }

        AlertDialog(
            onDismissRequest = { showReportViewerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HistoryEdu, contentDescription = "History", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cloud Financial Report", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color(0xFFF9F9F9))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        item {
                            Text(
                                text = compiledReport,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Cloud Sales Report", compiledReport)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Report copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Full Report")
                    }

                    Button(
                        onClick = {
                            viewModel.backupToCloud(compiledReport)
                            Toast.makeText(context, "Report successfully saved to cloud bucket!", Toast.LENGTH_SHORT).show()
                            showReportViewerDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Upload")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Archival to Cloud Storage")
                    }

                    TextButton(
                        onClick = { showReportViewerDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        )
    }
}
