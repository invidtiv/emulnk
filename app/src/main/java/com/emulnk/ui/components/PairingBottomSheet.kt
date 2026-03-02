package com.emulnk.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emulnk.R
import com.emulnk.model.ThemeConfig
import com.emulnk.model.ThemeType
import com.emulnk.model.resolvedType
import com.emulnk.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingBottomSheet(
    selectedItem: ThemeConfig,
    companions: List<ThemeConfig>,
    gameName: String,
    onDismiss: () -> Unit,
    onLaunch: (theme: ThemeConfig?, overlay: ThemeConfig?, setDefault: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCompanionIndex by remember { mutableIntStateOf(-1) } // -1 = None
    var setDefault by remember { mutableStateOf(false) }

    val isTheme = selectedItem.resolvedType == ThemeType.THEME
    val title = if (isTheme) stringResource(R.string.pair_with_overlay) else stringResource(R.string.pair_with_theme)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceRaised,
        shape = RoundedCornerShape(topStart = EmuLnkDimens.cornerLg, topEnd = EmuLnkDimens.cornerLg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = EmuLnkDimens.spacingXl)
                .padding(bottom = EmuLnkDimens.spacingXl)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(EmuLnkDimens.spacingLg))

            Column(modifier = Modifier.selectableGroup()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedCompanionIndex == -1,
                            onClick = { selectedCompanionIndex = -1 },
                            role = Role.RadioButton
                        )
                        .padding(vertical = EmuLnkDimens.spacingMd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedCompanionIndex == -1,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = BrandPurple, unselectedColor = TextTertiary)
                    )
                    Spacer(modifier = Modifier.width(EmuLnkDimens.spacingMd))
                    Text(
                        text = stringResource(R.string.pair_none),
                        fontSize = 14.sp,
                        color = TextSecondary
                    )
                }

                companions.forEachIndexed { index, companion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedCompanionIndex == index,
                                onClick = { selectedCompanionIndex = index },
                                role = Role.RadioButton
                            )
                            .padding(vertical = EmuLnkDimens.spacingMd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedCompanionIndex == index,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = BrandPurple, unselectedColor = TextTertiary)
                        )
                        Spacer(modifier = Modifier.width(EmuLnkDimens.spacingMd))
                        Text(
                            text = companion.meta.name,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(EmuLnkDimens.spacingMd))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = setDefault,
                    onCheckedChange = { setDefault = it },
                    colors = CheckboxDefaults.colors(checkedColor = BrandPurple, uncheckedColor = TextTertiary)
                )
                Spacer(modifier = Modifier.width(EmuLnkDimens.spacingSm))
                Text(
                    text = stringResource(R.string.pair_set_default, gameName),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(EmuLnkDimens.spacingLg))

            Button(
                onClick = {
                    val selectedCompanion = if (selectedCompanionIndex >= 0) companions[selectedCompanionIndex] else null
                    if (isTheme) {
                        onLaunch(selectedItem, selectedCompanion, setDefault)
                    } else {
                        onLaunch(selectedCompanion, selectedItem, setDefault)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                shape = RoundedCornerShape(EmuLnkDimens.cornerMd)
            ) {
                Text(
                    text = stringResource(R.string.pair_launch),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
