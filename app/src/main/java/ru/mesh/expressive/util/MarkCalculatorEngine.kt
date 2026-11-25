package ru.mesh.expressive.util

import ru.mesh.expressive.data.model.MarkItem
import ru.mesh.expressive.data.model.RoundingLimits
import ru.mesh.expressive.data.model.SubjectSummary
import kotlin.math.ceil
import kotlin.math.floor

data class SimulatedExistingMark(
    val index: Int,
    val original: MarkItem,
    val overrideValue: Int? = null,
    val isExcluded: Boolean = false
) {
    val isPointLike: Boolean
        get() = original.isPoint ||
            !original.pointDate.isNullOrBlank() ||
            original.rawValue.endsWith(".") ||
            original.rawValue == "."

    val isModified: Boolean
        get() = overrideValue != null || isExcluded

    val effectiveSimValue: Int
        get() = when {
            isExcluded -> 0
            overrideValue != null -> overrideValue
            else -> original.effectiveValue
        }

    val isIncludedInCalc: Boolean
        get() = !isExcluded && effectiveSimValue in 2..5
}

data class VirtualMark(
    val id: Long,
    val value: Int,
    val weight: Double
)

data class MarkCombinationHint(
    val targetGrade: Int,
    val threshold: Double,
    val fivesCount: Int,
    val foursCount: Int,
    val weight: Double,
    val resultingAverage: Double
)

data class SafetyMargin(
    val currentGrade: Int,
    val threshold: Double,
    val safeTwosX1: Int,
    val safeThreesX1: Int
)

data class SimulationResult(
    val baseAverage: Double,
    val simAverage: Double,
    val delta: Double,
    val baseFinalMark: Int,
    val simFinalMark: Int,
    val weightedSum: Double,
    val totalWeight: Double,
    val activePointsCount: Int,
    val expiredPointsCount: Int,
    val goalHints: Map<Int, List<MarkCombinationHint>>,
    val safetyMargin: SafetyMargin?
)

object MarkCalculatorEngine {

    fun buildInitialExistingMarks(subject: SubjectSummary): List<SimulatedExistingMark> =
        subject.marks.mapIndexed { index, mark ->
            SimulatedExistingMark(index = index, original = mark)
        }

    fun calculate(
        subject: SubjectSummary,
        existingMarks: List<SimulatedExistingMark>,
        virtualMarks: List<VirtualMark>,
        showWeighted: Boolean,
        limits: RoundingLimits
    ): SimulationResult {
        val baseAverage = subject.getEffectiveAverage(showWeighted)
        val baseFinalMark = limits.resolveFinalMark(baseAverage)

        var sum = 0.0
        var weight = 0.0
        var hasValidExisting = false

        for (item in existingMarks) {
            if (item.isIncludedInCalc) {
                hasValidExisting = true
                val w = if (showWeighted) item.original.weight.coerceAtLeast(0.1) else 1.0
                sum += item.effectiveSimValue * w
                weight += w
            }
        }

        // Fallback if subject has averageMark from API but empty/unparsed marks list
        if (!hasValidExisting && existingMarks.isEmpty() && subject.averageMark > 0.0) {
            sum = subject.averageMark
            weight = 1.0
        }

        for (vm in virtualMarks) {
            if (vm.value in 2..5) {
                val w = if (showWeighted) vm.weight.coerceAtLeast(0.1) else 1.0
                sum += vm.value * w
                weight += w
            }
        }

        val simAverage = if (weight > 0.0) sum / weight else 0.0
        val simFinalMark = limits.resolveFinalMark(simAverage)
        val delta = if (baseAverage > 0.0 && simAverage > 0.0) simAverage - baseAverage else 0.0

        val activePointsCount = existingMarks.count {
            it.isPointLike && !it.isExcluded && it.overrideValue == null && !it.original.isPointExpired
        }
        val expiredPointsCount = existingMarks.count {
            it.isPointLike && !it.isExcluded && it.overrideValue == null && it.original.isPointExpired
        }

        val goalHints = buildGoalHints(
            sum = sum,
            weight = weight,
            simAverage = simAverage,
            showWeighted = showWeighted,
            limits = limits
        )

        val safetyMargin = buildSafetyMargin(
            sum = sum,
            weight = weight,
            simFinalMark = simFinalMark,
            limits = limits
        )

        return SimulationResult(
            baseAverage = baseAverage,
            simAverage = simAverage,
            delta = delta,
            baseFinalMark = baseFinalMark,
            simFinalMark = simFinalMark,
            weightedSum = sum,
            totalWeight = weight,
            activePointsCount = activePointsCount,
            expiredPointsCount = expiredPointsCount,
            goalHints = goalHints,
            safetyMargin = safetyMargin
        )
    }

    private fun buildGoalHints(
        sum: Double,
        weight: Double,
        simAverage: Double,
        showWeighted: Boolean,
        limits: RoundingLimits
    ): Map<Int, List<MarkCombinationHint>> {
        val result = linkedMapOf<Int, List<MarkCombinationHint>>()
        val targetGrades = listOf(5, 4, 3)

        for (grade in targetGrades) {
            val threshold = limits.thresholdFor(grade)
            if (simAverage >= threshold - 1e-6) continue

            val hints = mutableListOf<MarkCombinationHint>()
            val weightsToTry = if (showWeighted) listOf(1.0, 2.0, 3.0) else listOf(1.0)

            for (w in weightsToTry) {
                val neededFives = neededSameMarks(sum, weight, markValue = 5, markWeight = w, threshold = threshold)
                if (neededFives in 1..30) {
                    val newAvg = (sum + 5.0 * w * neededFives) / (weight + w * neededFives)
                    hints.add(
                        MarkCombinationHint(
                            targetGrade = grade,
                            threshold = threshold,
                            fivesCount = neededFives,
                            foursCount = 0,
                            weight = w,
                            resultingAverage = newAvg
                        )
                    )
                }
            }

            // For target 4 (or 3), also show pure 4s at weight 1.0 if achievable and different
            if (grade <= 4 && threshold < 4.0) {
                val neededFours = neededSameMarks(sum, weight, markValue = 4, markWeight = 1.0, threshold = threshold)
                if (neededFours in 1..30) {
                    val alreadySame = hints.any { it.weight == 1.0 && it.foursCount == neededFours && it.fivesCount == 0 }
                    if (!alreadySame) {
                        val newAvg = (sum + 4.0 * neededFours) / (weight + neededFours)
                        hints.add(
                            MarkCombinationHint(
                                targetGrade = grade,
                                threshold = threshold,
                                fivesCount = 0,
                                foursCount = neededFours,
                                weight = 1.0,
                                resultingAverage = newAvg
                            )
                        )
                    }
                }
            }

            // For target 5 at x1, check if one 5 can be softened to a 4
            val x1Hint = hints.firstOrNull { it.weight == 1.0 && it.fivesCount >= 2 && it.foursCount == 0 }
            if (grade == 5 && x1Hint != null) {
                val fives = x1Hint.fivesCount - 1
                val fours = 1
                val newW = weight + x1Hint.fivesCount
                val newS = sum + 5.0 * fives + 4.0 * fours
                val newAvg = if (newW > 0.0) newS / newW else 0.0
                if (newAvg >= threshold - 1e-6) {
                    hints.add(
                        MarkCombinationHint(
                            targetGrade = grade,
                            threshold = threshold,
                            fivesCount = fives,
                            foursCount = fours,
                            weight = 1.0,
                            resultingAverage = newAvg
                        )
                    )
                }
            }

            if (hints.isNotEmpty()) {
                result[grade] = hints
            }
        }
        return result
    }

    private fun neededSameMarks(
        sum: Double,
        weight: Double,
        markValue: Int,
        markWeight: Double,
        threshold: Double
    ): Int {
        val denom = markWeight * (markValue - threshold)
        if (denom <= 1e-6) return -1
        val raw = (threshold * weight - sum) / denom
        if (raw <= 1e-9) return 0
        return ceil(raw - 1e-9).toInt()
    }

    private fun buildSafetyMargin(
        sum: Double,
        weight: Double,
        simFinalMark: Int,
        limits: RoundingLimits
    ): SafetyMargin? {
        if (simFinalMark < 3 || weight <= 0.0) return null
        val threshold = limits.thresholdFor(simFinalMark)
        val buffer = sum - threshold * weight
        if (buffer < -1e-6) return null

        val safeTwos = if (threshold > 2.0 + 1e-6) {
            floor((buffer + 1e-9) / (threshold - 2.0)).toInt().coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        val safeThrees = if (threshold > 3.0 + 1e-6) {
            floor((buffer + 1e-9) / (threshold - 3.0)).toInt().coerceAtLeast(0)
        } else {
            Int.MAX_VALUE
        }
        return SafetyMargin(
            currentGrade = simFinalMark,
            threshold = threshold,
            safeTwosX1 = safeTwos,
            safeThreesX1 = safeThrees
        )
    }
}
