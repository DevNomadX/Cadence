package com.example.ui.screens

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.ui.icons.CadenceIcons
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ScallopedCircleShape
import com.example.ui.theme.opaqueOver

private const val CADENCE_GITHUB_URL = "https://github.com/devnomadx/cadence"

private val AboutEmblemScallopShape = ScallopedCircleShape(
    lobes = 12,
    amplitudeFraction = 0.055f
)

private data class AboutCreditEntry(
    val library: String,
    val license: String
)

private val AboutThirdPartyCredits = listOf(
    AboutCreditEntry("Jetpack Compose & Material 3", "Apache License 2.0"),
    AboutCreditEntry("AndroidX Media3 (ExoPlayer)", "Apache License 2.0"),
    AboutCreditEntry("AndroidX Room & DataStore", "Apache License 2.0"),
    AboutCreditEntry("Coil Image Loading", "Apache License 2.0"),
    AboutCreditEntry("AndroidX Core, Activity & Lifecycle", "Apache License 2.0"),
    AboutCreditEntry("Kotlin Coroutines", "Apache License 2.0")
)

internal fun resolveAppVersionName(context: Context): String {
    val buildVersion = BuildConfig.VERSION_NAME.trim()
    if (buildVersion.isNotEmpty()) return buildVersion
    return runCatching {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName?.trim().orEmpty()
    }.getOrDefault("").ifEmpty { "1.0" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSubScreen(
    versionName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    var showCreditsDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val colorScheme = MaterialTheme.colorScheme
    val bg = colorScheme.background
    val topBarRestColor = colorScheme.surface.opaqueOver(bg, 1.0f)
    val topBarScrolledColor = colorScheme.surfaceContainer.opaqueOver(bg, 1.0f)
    val hairlineColor = colorScheme.outlineVariant.opaqueOver(bg, 0.30f)
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("about_subscreen"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_section_about),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .testTag("about_subscreen_title")
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .testTag("about_back_button"),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colorScheme.surfaceContainerHigh,
                            contentColor = colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                windowInsets = WindowInsets.statusBars,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarRestColor,
                    scrolledContainerColor = topBarScrolledColor,
                    navigationIconContentColor = colorScheme.onSurface,
                    titleContentColor = colorScheme.onSurface
                ),
                scrollBehavior = scrollBehavior,
                modifier = Modifier.testTag("about_top_bar")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .testTag("about_subscreen_list"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 20.dp,
                    bottom = navBarBottom + 24.dp
                )
            ) {
                // Clean, airy centered App Identity Header
                item(key = "about_identity_header", contentType = "identity_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(AboutEmblemScallopShape)
                                .background(colorScheme.primaryContainer)
                                .testTag("about_emblem"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CadenceIcons.MusicNote,
                                contentDescription = stringResource(R.string.app_name),
                                tint = colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("about_app_name")
                        )

                        Surface(
                            shape = CircleShape,
                            color = colorScheme.secondaryContainer,
                            modifier = Modifier.testTag("about_version_pill")
                        ) {
                            Text(
                                text = "v$versionName",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = MonoFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp,
                                letterSpacing = (-0.1).sp
                            ),
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("about_tagline")
                        )
                    }
                }

                // Clean borderless iconized info rows separated by 0.5dp hairlines
                item(key = "about_rows", contentType = "info_rows") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        AboutHairlineDivider(hairlineColor)

                        AboutInfoRow(
                            icon = CadenceIcons.VerifiedUser,
                            overline = stringResource(R.string.about_privacy_overline),
                            title = stringResource(R.string.settings_about_privacy_title),
                            subtitle = stringResource(R.string.settings_about_privacy_desc),
                            onClick = null,
                            testTag = "about_privacy_row"
                        )

                        AboutHairlineDivider(hairlineColor)

                        AboutInfoRow(
                            icon = CadenceIcons.GraphicEq,
                            overline = stringResource(R.string.about_engine_overline),
                            title = stringResource(R.string.about_engine_title),
                            subtitle = stringResource(R.string.about_engine_desc),
                            onClick = null,
                            testTag = "about_engine_row"
                        )

                        AboutHairlineDivider(hairlineColor)

                        AboutInfoRow(
                            icon = CadenceIcons.Code,
                            overline = stringResource(R.string.about_source_overline),
                            title = stringResource(R.string.settings_about_source_title),
                            subtitle = stringResource(R.string.settings_about_source_desc),
                            onClick = {
                                runCatching { uriHandler.openUri(CADENCE_GITHUB_URL) }
                            },
                            testTag = "about_source_code_row"
                        )

                        AboutHairlineDivider(hairlineColor)

                        AboutInfoRow(
                            icon = CadenceIcons.Description,
                            overline = stringResource(R.string.about_licenses_overline),
                            title = stringResource(R.string.settings_about_licenses_title),
                            subtitle = stringResource(R.string.settings_about_licenses_desc),
                            onClick = { showCreditsDialog = true },
                            testTag = "about_licenses_row"
                        )

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showCreditsDialog) {
        AboutThirdPartyCreditsDialog(
            onDismiss = { showCreditsDialog = false }
        )
    }
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    overline: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val rowModifier = Modifier
        .fillMaxWidth()
        .then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
        .padding(horizontal = 4.dp, vertical = 14.dp)
        .testTag(testTag)

    Row(
        modifier = rowModifier,
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
                text = overline,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
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

        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun AboutHairlineDivider(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(color)
    )
}

@Composable
private fun AboutThirdPartyCreditsDialog(
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.testTag("about_licenses_dialog"),
        title = {
            Text(
                text = stringResource(R.string.settings_about_licenses_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutThirdPartyCredits.forEach { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = entry.library,
                            style = MaterialTheme.typography.titleSmall,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = entry.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("about_licenses_close")
            ) {
                Text(text = stringResource(R.string.action_close))
            }
        }
    )
}
