package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InventoryViewModel
import com.example.ui.User
import com.example.ui.UserRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: InventoryViewModel,
    onLoginSuccess: () -> Unit
) {
    val users by viewModel.usersListState.collectAsState()
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var pinText by remember { mutableStateOf("") }
    var pinVisible by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val loggedInUser by viewModel.loggedInUser.collectAsState()
    LaunchedEffect(loggedInUser) {
        if (loggedInUser != null) {
            onLoginSuccess()
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            // Ambient glowing background with shades of blue, purple, and pink
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F172A), // Deep Slate Blue
                            Color(0xFF1E1B4B), // Purple-Indigo
                            Color(0xFF311042)  // Deep Pinkish Magenta
                        )
                    )
                )

                // Glowing blurry ambient spots
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.2f), Color.Transparent),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.15f, size.height * 0.2f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.2f), Color.Transparent),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.85f, size.height * 0.5f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFEC4899).copy(alpha = 0.15f), Color.Transparent),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * 0.35f, size.height * 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section with Language switch and Title Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Language toggle switch at the top right
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        CustomLanguageToggle(viewModel = viewModel)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Brand Logo - Open Book Icon
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                            .border(1.2.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = "Stationery Store Logo",
                            tint = Color(0xFFFFD700), // Golden yellow accent
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "PURBESH STATIONERY",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            color = Color.White,
                            fontSize = 24.sp
                        ),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Smart Retail POS & Inventory System",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }

                // Center Glassmorphism card for User Selection
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.03f)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = com.example.util.t("select_profile", viewModel),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                        )

                        // Custom horizontal layout for the 3 requested profiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            users.forEach { user ->
                                val isSelected = selectedUser?.username == user.username
                                UserProfileOption(
                                    user = user,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedUser = user
                                        showError = false
                                        pinText = ""
                                    }
                                )
                            }
                        }

                        // PIN Input section (slides/fades in)
                        AnimatedVisibility(
                            visible = selectedUser != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            selectedUser?.let { user ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = Color.White.copy(alpha = 0.15f)
                                    )

                                    Text(
                                        text = com.example.util.t("enter_pin", viewModel),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    )

                                    OutlinedTextField(
                                        value = pinText,
                                        onValueChange = {
                                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                                pinText = it
                                                showError = false
                                            }
                                        },
                                        label = { Text(com.example.util.t("security_pin", viewModel), color = Color.White.copy(alpha = 0.8f)) },
                                        placeholder = { Text("••••", color = Color.White.copy(alpha = 0.4f)) },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "PIN", tint = Color.White.copy(alpha = 0.8f)) },
                                        trailingIcon = {
                                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                                Icon(
                                                    imageVector = if (pinVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = if (pinVisible) "Hide PIN" else "Show PIN",
                                                    tint = Color.White.copy(alpha = 0.8f)
                                                )
                                            }
                                        },
                                        visualTransformation = if (pinVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color.White,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                            focusedLabelColor = Color.White,
                                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                            cursorColor = Color.White
                                        ),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        ),
                                        singleLine = true,
                                        isError = showError,
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    )

                                    if (showError) {
                                        Text(
                                            text = errorMessage,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (viewModel.login(user.username, pinText) == "SUCCESS") {
                                                onLoginSuccess()
                                            } else {
                                                showError = true
                                                errorMessage = if (users.find { it.username == user.username }?.isEnabled == false) "This staff profile is currently disabled. Please contact the system administrator." else "Incorrect security PIN. Please try again."
                                                pinText = ""
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(0.9f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFD700), // Glowing Yellow Accent
                                            contentColor = Color(0xFF1E020A)
                                        )
                                    ) {
                                        Icon(Icons.Default.Login, contentDescription = "Log In")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(com.example.util.t("log_in", viewModel), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Support footer message
                Text(
                    text = "Need assistance? Contact 9746638620",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.8f),
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}

@Composable
fun CustomLanguageToggle(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    val currentLanguage by viewModel.appLanguage.collectAsState()
    val isEnglish = currentLanguage == "en"

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // EN pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (isEnglish) Color(0xFFFFD700) else Color.Transparent)
                .clickable { viewModel.setAppLanguage("en") }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "EN",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = if (isEnglish) Color(0xFF1E020A) else Color.White.copy(alpha = 0.7f)
            )
        }

        // नेपाली pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (!isEnglish) Color(0xFFFFD700) else Color.Transparent)
                .clickable { viewModel.setAppLanguage("ne") }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "नेपाली",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                color = if (!isEnglish) Color(0xFF1E020A) else Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun UserProfileOption(
    user: User,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.1f else 1.0f, label = "scale")

    val glowColor = when (user.username.lowercase()) {
        "purbesh" -> Color(0xFF00C853) // Green for Purbesh
        "anita" -> Color(0xFF9C27B0)   // Purple for Anita
        "d" -> Color(0xFFFF9800)       // Orange for D
        else -> Color(user.avatarColor)
    }

    val displayRole = when (user.username.lowercase()) {
        "purbesh" -> "Admin"
        "anita" -> "Cashier/Staff"
        "d" -> "Staff"
        else -> user.role.label
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        // Glowing profile circle
        Box(
            modifier = Modifier
                .size(76.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isSelected) 0.35f else 0.08f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(glowColor, CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.fullName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = user.fullName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
            ),
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = displayRole,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = glowColor.copy(alpha = if (isSelected) 1f else 0.75f)
            ),
            textAlign = TextAlign.Center
        )
    }
}
