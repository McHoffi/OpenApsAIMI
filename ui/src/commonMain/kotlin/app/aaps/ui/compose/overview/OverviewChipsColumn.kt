package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.overview.chips.RunningModeChip
import app.aaps.ui.compose.overview.chips.SensitivityUiState
import app.aaps.ui.compose.overview.chips.TempTargetChip

@Composable
fun OverviewChipsColumn(
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    smbEnabled: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    sensitivityUiState: SensitivityUiState,
    onNavigate: (NavigationRequest) -> Unit,
    // The command chips (running mode / profile / temp target) open mutating screens — their click is disabled on an
    // unpaired client (same MASTER_OR_PAIRED_CLIENT gate as nav/Manage), while the chip stays visible as status.
    commandsAllowed: Boolean = true,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (trailingContent != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val chipsWidth = (maxWidth * 0.4f).coerceIn(100.dp, 160.dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.width(chipsWidth),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NarrowChips(
                            runningMode = runningMode,
                            runningModeText = runningModeText,
                            runningModeRemaining = runningModeRemaining,
                            runningModeProgress = runningModeProgress,
                            runningModeSceneManaged = runningModeSceneManaged,
                            smbEnabled = smbEnabled,
                            tempTargetText = tempTargetText,
                            tempTargetState = tempTargetState,
                            tempTargetProgress = tempTargetProgress,
                            tempTargetReason = tempTargetReason,
                            tempTargetSceneManaged = tempTargetSceneManaged,
                            onNavigate = onNavigate,
                            modifier = Modifier.width(chipsWidth),
                            commandsAllowed = commandsAllowed
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        content = trailingContent
                    )
                }
            }
        } else {
            NarrowChips(
                runningMode = runningMode,
                runningModeText = runningModeText,
                runningModeRemaining = runningModeRemaining,
                runningModeProgress = runningModeProgress,
                runningModeSceneManaged = runningModeSceneManaged,
                smbEnabled = smbEnabled,
                tempTargetText = tempTargetText,
                tempTargetState = tempTargetState,
                tempTargetProgress = tempTargetProgress,
                tempTargetReason = tempTargetReason,
                tempTargetSceneManaged = tempTargetSceneManaged,
                onNavigate = onNavigate,
                modifier = Modifier,
                commandsAllowed = commandsAllowed
            )
        }
        // SensitivityChipBlock(
        //     state = sensitivityUiState,
        //     modifier = Modifier.fillMaxWidth()
        // )
    }
}

@Composable
private fun NarrowChips(
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean,
    smbEnabled: Boolean,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean,
    onNavigate: (NavigationRequest) -> Unit,
    modifier: Modifier = Modifier,
    commandsAllowed: Boolean
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (runningModeText.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RunningModeChip(
                    mode = runningMode,
                    text = runningModeText,
                    progress = runningModeProgress,
                    modifier = Modifier.fillMaxWidth(),
                    remaining = runningModeRemaining,
                    sceneManaged = runningModeSceneManaged,
                    smbEnabled = smbEnabled,
                    enabled = commandsAllowed,
                    onClick = { onNavigate(NavigationRequest.Element(ElementType.RUNNING_MODE)) }
                )
            }
            if (tempTargetText.isNotEmpty()) {
                TempTargetChip(
                    targetText = tempTargetText,
                    state = tempTargetState,
                    progress = tempTargetProgress,
                    reason = tempTargetReason,
                    onClick = { onNavigate(NavigationRequest.Element(ElementType.TEMP_TARGET_MANAGEMENT)) },
                    enabled = commandsAllowed,
                    modifier = Modifier.fillMaxWidth(),
                    sceneManaged = tempTargetSceneManaged
                )
            }
        }
    }
}
