package com.sitesweep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sitesweep.ui.sessions.SessionDetailScreen
import com.sitesweep.ui.sessions.SessionDetailViewModel
import com.sitesweep.ui.sessions.SessionListScreen
import com.sitesweep.ui.sessions.SessionListViewModel
import com.sitesweep.ui.sweep.SweepScreen
import com.sitesweep.ui.sweep.SweepViewModel
import com.sitesweep.ui.theme.PaletteInk
import com.sitesweep.ui.theme.PaletteSafetyOrange
import com.sitesweep.ui.theme.SiteSweepTheme
import com.sitesweep.ui.theme.TextLightSecondary

sealed interface AppScreen {
    data object SessionList : AppScreen
    data class Sweep(val sessionId: String) : AppScreen
    data class SessionDetail(val sessionId: String) : AppScreen
    data class Revisit(val locationKey: String, val captureId: String) : AppScreen
}

/**
 * Single activity hosting SiteSweep with clean state-based Compose navigation.
 */
class MainActivity : ComponentActivity() {

    private val sessionListViewModel: SessionListViewModel by viewModels()
    private val sessionDetailViewModel: SessionDetailViewModel by viewModels()
    private val sweepViewModel: SweepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SiteSweepTheme {
                val context = LocalContext.current
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
                }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionsLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    }
                }

                if (hasCameraPermission) {
                    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.SessionList) }

                    when (val screen = currentScreen) {
                        is AppScreen.SessionList -> {
                            SessionListScreen(
                                viewModel = sessionListViewModel,
                                onStartSweep = { sessionId ->
                                    currentScreen = AppScreen.Sweep(sessionId)
                                },
                                onSessionClick = { sessionId ->
                                    currentScreen = AppScreen.SessionDetail(sessionId)
                                }
                            )
                        }

                        is AppScreen.Sweep -> {
                            BackHandler {
                                currentScreen = AppScreen.SessionList
                            }
                            SweepScreen(
                                viewModel = sweepViewModel,
                                sessionId = screen.sessionId,
                                onNavigateToSessions = {
                                    currentScreen = AppScreen.SessionList
                                },
                                onCaptureClick = { capture ->
                                    currentScreen = AppScreen.Revisit(capture.locationKey, capture.id)
                                }
                            )
                        }

                        is AppScreen.SessionDetail -> {
                            BackHandler {
                                currentScreen = AppScreen.SessionList
                            }
                            SessionDetailScreen(
                                viewModel = sessionDetailViewModel,
                                sessionId = screen.sessionId,
                                onBack = {
                                    currentScreen = AppScreen.SessionList
                                },
                                onResumeSweep = { sid ->
                                    currentScreen = AppScreen.Sweep(sid)
                                },
                                onCaptureClick = { locationKey, captureId ->
                                    currentScreen = AppScreen.Revisit(locationKey, captureId)
                                }
                            )
                        }

                        is AppScreen.Revisit -> {
                            BackHandler {
                                currentScreen = AppScreen.SessionList
                            }
                            // RevisitScreen will be attached in Item 5
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(PaletteInk)
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "REVISIT LOCATION: ${screen.locationKey}",
                                        color = PaletteSafetyOrange,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { currentScreen = AppScreen.SessionList },
                                        colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                                        shape = RoundedCornerShape(2.dp)
                                    ) {
                                        Text(
                                            text = "BACK TO SESSIONS",
                                            color = PaletteInk,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    CameraPermissionRequiredScreen(
                        onRequestPermission = {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.RECORD_AUDIO
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPermissionRequiredScreen(
    onRequestPermission: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaletteInk)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CAMERA ACCESS REQUIRED",
                color = PaletteSafetyOrange,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "SiteSweep requires camera access for on-device structural distress inference.",
                color = TextLightSecondary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = PaletteSafetyOrange),
                shape = RoundedCornerShape(2.dp)
            ) {
                Text(
                    text = "GRANT PERMISSION",
                    color = PaletteInk,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
