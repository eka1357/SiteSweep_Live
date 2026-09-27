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
import androidx.lifecycle.lifecycleScope
import com.sitesweep.ui.issues.CreateIssueScreen
import com.sitesweep.ui.issues.CreateIssueViewModel
import com.sitesweep.ui.issues.DashboardScreen
import com.sitesweep.ui.issues.DashboardViewModel
import com.sitesweep.ui.issues.IssueDetailScreen
import com.sitesweep.ui.issues.IssueDetailViewModel
import com.sitesweep.ui.issues.SelectCaptureScreen
import com.sitesweep.ui.revisit.RevisitScreen
import com.sitesweep.ui.revisit.RevisitViewModel
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
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface AppScreen {
    data object SessionList : AppScreen
    data class Sweep(val sessionId: String, val reinspectIssueId: String? = null) : AppScreen
    data class SessionDetail(val sessionId: String) : AppScreen
    data class Revisit(val locationKey: String, val captureId: String, val returnScreen: AppScreen = SessionList) : AppScreen
    data object Dashboard : AppScreen
    data class IssueDetail(val issueId: String) : AppScreen
    data class CreateIssue(val captureId: String) : AppScreen
    data object SelectCaptureForIssue : AppScreen
}

/**
 * Single activity hosting SiteSweep with clean state-based Compose navigation.
 */
class MainActivity : ComponentActivity() {

    private val sessionListViewModel: SessionListViewModel by viewModels()
    private val sessionDetailViewModel: SessionDetailViewModel by viewModels()
    private val sweepViewModel: SweepViewModel by viewModels()
    private val revisitViewModel: RevisitViewModel by viewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()
    private val issueDetailViewModel: IssueDetailViewModel by viewModels()
    private val createIssueViewModel: CreateIssueViewModel by viewModels()

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

                val app = context.applicationContext as SiteSweepApplication
                LaunchedEffect(Unit) {
                    val seeder = com.sitesweep.data.demo.DemoSeeder(context, app.repository)
                    if (!seeder.isSeeded()) {
                        seeder.seed()
                    }
                }

                if (hasCameraPermission) {
                    var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.SessionList) }
                    var showSettingsDialog by remember { mutableStateOf(false) }

                    if (showSettingsDialog) {
                        com.sitesweep.ui.settings.DemoSettingsDialog(
                            onDismiss = { showSettingsDialog = false }
                        )
                    }

                    when (val screen = currentScreen) {
                        is AppScreen.SessionList -> {
                            SessionListScreen(
                                viewModel = sessionListViewModel,
                                onStartSweep = { sessionId ->
                                    currentScreen = AppScreen.Sweep(sessionId)
                                },
                                onSessionClick = { sessionId ->
                                    currentScreen = AppScreen.SessionDetail(sessionId)
                                },
                                onOpenDashboard = {
                                    currentScreen = AppScreen.Dashboard
                                },
                                onOpenSettings = {
                                    showSettingsDialog = true
                                }
                            )
                        }

                        is AppScreen.Dashboard -> {
                            BackHandler {
                                currentScreen = AppScreen.SessionList
                            }
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onIssueClick = { issueId ->
                                    currentScreen = AppScreen.IssueDetail(issueId)
                                },
                                onStartSweep = {
                                    val newSid = UUID.randomUUID().toString()
                                    currentScreen = AppScreen.Sweep(newSid)
                                },
                                onCreateIssue = {
                                    currentScreen = AppScreen.SelectCaptureForIssue
                                },
                                onBack = {
                                    currentScreen = AppScreen.SessionList
                                }
                            )
                        }

                        is AppScreen.IssueDetail -> {
                            BackHandler {
                                currentScreen = AppScreen.Dashboard
                            }
                            IssueDetailScreen(
                                viewModel = issueDetailViewModel,
                                issueId = screen.issueId,
                                onBack = {
                                    currentScreen = AppScreen.Dashboard
                                },
                                onStartReinspection = { targetIssueId ->
                                    val sweepSessionId = UUID.randomUUID().toString()
                                    currentScreen = AppScreen.Sweep(
                                        sessionId = sweepSessionId,
                                        reinspectIssueId = targetIssueId
                                    )
                                }
                            )
                        }

                        is AppScreen.SelectCaptureForIssue -> {
                            BackHandler {
                                currentScreen = AppScreen.Dashboard
                            }
                            SelectCaptureScreen(
                                viewModel = createIssueViewModel,
                                onCaptureSelected = { captureId ->
                                    currentScreen = AppScreen.CreateIssue(captureId)
                                },
                                onBack = {
                                    currentScreen = AppScreen.Dashboard
                                }
                            )
                        }

                        is AppScreen.CreateIssue -> {
                            BackHandler {
                                currentScreen = AppScreen.Dashboard
                            }
                            CreateIssueScreen(
                                viewModel = createIssueViewModel,
                                captureId = screen.captureId,
                                onIssueCreated = { newIssueId ->
                                    currentScreen = AppScreen.IssueDetail(newIssueId)
                                },
                                onCancel = {
                                    currentScreen = AppScreen.Dashboard
                                }
                            )
                        }

                        is AppScreen.Sweep -> {
                            BackHandler {
                                sweepViewModel.endActiveSession()
                                currentScreen = if (screen.reinspectIssueId != null) {
                                    AppScreen.IssueDetail(screen.reinspectIssueId)
                                } else {
                                    AppScreen.SessionList
                                }
                            }

                            // If in reinspection mode, configure listener to automatically link capture to issue
                            LaunchedEffect(screen.sessionId, screen.reinspectIssueId) {
                                if (screen.reinspectIssueId != null) {
                                    sweepViewModel.setOnCaptureTriggeredListener { capture, _ ->
                                        lifecycleScope.launch {
                                            app.repository.attachCaptureToIssue(screen.reinspectIssueId, capture.id)
                                            currentScreen = AppScreen.IssueDetail(screen.reinspectIssueId)
                                        }
                                    }
                                } else {
                                    sweepViewModel.setOnCaptureTriggeredListener { _, _ -> }
                                }
                            }

                            SweepScreen(
                                viewModel = sweepViewModel,
                                sessionId = screen.sessionId,
                                backButtonLabel = if (screen.reinspectIssueId != null) "< ISSUE" else "< SESSIONS",
                                onNavigateToSessions = {
                                    sweepViewModel.endActiveSession()
                                    currentScreen = if (screen.reinspectIssueId != null) {
                                        AppScreen.IssueDetail(screen.reinspectIssueId)
                                    } else {
                                        AppScreen.SessionList
                                    }
                                },
                                onCaptureClick = { capture ->
                                    if (screen.reinspectIssueId != null) {
                                        lifecycleScope.launch {
                                            app.repository.attachCaptureToIssue(screen.reinspectIssueId, capture.id)
                                            currentScreen = AppScreen.IssueDetail(screen.reinspectIssueId)
                                        }
                                    } else {
                                        currentScreen = AppScreen.Revisit(
                                            locationKey = capture.locationKey,
                                            captureId = capture.id,
                                            returnScreen = AppScreen.Sweep(screen.sessionId)
                                        )
                                    }
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
                                    currentScreen = AppScreen.Revisit(
                                        locationKey = locationKey,
                                        captureId = captureId,
                                        returnScreen = AppScreen.SessionDetail(screen.sessionId)
                                    )
                                }
                            )
                        }

                        is AppScreen.Revisit -> {
                            BackHandler {
                                currentScreen = screen.returnScreen
                            }
                            RevisitScreen(
                                viewModel = revisitViewModel,
                                locationKey = screen.locationKey,
                                targetCaptureId = screen.captureId,
                                onBack = {
                                    currentScreen = screen.returnScreen
                                },
                                onCreateIssue = { capId ->
                                    currentScreen = AppScreen.CreateIssue(capId)
                                }
                            )
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
