package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import app.aaps.core.ui.CoreUiStrings

/**
 * Optional meal carb field, for use inside a confirm dialog.
 *
 * The field is always optional. Left blank, or set to 0, no carbs are recorded and the action runs
 * as before. A gram amount records one instant carb row next to the action.
 *
 * [value] is the raw text, not a number: the parse rules live in `parseMealCarbs`, and an invalid
 * amount must not block the action the dialog is about.
 */
@Composable
fun MealCarbsField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(stringResource(CoreUiStrings.label_carbs_g)) },
        supportingText = { Text(stringResource(CoreUiStrings.carbs_optional_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
