package app.aaps.ui.compose.stats

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.glass.GlassColors
import app.aaps.core.ui.compose.glass.GlassScreenBackground
import app.aaps.core.ui.compose.glass.GlassSectionCard
import app.aaps.core.ui.compose.glass.isGlassDarkMode
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.stats.viewmodels.StatsViewModel

/**
 * Composable screen displaying statistics including TDD, TIR, Dexcom TIR, and Activity Monitor.
 * Uses the Glass visual language (gradient background, glass section cards).
 *
 * @param viewModel ViewModel containing all statistics state and business logic
 * @param onNavigateBack Callback when back navigation is requested
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.loadAllStats()
        }
    }

    val isDark = isGlassDarkMode()

    GlassScreenBackground(isDark = isDark) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(CoreUiStrings.back),
                        tint = GlassColors.textBright(isDark)
                    )
                }
                Text(
                    text = stringResource(CoreUiStrings.statistics),
                    color = GlassColors.textBright(isDark),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // TDD Section
            GlassSectionCard(
                title = stringResource(CoreUiStrings.tdd),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth(),
                expanded = state.tddExpanded,
                onToggleExpanded = { viewModel.toggleTddExpanded() },
                trailingAction = if (state.tddExpanded && !state.tddLoading) {
                    {
                        FilledTonalButton(onClick = { viewModel.showRecalculateDialog() }) {
                            Text(text = stringResource(UiStrings.recalculate))
                        }
                    }
                } else null
            ) {
                Crossfade(
                    targetState = state.tddLoading,
                    label = stringResource(CoreUiStrings.loading)
                ) { isLoading ->
                    if (isLoading) {
                        LoadingSection(
                            title = stringResource(CoreUiStrings.tdd),
                            message = stringResource(UiStrings.calculation_in_progress)
                        )
                    } else {
                        state.tddStatsData?.let { data ->
                            TddStatsCompose(
                                tddStatsData = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // TIR Section
            GlassSectionCard(
                title = stringResource(CoreUiStrings.tir),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth(),
                expanded = state.tirExpanded,
                onToggleExpanded = { viewModel.toggleTirExpanded() }
            ) {
                Crossfade(
                    targetState = state.tirLoading,
                    label = stringResource(CoreUiStrings.loading)
                ) { isLoading ->
                    if (isLoading) {
                        LoadingSection(
                            title = stringResource(CoreUiStrings.tir),
                            message = stringResource(UiStrings.calculation_in_progress)
                        )
                    } else {
                        state.tirStatsData?.let { data ->
                            TirStatsCompose(
                                tirStatsData = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Dexcom TIR Section
            GlassSectionCard(
                title = stringResource(UiStrings.dexcom_tir),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth(),
                expanded = state.dexcomTirExpanded,
                onToggleExpanded = { viewModel.toggleDexcomTirExpanded() }
            ) {
                Crossfade(
                    targetState = state.dexcomTirLoading,
                    label = stringResource(CoreUiStrings.loading)
                ) { isLoading ->
                    if (isLoading) {
                        LoadingSection(
                            title = stringResource(UiStrings.dexcom_tir),
                            message = stringResource(UiStrings.calculation_in_progress)
                        )
                    } else {
                        state.dexcomTirData?.let { data ->
                            DexcomTirStatsCompose(
                                dexcomTir = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Activity Section - Android only, see StatsViewModel.showActivityStats
            if (viewModel.showActivityStats) GlassSectionCard(
                title = stringResource(UiStrings.activity_monitor),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth(),
                expanded = state.activityExpanded,
                onToggleExpanded = { viewModel.toggleActivityExpanded() },
                trailingAction = if (state.activityExpanded && !state.activityLoading) {
                    {
                        FilledTonalButton(onClick = { viewModel.showResetActivityDialog() }) {
                            Text(text = stringResource(CoreUiStrings.reset))
                        }
                    }
                } else null
            ) {
                Crossfade(
                    targetState = state.activityLoading,
                    label = stringResource(CoreUiStrings.loading)
                ) { isLoading ->
                    if (isLoading) {
                        LoadingSection(
                            title = stringResource(UiStrings.activity_monitor),
                            message = stringResource(UiStrings.calculation_in_progress)
                        )
                    } else {
                        state.activityStatsData?.let { data ->
                            ActivityStatsCompose(
                                activityStats = data,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // TDD Cycle Pattern Section
            GlassSectionCard(
                title = stringResource(CoreUiStrings.tdd_cycle_pattern),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth(),
                expanded = state.tddCycleExpanded,
                onToggleExpanded = { viewModel.toggleTddCycleExpanded() }
            ) {
                Column {
                    // Progress indicator (visible while still loading)
                    if (state.tddCycleLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.tddCycleProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = GlassColors.skyBlue,
                                trackColor = GlassColors.borderCard(isDark)
                            )
                            Text(
                                text = stringResource(
                                    UiStrings.calculation_in_progress_percent,
                                    (state.tddCycleProgress * 100).toInt()
                                ),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    // Graph (shown as soon as data is available, even while loading continues)
                    if (state.tddCyclePatternData != null) {
                        TddCyclePatternCompose(
                            data = state.tddCyclePatternData!!,
                            offset = state.tddCycleOffset,
                            onOffsetChange = { viewModel.updateCycleOffset(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (!state.tddCycleLoading) {
                        Text(
                            text = stringResource(UiStrings.not_enough_data_for_cycles),
                            style = MaterialTheme.typography.bodyMedium,
                            color = GlassColors.textMuted(isDark)
                        )
                    }
                }
            }

            // CGP Section (Comprehensive Glucose Pentagon) - always expanded
            GlassSectionCard(
                title = stringResource(UiStrings.cgp_title),
                isDark = isDark,
                modifier = Modifier.fillMaxWidth(),
                onToggleExpanded = null
            ) {
                Crossfade(targetState = state.dexcomTirLoading, label = "cgp_loading") { isLoading ->
                    if (isLoading) {
                        LoadingSection(title = stringResource(UiStrings.cgp_title), message = stringResource(UiStrings.calculation_in_progress))
                    } else {
                        state.dexcomTirData?.let { data ->
                            GlucosePentagonCompose(dexcomTir = data, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }

    if (state.showRecalculateDialog) {
        OkCancelDialog(
            message = stringResource(UiStrings.do_you_want_recalculate_tdd_stats),
            onConfirm = { viewModel.confirmRecalculateTdd() },
            onDismiss = { viewModel.dismissRecalculateDialog() }
        )
    }
}
