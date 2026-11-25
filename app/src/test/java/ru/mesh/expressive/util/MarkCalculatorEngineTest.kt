package ru.mesh.expressive.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.mesh.expressive.data.model.MarkItem
import ru.mesh.expressive.data.model.RoundingLimits
import ru.mesh.expressive.data.model.SubjectSummary

class MarkCalculatorEngineTest {

    private val standardLimits = RoundingLimits(
        two = 0.0,
        three = 2.60,
        four = 3.60,
        five = 4.60
    )

    @Test
    fun `closing active point and adding weighted virtual mark updates average and final grade`() {
        val subject = SubjectSummary(
            subject = "Математика",
            averageMark = 4.0,
            marks = listOf(
                MarkItem(id = "1", value = 4, rawValue = "4", weight = 1.0),
                MarkItem(id = "2", value = 4, rawValue = "4", weight = 1.0),
                MarkItem(id = "3", value = 0, rawValue = ".", isPoint = true, pointDate = "2099-01-01", weight = 2.0)
            )
        )

        val initial = MarkCalculatorEngine.buildInitialExistingMarks(subject)
        val baseRes = MarkCalculatorEngine.calculate(subject, initial, emptyList(), showWeighted = true, limits = standardLimits)
        assertEquals(4.0, baseRes.simAverage, 0.001)
        assertEquals(4, baseRes.simFinalMark)
        assertEquals(1, baseRes.activePointsCount)

        // Close the active point with a 5 (weight 2.0) -> (4 + 4 + 5*2) / (1 + 1 + 2) = 18 / 4 = 4.50
        val closedPoint = initial.map {
            if (it.index == 2) it.copy(overrideValue = 5) else it
        }
        val afterPoint = MarkCalculatorEngine.calculate(subject, closedPoint, emptyList(), showWeighted = true, limits = standardLimits)
        assertEquals(4.5, afterPoint.simAverage, 0.001)
        assertEquals(4, afterPoint.simFinalMark)
        assertEquals(0, afterPoint.activePointsCount)

        // Add one virtual 5 with weight 1.0 -> (18 + 5) / 5 = 4.60 -> final mark 5
        val withVirtual = MarkCalculatorEngine.calculate(
            subject = subject,
            existingMarks = closedPoint,
            virtualMarks = listOf(VirtualMark(id = 1L, value = 5, weight = 1.0)),
            showWeighted = true,
            limits = standardLimits
        )
        assertEquals(4.6, withVirtual.simAverage, 0.001)
        assertEquals(5, withVirtual.simFinalMark)
    }

    @Test
    fun `excluding bad mark and calculating goal hints with school thresholds`() {
        val subject = SubjectSummary(
            subject = "Физика",
            averageMark = 3.5,
            marks = listOf(
                MarkItem(id = "1", value = 2, rawValue = "2", weight = 1.0),
                MarkItem(id = "2", value = 5, rawValue = "5", weight = 1.0)
            )
        )
        val initial = MarkCalculatorEngine.buildInitialExistingMarks(subject)
        val res = MarkCalculatorEngine.calculate(subject, initial, emptyList(), showWeighted = true, limits = standardLimits)

        assertEquals(3.5, res.simAverage, 0.001)
        assertEquals(3, res.simFinalMark)

        // Check goal hints for 4 (threshold 3.60)
        // At x1: (7 + 5n) / (2 + n) >= 3.60 -> 1.4n >= 0.2 -> n = 1 (12/3 = 4.0)
        val hints4 = res.goalHints[4]
        assertNotNull(hints4)
        val x1Hint4 = hints4!!.first { it.weight == 1.0 && it.foursCount == 0 }
        assertEquals(1, x1Hint4.fivesCount)

        // Exclude the 2 -> average becomes 5.0
        val excluded = initial.map { if (it.index == 0) it.copy(isExcluded = true) else it }
        val resExcl = MarkCalculatorEngine.calculate(subject, excluded, emptyList(), showWeighted = true, limits = standardLimits)
        assertEquals(5.0, resExcl.simAverage, 0.001)
        assertEquals(5, resExcl.simFinalMark)
        assertTrue((resExcl.safetyMargin?.safeTwosX1 ?: 0) == 0) // (5 - 4.60) / (4.60 - 2) = 0.4 / 2.6 = 0
        assertTrue((resExcl.safetyMargin?.safeThreesX1 ?: 0) == 0) // (5 - 4.60) / (4.60 - 3) = 0.4 / 1.6 = 0
    }

    @Test
    fun `safety margin computes how many twos and threes can be received`() {
        val subject = SubjectSummary(
            subject = "История",
            averageMark = 4.8,
            marks = listOf(
                MarkItem(id = "1", value = 5, rawValue = "5", weight = 1.0),
                MarkItem(id = "2", value = 5, rawValue = "5", weight = 1.0),
                MarkItem(id = "3", value = 5, rawValue = "5", weight = 1.0),
                MarkItem(id = "4", value = 5, rawValue = "5", weight = 1.0),
                MarkItem(id = "5", value = 4, rawValue = "4", weight = 1.0)
            )
        )
        // Sum = 24, Weight = 5, Avg = 4.80, threshold for 5 = 4.50 (custom school)
        val customLimits = RoundingLimits(three = 2.50, four = 3.50, five = 4.50)
        val initial = MarkCalculatorEngine.buildInitialExistingMarks(subject)
        val res = MarkCalculatorEngine.calculate(subject, initial, emptyList(), showWeighted = true, limits = customLimits)

        assertEquals(5, res.simFinalMark)
        val margin = res.safetyMargin
        assertNotNull(margin)
        // Buffer = 24 - 4.5 * 5 = 1.5
        // Safe 2s: floor(1.5 / (4.5 - 2.0)) = floor(1.5 / 2.5) = 0
        // Safe 3s: floor(1.5 / (4.5 - 3.0)) = floor(1.5 / 1.5) = 1
        assertEquals(0, margin!!.safeTwosX1)
        assertEquals(1, margin.safeThreesX1)
    }
}
