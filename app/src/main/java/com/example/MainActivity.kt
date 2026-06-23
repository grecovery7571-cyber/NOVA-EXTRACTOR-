package com.example

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import com.example.ui.InstalledApp
import com.example.ui.TreeNode
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.HistoryEntity
import com.example.extractor.SourceExtractor
import com.example.ui.MainViewModel
import com.example.ui.MainViewModel.ViewState
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    // Inject our main viewmodel via a factory to support Room Context
    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(application) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val viewState by viewModel.viewState.collectAsStateWithLifecycle()
                val isFirstRun by viewModel.isFirstRun.collectAsStateWithLifecycle()
                val pendingExtraction by viewModel.pendingExtraction.collectAsStateWithLifecycle()
 
                // High-contrast, glowing radial canvas behind the glass layout
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackPure)
                        .drawBehind {
                            // Top-left neon blue glow: bg-[#00CFFF]/20 blur
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x3300CFFF), Color.Transparent),
                                    center = Offset(-size.width * 0.1f, -size.height * 0.1f),
                                    radius = size.width * 0.75f
                                )
                            )
                            // Bottom-right white glow: bg-white/5 blur
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0x0DFFFFFF), Color.Transparent),
                                    center = Offset(size.width * 1.1f, size.height * 1.1f),
                                    radius = size.width * 0.75f
                                )
                            )
                        }
                ) {
                    when (viewState) {
                        ViewState.SPLASH -> {
                            SplashScreenContent()
                        }
                        ViewState.MAIN -> {
                            MainDashboardScreen(
                                viewModel = viewModel,
                                showDisclaimer = isFirstRun
                            )
                        }
                        ViewState.PROCESSING -> {
                            ProcessingScreenContent(viewModel = viewModel)
                        }
                        ViewState.RESULT -> {
                            ResultScreenContent(viewModel = viewModel)
                        }
                    }

                    if (pendingExtraction != null) {
                        ExtractionConfirmationDialog(
                            request = pendingExtraction!!,
                            onConfirm = { viewModel.confirmExtraction() },
                            onDismiss = { viewModel.cancelExtraction() }
                        )
                    }
                }
            }
        }

        // Process file shared via external share sheets
        handleSharedIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type
        if (Intent.ACTION_SEND == action && type != null) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                viewModel.triggerExtractionRequest(uri)
            }
        } else if (Intent.ACTION_SEND_MULTIPLE == action && type != null) {
            val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            if (!uris.isNullOrEmpty()) {
                val firstUri = uris[0]
                viewModel.triggerExtractionRequest(firstUri)
                if (uris.size > 1) {
                    Toast.makeText(this, "Only one file can be processed at a time.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

/**
 * 1. SPLASH SCREEN (Section 1)
 * Exactly 3 seconds full-screen, Tech Logo and Orbit Scanner in Pitch Black and Neon styling.
 */
@Composable
fun SplashScreenContent() {
    // Elegant entrance scale & fade-in animations
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    
    // Infinite transition for futuristic rotation of the tech ring
    val infiniteTransition = rememberInfiniteTransition(label = "Splash Rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Tech Ring Angle"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000)), // Deep sleek OLED pitch black background
        contentAlignment = Alignment.Center
    ) {
        // Decorative background glowing tech orbits
        Box(
            modifier = Modifier
                .size(400.dp)
                .drawBehind {
                    drawCircle(
                        color = Color(0x1300CFFF),
                        radius = size.width * 0.45f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x0600CFFF),
                        radius = size.width * 0.35f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                    )
                }
        )

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(900)) + scaleIn(animationSpec = tween(900, easing = FastOutSlowInEasing)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                // Glow Tech ring + Logo
                Box(
                    modifier = Modifier.size(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Tech Scanner Circle (rotates with animation)
                    Box(
                        modifier = Modifier
                            .size(146.dp)
                            .graphicsLayer { rotationZ = angle }
                            .border(
                                width = 1.5.dp,
                                brush = Brush.sweepGradient(
                                    colors = listOf(NeonBlue, Color.Transparent, NeonBlue, Color.Transparent)
                                ),
                                shape = CircleShape
                            )
                    )
                    
                    // Actual beautiful Mascot logo with high contrast rounded border
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0F172A))
                            .border(2.dp, NeonBlue.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_nova_logo_1782155128469),
                            contentDescription = "Nova Extractor Logo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // App Brand Name with sleek Neon Text representation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NOVA ",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = WhitePure,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            fontSize = 30.sp
                        )
                    )
                    Text(
                        text = "EXTRACTOR",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = NeonBlue,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            fontSize = 30.sp
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "BY NOVA MAX • DEVELOPER KARTIK",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GreyOff,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        fontSize = 10.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(48.dp))

                // Sleek loading status loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = NeonBlue,
                    strokeWidth = 2.dp
                )
                
                Spacer(modifier = Modifier.height(14.dp))
                
                Text(
                    text = "OFFLINE SOURCE DECOMPILER ACTIVE",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GreyOff.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontSize = 9.sp
                    )
                )
            }
        }
    }
}

/**
 * 2. MAIN DASHBOARD SCREEN (Section 7.3)
 */
@Composable
fun MainDashboardScreen(
    viewModel: MainViewModel,
    showDisclaimer: Boolean
) {
    val context = LocalContext.current
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()

    var showAppsModal by remember { mutableStateOf(false) }

    LaunchedEffect(showAppsModal) {
        if (showAppsModal) {
            viewModel.loadInstalledApps()
        }
    }

    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.triggerExtractionRequest(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "NOVA ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = WhitePure,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                    Text(
                        text = "EXTRACTOR",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = NeonBlue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "BY NOVA MAX (DEVELOPER KARTIK)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GreyOff,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        fontSize = 10.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {

                // Interactive centered Glass circle file intake
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .size(192.dp) // w-48 -> 192dp
                        .clip(CircleShape)
                        .background(WhitePure.copy(alpha = 0.10f)) // bg-white/10
                        .border(1.5.dp, WhitePure.copy(alpha = 0.20f), CircleShape) // border-white/20
                        .clickable { filePicker.launch("*/*") }
                        .testTag("select_file_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp) // w-12 -> 48dp
                                .clip(CircleShape)
                                .background(WhitePure), // bg-white
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Icon",
                                tint = BlackPure, // text-black
                                modifier = Modifier.size(24.dp) // h-6 w-6 -> 24dp
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "TAP TO SELECT",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = WhitePure,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "APK or IPA",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GreyOff,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                Text(
                    text = "Drag and drop files here or browse your system to begin source code extraction.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = GreyOff.copy(alpha = 0.7f),
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.widthIn(max = 240.dp)
                )

                Button(
                    onClick = { showAppsModal = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 8.dp)
                        .testTag("extract_installed_app_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "Device Installed Apps",
                            tint = NeonBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "EXTRACT INSTALLED APP",
                            color = WhitePure,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // AI Decompiler Settings Card
                val isAiReconstruction by viewModel.isAiReconstructionEnabled.collectAsStateWithLifecycle()
                val isProjectExport by viewModel.isProjectExportEnabled.collectAsStateWithLifecycle()
                val isKeyAvailable = remember { com.example.extractor.GeminiDecompiler.isKeyAvailable() }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassCard, RoundedCornerShape(18.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Decompiler config",
                                tint = NeonBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DECOMPILER CONFIGURATION",
                                color = WhitePure,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Project Export toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Android Studio Project Export",
                                    color = WhitePure,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Generates build.gradle, settings, and wrapper directory structure",
                                    color = GreyOff,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = isProjectExport,
                                onCheckedChange = { viewModel.setProjectExport(it) },
                                modifier = Modifier.testTag("project_export_toggle"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BlackPure,
                                    checkedTrackColor = NeonBlue,
                                    uncheckedThumbColor = GreyOff,
                                    uncheckedTrackColor = GlassBg
                                )
                            )
                        }

                        HorizontalDivider(color = GlassBorder)

                        // AI Reconstruction toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "AI Deep Sources Reconstruction",
                                    color = WhitePure,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Rebuilds actual method bodies, class interfaces, and variable logic",
                                    color = GreyOff,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = isAiReconstruction,
                                onCheckedChange = { viewModel.setAiReconstruction(it) },
                                modifier = Modifier.testTag("ai_reconstruction_toggle"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = BlackPure,
                                    checkedTrackColor = NeonBlue,
                                    uncheckedThumbColor = GreyOff,
                                    uncheckedTrackColor = GlassBg
                                )
                            )
                        }

                        if (!isKeyAvailable && isAiReconstruction) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x22FF5353), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "⚠️ Configure GEMINI_API_KEY inside AI Studio Secrets to run AI AST method reconstruction.",
                                    color = Color(0xFFFF7E7E),
                                    fontSize = 9.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (isKeyAvailable && isAiReconstruction) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x114CAF50), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0x334CAF50), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF4CAF50), CircleShape)
                                    )
                                    Text(
                                        text = "Gemini AI Engine Active (gemini-3.1-pro-preview)",
                                        color = Color(0xFF81C784),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // History label and utilities
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Extraction History",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = WhitePure,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (historyList.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAllHistory() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Text(
                                text = "Clear All",
                                color = NeonBlue,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // History List (Section 7.3)
                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(GlassCard, RoundedCornerShape(24.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "Empty History",
                                tint = GreyOff,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No processed records yet",
                                color = GreyOff,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(historyList) { record ->
                            HistoryRowCard(
                                record = record,
                                onShare = { viewModel.shareOutputZip(context, record.outputPath) },
                                onDelete = { viewModel.deleteHistoryItem(record.id) },
                                onBrowse = { viewModel.openDecompiledSourceViewer(record) }
                            )
                        }
                    }
                }

                // Bottom Bar Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .drawBehind {
                            // Draw top thin border simulating border-t border-white/5
                            drawLine(
                                color = WhitePure.copy(alpha = 0.05f),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, 0f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF22C55E)) // green-500 equivalent
                        )
                        Text(
                            text = "SYSTEM OFFLINE READY",
                            color = GreyOff,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "V 1.0.4",
                        color = WhitePure.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Legal & Security Disclaimer Modal (Section 9)
            if (showDisclaimer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BlackPure.copy(alpha = 0.85f))
                        .pointerInput(Unit) {},
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .background(GlassCard, RoundedCornerShape(24.dp))
                            .border(1.5.dp, GlassBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(NeonBlue.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = "Security Alert",
                                    tint = NeonBlue,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "SECURITY & LEGAL DISCLAIMER",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = WhitePure,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "NOVA EXTRACTOR is intended for security research, app analysis, and recovering your own source code. Do not use it to infringe copyright. You are responsible for complying with local laws.\n\nOperating completely offline: No data ever leaves this device.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = GreyOff,
                                    lineHeight = 18.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { viewModel.dismissDisclaimer() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = WhitePure,
                                    contentColor = BlackPure
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("accept_disclaimer_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "I ACCEPT & AGREE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    }
                }
            }

            // Installed Apps Drawer Overlay
            AnimatedVisibility(
                visible = showAppsModal,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                InstalledAppsModal(
                    viewModel = viewModel,
                    onClose = { showAppsModal = false }
                )
            }

            // Decompiled source browser Overlay
            val activeBrowsingRecord by viewModel.activeBrowsingRecord.collectAsStateWithLifecycle()
            
            AnimatedVisibility(
                visible = activeBrowsingRecord != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                DecompiledSourceBrowserModal(
                    viewModel = viewModel,
                    onClose = { viewModel.closeDecompiledSourceViewer() }
                )
            }
        }
    }
}

/**
 * HISTORY CARD ROW
 */
@Composable
fun HistoryRowCard(
    record: HistoryEntity,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onBrowse: () -> Unit
) {
    val dateString = remember(record.timestamp) {
        try {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(record.timestamp))
        } catch (e: Exception) {
            "Unknown date"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassCard, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left platform tag launcher layout
            Box(
                modifier = Modifier
                    .size(40.dp) // w-10 -> 40dp
                    .clip(RoundedCornerShape(12.dp)) // rounded-xl -> 12dp
                    .background(WhitePure.copy(alpha = 0.10f)) // bg-white/10
                    .border(1.dp, WhitePure.copy(alpha = 0.10f), RoundedCornerShape(12.dp)), // border border-white/10
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = record.fileType, // "APK" or "IPA"
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (record.fileType == "APK") NeonBlue else WhitePure.copy(alpha = 0.8f),
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text fields
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = record.fileName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = WhitePure,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = record.fileSize,
                        style = MaterialTheme.typography.bodySmall.copy(color = GreyOff)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall.copy(color = GreyOff)
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.bodySmall.copy(color = GreyOff)
                    )
                }

                if (record.status == "SUCCESS") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${record.extractedClassesCount} Classes",
                            fontSize = 11.sp,
                            color = NeonBlue,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "|",
                            fontSize = 11.sp,
                            color = GreyOff
                        )
                        Text(
                            text = "${record.extractedScriptsCount} Scripts",
                            fontSize = 11.sp,
                            color = NeonBlue,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (record.errorMessage != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Error: " + record.errorMessage,
                        color = Color.Red,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick shares / deletes action buttons (ensuring Min target metrics >= 48dp)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (record.status == "SUCCESS") {
                    IconButton(
                        onClick = onBrowse,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("browse_history_item_" + record.id)
                    ) {
                        FolderIcon(
                            isExpanded = true,
                            tint = NeonBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onShare,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("share_history_item_" + record.id)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share Zip",
                            tint = WhitePure
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_history_item_" + record.id)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete item",
                        tint = GreyOff
                    )
                }
            }
        }
    }
}

/**
 * 3. PROCESSING SCREEN (Section 7.3)
 * Full-screen glass overlay, circular progress, incremental step status description.
 */
@Composable
fun ProcessingScreenContent(viewModel: MainViewModel) {
    val stepText by viewModel.progressStep.collectAsStateWithLifecycle()
    val progressPercent by viewModel.progressPercentage.collectAsStateWithLifecycle()

    // Pulsing animations logic
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_scale")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackPure.copy(alpha = 0.85f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(GlassBg, RoundedCornerShape(24.dp))
                .border(1.5.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Interactive pulsing tech logo/image
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(CircleShape)
                        .background(GlassCard)
                        .border(1.dp, GlassBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_nova_logo_1782155128469),
                        contentDescription = "Mascot logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Circular loading progress with neon styling
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxSize(),
                        color = NeonBlue,
                        strokeWidth = 6.dp,
                        trackColor = WhitePure.copy(alpha = 0.1f)
                    )
                    Text(
                        text = "$progressPercent%",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = WhitePure,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "DECOMPILING SOURCE ELEMENTS",
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = WhitePure,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Status text showing what extractor is doing
                AnimatedContent(
                    targetState = stepText,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    },
                    label = "text_animation"
                ) { targetStep ->
                    Text(
                        text = targetStep,
                        style = MaterialTheme.typography.bodyMedium.copy(color = GreyOff),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.height(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.cancelProcessing() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = WhitePure
                    ),
                    border = BorderStroke(1.dp, WhitePure.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(48.dp)
                        .testTag("cancel_extraction_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "ABORT EXTRACTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                }
            }
        }
    }
}

/**
 * 4. RESULT SCREEN (Section 7.3)
 */
@Composable
fun ResultScreenContent(viewModel: MainViewModel) {
    val context = LocalContext.current
    val result by viewModel.lastResult.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackPure.copy(alpha = 0.9f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        val extraResult = result ?: return Box {}

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(GlassBg, RoundedCornerShape(24.dp))
                .border(1.5.dp, GlassBorder, RoundedCornerShape(24.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Large status symbol
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            if (extraResult.success) Color(0x3300FF00) else Color(0x33FF0000)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (extraResult.success) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = "Status",
                        tint = if (extraResult.success) Color(0xFF00FF00) else Color.Red,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (extraResult.success) "EXTRACTION COMPLETED" else "EXTRACTION FAILED",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = WhitePure,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "NOVA EXTRACTOR BY NOVA MAX",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = NeonBlue,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Detail Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassCard, RoundedCornerShape(16.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResultRowItem("Source File", extraResult.appName)
                        ResultRowItem("Format Type", extraResult.fileType)
                        ResultRowItem("File Size", extraResult.sizeFormatted)

                        if (extraResult.success) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            ResultRowItem("Discovered Classes", "${extraResult.classesCount} Class models")
                            ResultRowItem("Extracted Scripts", "${extraResult.scriptsCount} Javascript/Plists")
                            ResultRowItem("Output", "NOVA_${extraResult.appName}_source.zip")
                        } else if (extraResult.error != null) {
                            HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 4.dp))
                            Text(
                                text = "Fatal Exception:\n${extraResult.error}",
                                color = Color.Red,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // If IPA package, display notice details about Mach-O constraints (Section 4.1)
                if (extraResult.success && extraResult.fileType == "IPA") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x33FF9800), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0x66FF9800), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Notice: Native iOS code cannot be retrieved; only readable scripts, configurations, and assets are extracted.",
                            color = Color(0xFFFFB74D),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Operation Action Buttons (Section 6)
                if (extraResult.success) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Download ZIP pointing button (Section 6: Download button)
                        Button(
                            onClick = {
                                Toast.makeText(
                                    context,
                                    "Saved inside Downloads: NOVA_${extraResult.appName}_source.zip",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WhitePure,
                                contentColor = BlackPure
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("download_zip_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "Download",
                                    modifier = Modifier.graphicsLayer { rotationZ = 90f }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("DOWNLOAD", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        // Share ZIP sheet button (Section 6: Share button)
                        Button(
                            onClick = { viewModel.shareOutputZip(context, extraResult.zipFilePath) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                contentColor = WhitePure
                            ),
                            border = BorderStroke(1.dp, WhitePure.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_zip_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SHARE", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Return back dashboard button
                TextButton(
                    onClick = { viewModel.navigateToMain() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("back_to_dashboard_button")
                ) {
                    Text(
                        text = "RETURN TO DASHBOARD",
                        color = NeonBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ResultRowItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = GreyOff,
            fontSize = 12.sp,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = value,
            color = WhitePure,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

// ------------------------------------------------------------------------
// INSTALLED APPS CORE SELECTOR MODAL & UTILITIES
// ------------------------------------------------------------------------

fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
    if (drawable is android.graphics.drawable.BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }
    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } else {
        Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    }
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstalledAppsModal(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val loading by viewModel.loadingInstalledApps.collectAsStateWithLifecycle()
    
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFC020205), // Ultra-sleek premium dark OLED overlay backdrop
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Top header row
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = WhitePure
                        )
                    }
                    Text(
                        text = "SELECT INSTALLED APP",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = WhitePure,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    )
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = GreyOff
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Beautiful Search Text Field
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search app name or package...", color = GreyOff) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = WhitePure.copy(alpha = 0.08f),
                        unfocusedContainerColor = WhitePure.copy(alpha = 0.04f),
                        focusedIndicatorColor = NeonBlue,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = WhitePure,
                        unfocusedTextColor = WhitePure
                    ),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "", tint = GreyOff)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search", tint = GreyOff)
                            }
                        }
                    },
                    singleLine = true
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (loading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = NeonBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading installed applications...", color = GreyOff)
                }
            } else if (filteredApps.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(imageVector = Icons.Default.List, contentDescription = "", tint = GreyOff, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No matching apps found", color = GreyOff)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredApps) { app ->
                        AppItemRow(
                            app = app,
                            onClick = {
                                onClose()
                                viewModel.triggerExtractionRequest(
                                    uri = Uri.fromFile(java.io.File(app.sourceDir)),
                                    customFileName = "${app.name.replace(" ", "_")}_extracted.apk"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemRow(
    app: InstalledApp,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Convert Drawable to Bitmap safely
    val bitmap = remember(app.packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(app.packageName)
            drawableToBitmap(drawable).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(WhitePure.copy(alpha = 0.05f))
            .border(1.dp, WhitePure.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App icon with beautiful rounding/shadow container
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(WhitePure.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = app.name,
                    tint = NeonBlue,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Label and package info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = app.name,
                    color = WhitePure,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSystem) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(WhitePure.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "SYSTEM",
                            color = GreyOff,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = app.packageName,
                color = GreyOff,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Size & Arrow trigger action
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = app.sizeFormatted,
                color = NeonBlue,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "v${app.version}",
                color = GreyOff,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
fun ExtractionConfirmationDialog(
    request: com.example.ui.ExtractionRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Color(0xFF0C101B), // Dark sleek sub-blue background
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Confirmation Warning",
                tint = NeonBlue,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "DECOMPILE SOURCE CODE?",
                color = WhitePure,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "You are about to run Nova Extractor to extract, decompile, and reconstruct the full source trees from this package.",
                    color = GreyOff,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF131A2D))
                        .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "TARGET FILE:",
                            color = NeonBlue,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = request.resolvedName,
                            color = WhitePure,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Text(
                    text = "Would you like to proceed with extraction?",
                    color = WhitePure.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_extraction_button")
            ) {
                Text(
                    text = "CONTINUE",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_extraction_button")
            ) {
                Text(
                    text = "CANCEL",
                    color = GreyOff,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    )
}

/**
 * 10. INTERACTIVE EXPLORER FILE TREE AND CODE DECOMPILATION VIEW MODAL
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecompiledSourceBrowserModal(
    viewModel: MainViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rootNode by viewModel.activeTreeRoot.collectAsStateWithLifecycle()
    val currentFileContent by viewModel.currentOpenedFileContent.collectAsStateWithLifecycle()
    val currentFileName by viewModel.currentOpenedFileName.collectAsStateWithLifecycle()
    val currentFilePath by viewModel.currentOpenedFilePath.collectAsStateWithLifecycle()
    val searchQuery by viewModel.treeSearchQuery.collectAsStateWithLifecycle()
    val activeRecord by viewModel.activeBrowsingRecord.collectAsStateWithLifecycle()

    val expandedPaths = remember { mutableStateMapOf<String, Boolean>() }

    // Flat representation computed dynamically based on expansion map and search query
    val flattenedNodes = remember(rootNode, expandedPaths, searchQuery) {
        val list = mutableListOf<Pair<TreeNode, Int>>()
        rootNode?.let { root ->
            flattenFilteredTree(root, 0, searchQuery, expandedPaths, list)
        }
        list
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D1A)) // Deep space slate theme
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header panel (Premium gloss neon bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .drawBehind {
                        drawLine(
                            color = NeonBlue.copy(alpha = 0.2f),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    FolderIcon(
                        isExpanded = true,
                        tint = NeonBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = activeRecord?.fileName ?: "DECOMPILED SOURCE TREE",
                            color = WhitePure,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${flattenedNodes.size} visible source parts",
                            color = GreyOff,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .background(WhitePure.copy(alpha = 0.05f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Project Browser",
                        tint = WhitePure
                    )
                }
            }

            if (rootNode == null) {
                // Loading view spinner while indexing files
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = NeonBlue, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "INDEXING DECOMPILED FILE TREE STRUCTURES...",
                            color = GreyOff,
                            fontSize = 11.sp,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    val isWideScreen = maxWidth > 680.dp

                    if (isWideScreen) {
                        // Split view (Explorer on Left, Code Viewer on Right)
                        Row(modifier = Modifier.fillMaxSize()) {
                            // File Explorer pane
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF0F172A).copy(alpha = 0.4f))
                                    .drawBehind {
                                        drawLine(
                                            color = GlassBorder,
                                            start = Offset(size.width, 0f),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                            ) {
                                ExplorerPanel(
                                    searchQuery = searchQuery,
                                    onUpdateQuery = { viewModel.updateTreeSearchQuery(it) },
                                    flattenedNodes = flattenedNodes,
                                    expandedPaths = expandedPaths,
                                    onNodeClick = { node ->
                                        if (node.isDirectory) {
                                            expandedPaths[node.path] = !(expandedPaths[node.path] ?: false)
                                        } else {
                                            viewModel.loadFileContentFromActiveZip(node.path)
                                        }
                                    },
                                    activeFilePath = currentFilePath ?: ""
                                )
                            }

                            // Code visualizer pane
                            Box(
                                modifier = Modifier
                                    .weight(2f)
                                    .fillMaxHeight()
                            ) {
                                if (currentFileContent == null) {
                                    EmptyCodePlaceholder()
                                } else {
                                    CodeViewerPanel(
                                        fileName = currentFileName ?: "",
                                        filePath = currentFilePath ?: "",
                                        fileContent = currentFileContent ?: "",
                                        onCloseFile = { viewModel.closeDecompiledSourceViewer() },
                                        context = context,
                                        isSplit = true
                                    )
                                }
                            }
                        }
                    } else {
                        // Mobile View (Toggle between file list or code content)
                        if (currentFileContent == null) {
                            ExplorerPanel(
                                searchQuery = searchQuery,
                                onUpdateQuery = { viewModel.updateTreeSearchQuery(it) },
                                flattenedNodes = flattenedNodes,
                                expandedPaths = expandedPaths,
                                onNodeClick = { node ->
                                    if (node.isDirectory) {
                                        expandedPaths[node.path] = !(expandedPaths[node.path] ?: false)
                                    } else {
                                        viewModel.loadFileContentFromActiveZip(node.path)
                                    }
                                },
                                activeFilePath = ""
                            )
                        } else {
                            CodeViewerPanel(
                                fileName = currentFileName ?: "",
                                filePath = currentFilePath ?: "",
                                fileContent = currentFileContent ?: "",
                                onCloseFile = { viewModel.closeDecompiledSourceViewer() },
                                context = context,
                                isSplit = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Explorer Panel List & Search view
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerPanel(
    searchQuery: String,
    onUpdateQuery: (String) -> Unit,
    flattenedNodes: List<Pair<TreeNode, Int>>,
    expandedPaths: Map<String, Boolean>,
    onNodeClick: (TreeNode) -> Unit,
    activeFilePath: String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search filter input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onUpdateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                placeholder = {
                    Text(
                        text = "Search decompiled sources...",
                        color = GreyOff.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = WhitePure, fontSize = 13.sp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = GreyOff,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onUpdateQuery("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = GreyOff,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue.copy(alpha = 0.6f),
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.5f)
                )
            )
        }

        if (flattenedNodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) "No matching files or packages found" else "Empty project structure",
                    color = GreyOff,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(flattenedNodes) { (node, depth) ->
                    val isExpanded = expandedPaths[node.path] ?: false
                    val isActiveFile = activeFilePath == node.path
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isActiveFile -> NeonBlue.copy(alpha = 0.15f)
                                    node.isDirectory -> Color.Transparent
                                    else -> Color.Transparent
                                }
                            )
                            .clickable { onNodeClick(node) }
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom spacing based on indentation tree depth
                        Spacer(modifier = Modifier.width((depth * 14).dp))

                        // Tree element icon
                        val iconColor = when {
                            node.isDirectory -> if (isExpanded) NeonBlue else GreyOff
                            node.name == "AndroidManifest.xml" -> Color(0xFFFFB020) // warm amber
                            node.name.endsWith(".xml") -> Color(0xFFFFD54F) // resource yellow
                            node.name.endsWith(".smali") -> Color(0xFF29B6F6) // assembly blue
                            node.name.endsWith(".java") || node.name.endsWith(".kt") -> Color(0xFF4CAF50) // code green
                            else -> GreyOff
                        }

                        if (node.isDirectory) {
                            FolderIcon(
                                isExpanded = isExpanded,
                                tint = if (isActiveFile) NeonBlue else iconColor,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            FileIcon(
                                tint = if (isActiveFile) NeonBlue else iconColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = node.name,
                            color = if (isActiveFile) NeonBlue else if (node.isDirectory) WhitePure else WhitePure.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = if (node.isDirectory) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Code viewer panel with vertical and horizontal scroll options & line indices
 */
@Composable
fun CodeViewerPanel(
    fileName: String,
    filePath: String,
    fileContent: String,
    onCloseFile: () -> Unit,
    context: Context,
    isSplit: Boolean
) {
    val lines = remember(fileContent) { fileContent.split("\n") }
    val clipboardManager = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager }

    Column(modifier = Modifier.fillMaxSize()) {
        // File view sub-header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .drawBehind {
                    drawLine(
                        color = GlassBorder,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (!isSplit) {
                    IconButton(
                        onClick = onCloseFile,
                        modifier = Modifier
                            .size(36.dp)
                            .background(WhitePure.copy(alpha = 0.05f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Return to File Tree explorer",
                            tint = WhitePure
                        )
                    }
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileName,
                        color = WhitePure,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = filePath,
                        color = GreyOff,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Code Operations
            IconButton(
                onClick = {
                    try {
                        val clip = android.content.ClipData.newPlainText("Source Code", fileContent)
                        clipboardManager.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "Code copied successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Copy failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .background(WhitePure.copy(alpha = 0.05f), CircleShape)
            ) {
                CopyIcon(
                    tint = NeonBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Monospace Scrollable code text pane
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .background(Color(0xFF070A11)) // Dark IDE back canvas
        ) {
            // Horizontal + Vertical Scrollbars
            val stateVertical = rememberScrollState()
            val stateHorizontal = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(stateVertical)
            ) {
                // Line counts gutter
                Column(
                    modifier = Modifier
                        .background(Color(0xFF0A0F1D))
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                        .widthIn(min = 36.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    for (i in 1..lines.size) {
                        Text(
                            text = i.toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = GreyOff.copy(alpha = 0.4f),
                            lineHeight = 16.sp
                        )
                    }
                }

                // Divider line
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(GlassBorder)
                )

                // Editable layout contents
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(stateHorizontal)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = fileContent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = WhitePure.copy(alpha = 0.9f),
                            lineHeight = 16.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Empty Code Placeholder View
 */
@Composable
fun EmptyCodePlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            FileIcon(
                tint = GreyOff.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "NO ACTIVE FILE SELECTED",
                color = WhitePure,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select any smali, java, or xml resources from the file explorer pane to browse extracted and AI-reconstructed methods.",
                color = GreyOff,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
    }
}

/**
 * Recursive check helper function specifically to auto-expand directories on query match and render children
 */
fun flattenFilteredTree(
    node: TreeNode,
    depth: Int,
    query: String,
    expandedPaths: Map<String, Boolean>,
    result: MutableList<Pair<TreeNode, Int>>
) {
    for (child in node.children) {
        val matchesQuery = query.isEmpty() || child.name.contains(query, ignoreCase = true) || hasMatchingChild(child, query)
        if (matchesQuery) {
            result.add(Pair(child, depth))
            val isExpanded = if (query.isNotEmpty()) {
                hasMatchingChild(child, query)
            } else {
                expandedPaths[child.path] ?: false
            }
            if (child.isDirectory && isExpanded) {
                flattenFilteredTree(child, depth + 1, query, expandedPaths, result)
            }
        }
    }
}

fun hasMatchingChild(node: TreeNode, query: String): Boolean {
    for (child in node.children) {
        if (child.name.contains(query, ignoreCase = true) || hasMatchingChild(child, query)) {
            return true
        }
    }
    return false
}

/**
 * CUSTOM CANVAS DIRECT VECTOR GRAPHICS DRAWABLES
 */
@Composable
fun FolderIcon(isExpanded: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            if (isExpanded) {
                // Open folder state
                moveTo(w * 0.05f, h * 0.25f)
                lineTo(w * 0.35f, h * 0.25f)
                lineTo(w * 0.45f, h * 0.4f)
                lineTo(w * 0.95f, h * 0.4f)
                lineTo(w * 0.95f, h * 0.85f)
                lineTo(w * 0.05f, h * 0.85f)
                close()
            } else {
                // Closed folder state
                moveTo(w * 0.05f, h * 0.25f)
                lineTo(w * 0.4f, h * 0.25f)
                lineTo(w * 0.5f, h * 0.4f)
                lineTo(w * 0.95f, h * 0.4f)
                lineTo(w * 0.95f, h * 0.8f)
                lineTo(w * 0.05f, h * 0.8f)
                close()
            }
        }
        drawPath(path = path, color = tint)
    }
}

@Composable
fun FileIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.15f, h * 0.1f)
            lineTo(w * 0.65f, h * 0.1f)
            lineTo(w * 0.85f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.9f)
            lineTo(w * 0.15f, h * 0.9f)
            close()
        }
        drawPath(path = path, color = tint)
        
        // Corner fold paint
        val foldPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.65f, h * 0.1f)
            lineTo(w * 0.65f, h * 0.3f)
            lineTo(w * 0.85f, h * 0.3f)
            close()
        }
        drawPath(path = foldPath, color = Color.Black.copy(alpha = 0.35f))
    }
}

@Composable
fun CopyIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Back overlapping box sheet
        drawRect(
            color = tint.copy(alpha = 0.4f),
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
        // Front box sheet
        drawRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.35f),
            size = androidx.compose.ui.geometry.Size(w * 0.55f, h * 0.55f)
        )
    }
}




