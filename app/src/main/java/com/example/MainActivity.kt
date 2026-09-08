package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.CameraPreviewScreen
import com.example.ui.screens.CitizenScreen
import com.example.ui.screens.ControlCenterScreen
import com.example.ui.screens.DemoScreen
import com.example.ui.screens.ResponderScreen
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.CleanCanvas
import com.example.ui.theme.CleanWhite
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateBorderLight
import com.example.ui.theme.TextSlate400
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
        setContent {
            MyApplicationTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainApp(viewModel: EmergencyViewModel) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.CITIZEN) }
    var showCameraPreviewScreen by rememberSaveable { mutableStateOf(false) }

    if (showCameraPreviewScreen) {
        CameraPreviewScreen(
            viewModel = viewModel,
            onNavigateBack = { showCameraPreviewScreen = false }
        )
    } else {
        Scaffold(
            bottomBar = {
                CleanBottomNavigation(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
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
                    MainTab.RESPONDER -> ResponderScreen(viewModel = viewModel)
                    MainTab.CONTROL -> ControlCenterScreen(viewModel = viewModel)
                    MainTab.DEMO -> DemoScreen(
                        viewModel = viewModel,
                        onOpenLiveCamera = { showCameraPreviewScreen = true }
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

