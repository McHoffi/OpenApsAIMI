package app.aaps.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MealCarbsInputTest {

    private val maxCarbs = 400.0

    @Test fun `blank means no carbs`() {
        assertThat(parseMealCarbs("", maxCarbs)).isEqualTo(MealCarbsInput.None)
        assertThat(parseMealCarbs("   ", maxCarbs)).isEqualTo(MealCarbsInput.None)
    }

    @Test fun `zero means no carbs`() {
        assertThat(parseMealCarbs("0", maxCarbs)).isEqualTo(MealCarbsInput.None)
        assertThat(parseMealCarbs("0.0", maxCarbs)).isEqualTo(MealCarbsInput.None)
    }

    @Test fun `a plain number is the grams`() {
        assertThat(parseMealCarbs("40", maxCarbs)).isEqualTo(MealCarbsInput.Grams(40.0))
        assertThat(parseMealCarbs("40.5", maxCarbs)).isEqualTo(MealCarbsInput.Grams(40.5))
    }

    @Test fun `a trailing g is accepted`() {
        assertThat(parseMealCarbs("40g", maxCarbs)).isEqualTo(MealCarbsInput.Grams(40.0))
        assertThat(parseMealCarbs("40 g", maxCarbs)).isEqualTo(MealCarbsInput.Grams(40.0))
        assertThat(parseMealCarbs("40G", maxCarbs)).isEqualTo(MealCarbsInput.Grams(40.0))
    }

    @Test fun `a comma works as the decimal mark`() {
        assertThat(parseMealCarbs("40,5", maxCarbs)).isEqualTo(MealCarbsInput.Grams(40.5))
    }

    @Test fun `a negative number is rejected`() {
        assertThat(parseMealCarbs("-5", maxCarbs)).isEqualTo(MealCarbsInput.Invalid)
    }

    @Test fun `text that is not a number is rejected`() {
        assertThat(parseMealCarbs("abc", maxCarbs)).isEqualTo(MealCarbsInput.Invalid)
        assertThat(parseMealCarbs("40grams", maxCarbs)).isEqualTo(MealCarbsInput.Invalid)
    }

    @Test fun `an amount above the limit is clamped`() {
        assertThat(parseMealCarbs("999", maxCarbs)).isEqualTo(MealCarbsInput.Grams(400.0))
    }

    @Test fun `an amount at the limit is kept`() {
        assertThat(parseMealCarbs("400", maxCarbs)).isEqualTo(MealCarbsInput.Grams(400.0))
    }
}
