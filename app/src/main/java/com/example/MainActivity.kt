package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import java.util.UUID
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import com.example.auth.AuthState
import com.example.auth.FirebaseAuthManager
import com.example.messaging.UeriFirebaseMessagingService
import com.example.model.ueri.UserAccount
import com.example.model.ueri.UserRole

import com.example.ui.screens.CameraPreviewScreen
import com.example.ui.screens.CitizenScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.DemoScreen
import com.example.ui.screens.ResponderScreen
import com.example.ui.screens.ResponderVerificationPendingScreen
import com.example.ui.screens.WelcomeAuthScreen
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanCanvas
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.EmergencyRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SafetyGreen
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate400
import com.example.ui.theme.TextSlate500
import com.example.ui.theme.TextSlate600
import com.example.ui.theme.TextSlate900
import com.example.ui.viewmodel.EmergencyViewModel

enum class MainTab(val title: String, val icon: ImageVector, val tag: String) {
    CITIZEN("Citizen SOS", Icons.Default.Security, "tab_citizen"),
    RESPONDER("Responder", Icons.Default.DirectionsRun, "tab_responder"),
    CONTROL("Control Hub", Icons.Default.CellTower, "tab_control"),
    DEMO("Demo / Fast", Icons.Default.Speed, "tab_demo")
}

class MainActivity : ComponentActivity() {
    private val viewModel: EmergencyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        registerFcmToken()

        setContent {
            MyApplicationTheme {
                MainApp(
                    viewModel = viewModel,
                    initialIntent = intent
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val emergencyId = intent.getStringExtra(UeriFirebaseMessagingService.EXTRA_EMERGENCY_ID)
            ?: intent.getStringExtra("EXTRA_EMERGENCY_ID")
            ?: intent.getStringExtra("emergencyId")

        val navigateTab = intent.getStringExtra(UeriFirebaseMessagingService.EXTRA_NAVIGATE_TAB)
            ?: intent.getStringExtra("EXTRA_NAVIGATE_TAB")

        Log.d("MainActivity", "Processing notification deep-link intent: emergencyId=$emergencyId, navigateTab=$navigateTab")
        if (!emergencyId.isNullOrBlank()) {
            viewModel.handleEmergencyDeepLink(emergencyId)
        }
    }

    private var isFcmRegistrationAttempted = false


    private fun registerFcmToken() {
        if (isFcmRegistrationAttempted) return
        isFcmRegistrationAttempted = true

        val prefs = getSharedPreferences(FirebaseAuthManager.PREFS_NAME, Context.MODE_PRIVATE)
        val existingToken = prefs.getString(FirebaseAuthManager.KEY_FCM_TOKEN, "") ?: ""

        if (existingToken.isNotBlank()) {
            Log.d("MainActivity", "Reusing saved push token: $existingToken")
            viewModel.updateFcmToken(existingToken)
            return
        }

        val token = getOrCreateStableFallbackToken(prefs)
        Log.i("MainActivity", "Initialized stable device push token: $token")
        viewModel.updateFcmToken(token)
    }

    private fun getOrCreateStableFallbackToken(prefs: SharedPreferences): String {
        val saved = prefs.getString(FirebaseAuthManager.KEY_FCM_TOKEN, "") ?: ""
        if (saved.isNotBlank()) return saved

        val androidId = try {
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        } catch (_: Exception) { null }
        val token = "device_${androidId ?: UUID.randomUUID().toString().take(12)}"
        prefs.edit().putString(FirebaseAuthManager.KEY_FCM_TOKEN, token).apply()
        return token
    }
}

@Composable
fun MainApp(
    viewModel: EmergencyViewModel,
    initialIntent: Intent? = null
) {
    val authState by viewModel.authState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableStateOf(MainTab.CITIZEN) }
    var showCameraPreviewScreen by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var initialRoutingDone by rememberSaveable { mutableStateOf(false) }

    // Android 13+ Notification Permission Request
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("MainApp", "POST_NOTIFICATIONS granted: $isGranted")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Check if launched directly from an FCM notification deep link
    LaunchedEffect(initialIntent) {
        val navTab = initialIntent?.getStringExtra(UeriFirebaseMessagingService.EXTRA_NAVIGATE_TAB)
            ?: initialIntent?.getStringExtra("EXTRA_NAVIGATE_TAB")
        val emergencyId = initialIntent?.getStringExtra(UeriFirebaseMessagingService.EXTRA_EMERGENCY_ID)
            ?: initialIntent?.getStringExtra("EXTRA_EMERGENCY_ID")
            ?: initialIntent?.getStringExtra("emergencyId")

        if (navTab == "RESPONDER" || !emergencyId.isNullOrBlank()) {
            selectedTab = MainTab.RESPONDER
            if (!emergencyId.isNullOrBlank()) {
                viewModel.handleEmergencyDeepLink(emergencyId)
            }
        }
    }

    // Auto-route on initial authenticated session
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated && !initialRoutingDone) {
            val user = (authState as AuthState.Authenticated).user
            if (user.role == UserRole.RESPONDER.name) {
                selectedTab = MainTab.RESPONDER
            } else {
                selectedTab = MainTab.CITIZEN
            }
            initialRoutingDone = true
        } else if (authState is AuthState.Unauthenticated) {
            initialRoutingDone = false
        }
    }

    when (val state = authState) {
        is AuthState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CleanCanvas),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = BrandBlue,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "UERI EMERGENCY MESH",
                        color = TextSlate900,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Verifying security session...",
                        color = TextSlate500,
                        fontSize = 11.sp
                    )
                }
            }
        }

        is AuthState.Unauthenticated -> {
            WelcomeAuthScreen(
                viewModel = viewModel,
                onAuthSuccess = { user ->
                    if (user.role == UserRole.RESPONDER.name) {
                        selectedTab = MainTab.RESPONDER
                    } else {
                        selectedTab = MainTab.CITIZEN
                    }
                    initialRoutingDone = true
                }
            )
        }

        is AuthState.Authenticated -> {
            val user = state.user

            if (showCameraPreviewScreen) {
                CameraPreviewScreen(
                    viewModel = viewModel,
                    onNavigateBack = { showCameraPreviewScreen = false }
                )
            } else {
                Scaffold(
                    topBar = {
                        UeriTopSessionBar(
                            user = user,
                            onLogoutClick = { showLogoutDialog = true }
                        )
                    },
                    bottomBar = {
                        CleanBottomNavigation(
                            selectedTab = selectedTab,
                            onTabSelected = { tab ->
                                selectedTab = tab
                                when (tab) {
                                    MainTab.CITIZEN -> viewModel.authManager.switchRole(UserRole.CITIZEN)
                                    MainTab.RESPONDER -> viewModel.authManager.switchRole(UserRole.RESPONDER)
                                    MainTab.CONTROL -> viewModel.authManager.switchRole(UserRole.CONTROL_OPERATOR)
                                    MainTab.DEMO -> viewModel.authManager.switchRole(UserRole.CONTROL_OPERATOR)
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                    containerColor = CleanCanvas
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = CleanCanvas
                    ) {
                        when (selectedTab) {
                            MainTab.CITIZEN -> CitizenScreen(
                                viewModel = viewModel,
                                onOpenLiveCamera = { showCameraPreviewScreen = true }
                            )

                            MainTab.RESPONDER -> {
                                if (user.role == UserRole.RESPONDER.name && !user.verifiedResponder) {
                                    ResponderVerificationPendingScreen(
                                        user = user,
                                        viewModel = viewModel,
                                        onApproved = {
                                            // Verified: re-render ResponderScreen
                                        },
                                        onLogout = { viewModel.logout() }
                                    )
                                } else {
                                    ResponderScreen(viewModel = viewModel)
                                }
                            }

                            MainTab.CONTROL -> ControlCenterScreen(viewModel = viewModel)

                            MainTab.DEMO -> DemoScreen(
                                viewModel = viewModel,
                                onOpenLiveCamera = { showCameraPreviewScreen = true }
                            )
                        }
                    }
                }

                if (showLogoutDialog) {
                    AlertDialog(
                        onDismissRequest = { showLogoutDialog = false },
                        title = {
                            Text(
                                text = "Sign Out of UERI?",
                                fontWeight = FontWeight.Bold,
                                color = TextSlate900
                            )
                        },
                        text = {
                            Text(
                                text = "You will be signed out of this device. To trigger or receive emergency broadcasts again, you will need to sign back in.",
                                fontSize = 13.sp,
                                color = TextSlate600
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showLogoutDialog = false
                                    viewModel.logout()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Sign Out", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = { showLogoutDialog = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UeriTopSessionBar(
    user: UserAccount,
    onLogoutClick: () -> Unit
) {
    Surface(
        color = CleanWhite,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(BrandBlue.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (user.role == UserRole.RESPONDER.name) Icons.Default.DirectionsRun else Icons.Default.Security,
                        contentDescription = "User Role Icon",
                        tint = BrandBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = user.displayName.ifBlank { "UERI Operator" },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlate900
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isVerified = if (user.role == UserRole.RESPONDER.name) user.verifiedResponder else true
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (isVerified) SafetyGreen else AlertAmber, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (user.role == UserRole.RESPONDER.name) {
                                if (user.verifiedResponder) "VERIFIED RESPONDER" else "RESPONDER (PENDING VERIFICATION)"
                            } else "CITIZEN SOS ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVerified) SafetyGreen else AlertAmber
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF1F5F9))
                    .clickable(onClick = onLogoutClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("ueri_logout_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Logout,
                        contentDescription = "Sign Out",
                        tint = TextSlate600,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LOGOUT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSlate600
                    )
                }
            }
        }
    }
}

@Composable
private fun CleanBottomNavigation(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit
) {
    Surface(
        color = CleanWhite,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            HorizontalDivider(thickness = 1.dp, color = SlateBorderLight)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val itemColor = if (isSelected) BrandBlue else TextSlate400

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onTabSelected(tab) }
                            .testTag(tab.tag),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = itemColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = tab.title,
                                color = itemColor,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
