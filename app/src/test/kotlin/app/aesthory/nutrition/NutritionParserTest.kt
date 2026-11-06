package app.aesthory.nutrition

import org.junit.Assert.*
import org.junit.Test

class NutritionParserTest {
    @Test fun readsPlainTotalsWithUnitsAndThousands() {
        assertEquals(NutritionValues(1850.0, 130.5, 210.0, 60.0), NutritionParser.parse("Calories: 1,850 kcal\nProtein 130.5 g\nCarbohydrates 210 grams\nFat 60 g"))
    }
    @Test fun goalsRemainingAndServingValuesAreNotConsumedTotals() {
        assertEquals(NutritionValues(), NutritionParser.parse("Goal 2200\nCalories 1800\nProtein 90 g\nRemaining 400"))
        assertEquals(NutritionValues(1800.0), NutritionParser.parse("Goal 2200\nCalories consumed: 1800 kcal\nProtein 90 g"))
        assertEquals(NutritionValues(), NutritionParser.parse("Per serving\nCalories 200"))
    }
    @Test fun conflictingRepeatedAndMultiNumberLinesStayUnknown() {
        assertNull(NutritionParser.parse("Calories 1800\nCalories 1900").calories)
        assertNull(NutritionParser.parse("Protein 100 / 150 g").protein)
        assertNull(NutritionParser.parse("Calories 1800 2200").calories)
    }
    @Test fun negativeWrongUnitsAndOutOfBoundsAreRejected() {
        assertEquals(NutritionValues(), NutritionParser.parse("Calories -500\nProtein 100 mg\nFat 6000 g\nCarbs NaN"))
        assertNull(NutritionParser.parse("Energy 8000 kJ").calories)
    }
    @Test fun missingAndZeroStayDifferent() {
        assertEquals(NutritionValues(protein = 0.0, fat = 12.5), NutritionParser.parse("Protein 0 g\nFat 12,5 g"))
        assertNull(NutritionParser.entered("", 5000.0))
        assertEquals(0.0, NutritionParser.entered("0", 5000.0)!!, 0.0)
    }
    @Test fun manualNumbersNeedFinitePlainDecimals() {
        listOf("NaN", "Infinity", "-1", "1e3", "1,000", "20.123", "5001").forEach { raw -> assertTrue(runCatching { NutritionParser.entered(raw, 5000.0) }.isFailure) }
        assertEquals(12.5, NutritionParser.entered("12,5", 5000.0)!!, 0.0)
    }
}
