package app.aaps.core.ui

import app.aaps.core.data.model.CA
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer

/**
 * Result of reading the optional meal carb field of a confirm dialog.
 */
sealed interface MealCarbsInput {

    /** The field was left empty, or set to 0: record no carbs. */
    data object None : MealCarbsInput

    /** A usable gram amount, already clamped to the carb limit. */
    data class Grams(val amount: Double) : MealCarbsInput

    /** The text is not a usable meal carb amount. Write nothing. */
    data object Invalid : MealCarbsInput
}

/**
 * Parse the optional meal carb field of a confirm dialog.
 *
 * Blank or zero means "no carbs". A trailing "g" is accepted, because "40g" is the natural way to
 * write this. Both "." and "," work as the decimal mark: the number pad uses the mark of the phone
 * language, and this is user input, not a translated text.
 *
 * A negative number is rejected. This field announces a meal, so it is not a way to remove COB.
 * Anything above [maxCarbs] is clamped, the same way the carbs dialog clamps.
 */
fun parseMealCarbs(raw: String, maxCarbs: Double): MealCarbsInput {
    val cleaned = raw.trim().removeSuffix("g").removeSuffix("G").trim()
    if (cleaned.isEmpty()) return MealCarbsInput.None
    // User input, not a resource string: accept both decimal marks before parsing.
    val value = cleaned.replace(',', '.').toDoubleOrNull() ?: return MealCarbsInput.Invalid
    if (value < 0.0) return MealCarbsInput.Invalid
    if (value == 0.0) return MealCarbsInput.None
    return MealCarbsInput.Grams(value.coerceAtMost(maxCarbs))
}

/**
 * Write the optional meal carb field of a confirm dialog.
 *
 * [raw] is what the user typed. Blank or zero writes nothing. A gram amount writes one instant
 * carb row. The parse result is returned unchanged, so the caller can show one message about it.
 *
 * This writes only the carb row. The meal mode itself is the caller's job and runs first: the
 * note is what arms the meal doses, so it must not wait for the carb row. An invalid amount is
 * reported and nothing is written, and the mode still runs. A typo must not stop meal insulin.
 *
 * @param note kept on the carb row and in the treatments log, so the meal can be found later.
 * @param now the same timestamp as the meal note, so COB and the meal clock start together.
 */
suspend fun recordMealCarbs(
    persistenceLayer: PersistenceLayer,
    raw: String,
    maxCarbs: Double,
    note: String?,
    now: Long,
): MealCarbsInput {
    val parsed = parseMealCarbs(raw, maxCarbs)
    if (parsed is MealCarbsInput.Grams) {
        persistenceLayer.insertOrUpdateCarbs(
            CA(
                timestamp = now,
                duration = 0L,
                amount = parsed.amount,
                notes = note,
            ),
            action = Action.CARBS,
            source = Sources.CarbDialog,
            note = note,
        )
    }
    return parsed
}
