package com.example.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.SavedFolderItem
import com.example.data.StableList
import com.example.data.ThemePreferences
import com.example.ui.components.ambientAuraBackdrop
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.AppearanceMode
import com.example.ui.theme.ExpressivePalette
import com.example.ui.theme.LocalPerformanceMode
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.opaqueOver

private const val ONBOARDING_STEP_COUNT = 3

private val OnboardingEmblemScallopShape = ScallopedCircleShape(
    lobes = 12,
    amplitudeFraction = 0.055f
)

/**
 * 3-step Material 3 Expressive first-time setup flow for Cadence:
 * - Step 1: Welcome & Offline Promise (12-lobe scalloped emblem + 3 borderless feature rows)
 * - Step 2: Make It Yours (Auto/Light/Dark + 10 live color-wheel palettes, zero Display Name)
 * - Step 3: Permissions & Music Folders (Audio permission, optional lock-screen notifications, folder mode)
 */
@Composable
fun OnboardingScreen(
    themePrefs: ThemePreferences = ThemePreferences(),
    savedFolders: StableList<SavedFolderItem> = StableList(),
    isAudioPermissionGranted: Boolean = false,
    isNotificationPermissionGranted: Boolean = false,
    showNotificationPermissionRow: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
    onSelectPalette: (ExpressivePalette) -> Unit = {},
    onSelectAppearance: (AppearanceMode) -> Unit = {},
    onRequestAudioPermission: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onAddFolder: () -> Unit = {},
    onRemoveFolder: (String) -> Unit = {},
    onStartListening: (scanAllFolders: Boolean) -> Unit,
    drawAmbientBackdrop: Boolean = true,
    modifier: Modifier = Modifier
) {
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var scanAllFolders by rememberSaveable { mutableStateOf(true) }
    val performanceMode = LocalPerformanceMode.current

    BackHandler(enabled = currentStep > 0) {
        currentStep = (currentStep - 1).coerceAtLeast(0)
    }

    val backdropModifier = if (drawAmbientBackdrop) {
        Modifier.ambientAuraBackdrop(
            enabled = themePrefs.effectiveAmbientAura,
            isPlaying = false
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backdropModifier)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
        ) {
            OnboardingProgressDots(
                currentStep = currentStep,
                totalSteps = ONBOARDING_STEP_COUNT,
                performanceMode = performanceMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 6.dp)
            )

            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    if (performanceMode) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val forward = targetState >= initialState
                        val enterOffset: (Int) -> Int = { width -> if (forward) width / 5 else -width / 5 }
                        val exitOffset: (Int) -> Int = { width -> if (forward) -width / 5 else width / 5 }
                        (slideInHorizontally(tween(240), initialOffsetX = enterOffset) + fadeIn(tween(220)))
                            .togetherWith(
                                slideOutHorizontally(tween(200), targetOffsetX = exitOffset) + fadeOut(tween(160))
                            )
                    }
                },
                label = "onboarding_step_content",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { step ->
                when (step) {
                    0 -> OnboardingWelcomeStep()
                    1 -> OnboardingAppearanceStep(
                        themePrefs = themePrefs,
                        onSelectPalette = onSelectPalette,
                        onSelectAppearance = onSelectAppearance
                    )
                    else -> OnboardingLibrarySetupStep(
                        isAudioPermissionGranted = isAudioPermissionGranted,
                        isNotificationPermissionGranted = isNotificationPermissionGranted,
                        showNotificationPermissionRow = showNotificationPermissionRow,
                        scanAllFolders = scanAllFolders,
                        savedFolders = savedFolders,
                        onRequestAudioPermission = onRequestAudioPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onSelectScanAllFolders = { scanAllFolders = it },
                        onAddFolder = onAddFolder,
                        onRemoveFolder = onRemoveFolder
                    )
                }
            }

            OnboardingBottomControls(
                currentStep = currentStep,
                onGetStarted = { currentStep = 1 },
                onBack = { currentStep = (currentStep - 1).coerceAtLeast(0) },
                onContinue = { currentStep = 2 },
                onStartListening = { onStartListening(scanAllFolders) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun OnboardingProgressDots(
    currentStep: Int,
    totalSteps: Int,
    performanceMode: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val completedDotColor = colorScheme.primary.opaqueOver(bg, 0.45f)
    val inactiveDotColor = colorScheme.outlineVariant.opaqueOver(bg, 0.70f)
    val stepDescription = stringResource(R.string.onboarding_step_indicator_cd, currentStep + 1, totalSteps)

    Row(
        modifier = modifier
            .semantics { contentDescription = stepDescription }
            .testTag("onboarding_progress_dots"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep
            val dotWidth = if (performanceMode) {
                if (isActive) 28.dp else 8.dp
            } else {
                animateDpAsState(
                    targetValue = if (isActive) 28.dp else 8.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "onboarding_dot_width_$index"
                ).value
            }
            val dotColor = when {
                isActive -> colorScheme.primary
                isCompleted -> completedDotColor
                else -> inactiveDotColor
            }

            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(dotColor)
                    .testTag("onboarding_dot_$index")
            )
        }
    }
}

@Composable
private fun OnboardingWelcomeStep(
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val hairlineColor = colorScheme.outlineVariant.opaqueOver(bg, 0.30f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("onboarding_step_1"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(OnboardingEmblemScallopShape)
                .background(colorScheme.primaryContainer)
                .testTag("onboarding_emblem"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = CadenceIcons.MusicNote,
                contentDescription = stringResource(R.string.app_name),
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(Modifier.height(22.dp))

        Text(
            text = stringResource(R.string.onboarding_step1_headline),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_title")
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_step1_subtitle),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                letterSpacing = (-0.15).sp
            ),
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_subtitle")
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            OnboardingHairline(hairlineColor)

            OnboardingFeatureHighlightRow(
                icon = CadenceIcons.VerifiedUser,
                title = stringResource(R.string.onboarding_feature_privacy_title),
                subtitle = stringResource(R.string.onboarding_feature_privacy_desc),
                testTag = "onboarding_feature_privacy"
            )

            OnboardingHairline(hairlineColor)

            OnboardingFeatureHighlightRow(
                icon = CadenceIcons.GraphicEq,
                title = stringResource(R.string.onboarding_feature_hifi_title),
                subtitle = stringResource(R.string.onboarding_feature_hifi_desc),
                testTag = "onboarding_feature_hifi"
            )

            OnboardingHairline(hairlineColor)

            OnboardingFeatureHighlightRow(
                icon = CadenceIcons.AutoAwesome,
                title = stringResource(R.string.onboarding_feature_mixes_title),
                subtitle = stringResource(R.string.onboarding_feature_mixes_desc),
                testTag = "onboarding_feature_mixes"
            )

            OnboardingHairline(hairlineColor)
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun OnboardingFeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = colorScheme.secondaryContainer,
            contentColor = colorScheme.onSecondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OnboardingHairline(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color)
    )
}

@Composable
private fun OnboardingBottomControls(
    currentStep: Int,
    onGetStarted: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onStartListening: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (currentStep) {
        0 -> {
            Button(
                onClick = onGetStarted,
                shape = CircleShape,
                modifier = modifier
                    .height(54.dp)
                    .testTag("onboarding_get_started_button")
            ) {
                Text(
                    text = stringResource(R.string.onboarding_cta_get_started),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        1 -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onBack,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(0.38f)
                        .height(52.dp)
                        .testTag("onboarding_back_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_back),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = onContinue,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(0.62f)
                        .height(52.dp)
                        .testTag("onboarding_continue_button")
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_cta_continue),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        else -> {
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onBack,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(0.36f)
                        .height(52.dp)
                        .testTag("onboarding_back_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_back),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Button(
                    onClick = onStartListening,
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(0.64f)
                        .height(52.dp)
                        .testTag("onboarding_start_listening_button")
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_cta_start_listening),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
