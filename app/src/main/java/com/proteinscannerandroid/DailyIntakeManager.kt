package com.proteinscannerandroid

import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

object DailyIntakeManager {
    private const val PREFS_NAME = "DailyIntakePrefs"
    private const val KEY_DAILY_GOAL = "daily_protein_goal"
    private const val KEY_GOAL_CELEBRATED_DATE = "goal_celebrated_date"
    private const val DEFAULT_GOAL = 150.0

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun todayDateString(): String = dateFormat.format(Date())

    fun getGoal(context: Context): Double {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_DAILY_GOAL, DEFAULT_GOAL.toFloat()).toDouble()
    }

    fun setGoal(context: Context, grams: Double) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_DAILY_GOAL, grams.toFloat())
            .apply()
    }

    fun hasAlreadyCelebratedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_GOAL_CELEBRATED_DATE, "") == todayDateString()
    }

    fun markCelebratedToday(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GOAL_CELEBRATED_DATE, todayDateString())
            .apply()
    }

    /**
     * Calculate streak of consecutive days where the protein goal was met.
     * Expects a list of date strings where the goal was achieved.
     */
    fun calculateStreak(datesWithEntries: List<String>): Int {
        if (datesWithEntries.isEmpty()) return 0

        val sortedDates = datesWithEntries
            .mapNotNull { runCatching { dateFormat.parse(it) }.getOrNull() }
            .sortedDescending()

        if (sortedDates.isEmpty()) return 0

        val calendar = Calendar.getInstance()
        // Start from today
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val todayCal = calendar.clone() as Calendar
        val firstDate = Calendar.getInstance().apply { time = sortedDates[0] }
        firstDate.set(Calendar.HOUR_OF_DAY, 0)
        firstDate.set(Calendar.MINUTE, 0)
        firstDate.set(Calendar.SECOND, 0)
        firstDate.set(Calendar.MILLISECOND, 0)

        // If the most recent entry is not today or yesterday, streak is 0
        val diffDays = ((todayCal.timeInMillis - firstDate.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        if (diffDays > 1) return 0

        // Build set of date strings for fast lookup
        val dateSet = datesWithEntries.toSet()

        var streak = 0
        val checkCal = todayCal.clone() as Calendar

        // If today has no entry, start checking from yesterday
        if (!dateSet.contains(dateFormat.format(checkCal.time))) {
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        // Count consecutive days backward
        while (dateSet.contains(dateFormat.format(checkCal.time))) {
            streak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        }

        return streak
    }

    fun progressPercent(currentGrams: Double, goal: Double): Int {
        if (goal <= 0) return 0
        return ((currentGrams / goal) * 100).coerceIn(0.0, 100.0).toInt()
    }

    data class MotivationalMessage(val emoji: String, val title: String, val subtitle: String)

    /**
     * Get a motivational message based on current state.
     * Uses day-of-year to rotate messages so they vary daily but stay consistent within a day.
     */
    fun getMotivationalMessage(streak: Int, todayTotal: Double, goal: Double, hasEntriesToday: Boolean): MotivationalMessage {
        val dayIndex = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val goalMetToday = todayTotal >= goal

        return when {
            // Has an active streak
            streak >= 30 -> pick(dayIndex, listOf(
                MotivationalMessage("👑", "$streak Day Streak!", "Incredible dedication!"),
                MotivationalMessage("🏆", "$streak Day Streak!", "Legendary streak!"),
                MotivationalMessage("🔥", "$streak Day Streak!", "Absolute champion!")
            ))
            streak >= 14 -> pick(dayIndex, listOf(
                MotivationalMessage("🔥", "$streak Day Streak!", "You're on fire!"),
                MotivationalMessage("🏆", "$streak Day Streak!", "Two weeks of dedication!"),
                MotivationalMessage("⚡", "$streak Day Streak!", "Machine mode!")
            ))
            streak >= 7 -> pick(dayIndex, listOf(
                MotivationalMessage("🏆", "$streak Day Streak!", "One week strong!"),
                MotivationalMessage("🔥", "$streak Day Streak!", "A full week — beast mode!"),
                MotivationalMessage("💪", "$streak Day Streak!", "Unstoppable!")
            ))
            streak >= 3 -> pick(dayIndex, listOf(
                MotivationalMessage("🔥", "$streak Day Streak!", "Keep it going!"),
                MotivationalMessage("💪", "$streak Day Streak!", "You're consistent!"),
                MotivationalMessage("⚡", "$streak Day Streak!", "Crushing it!")
            ))
            streak >= 1 -> pick(dayIndex, listOf(
                MotivationalMessage("🌱", "$streak Day Streak!", "Great start!"),
                MotivationalMessage("💪", "$streak Day Streak!", "Building momentum!"),
                MotivationalMessage("🔥", "$streak Day Streak!", "Keep it up!")
            ))
            // No streak but hit goal today
            goalMetToday -> pick(dayIndex, listOf(
                MotivationalMessage("🎉", "Goal Crushed!", "Do it again tomorrow for a streak!"),
                MotivationalMessage("⚡", "Nailed It!", "Come back tomorrow to start a streak!"),
                MotivationalMessage("💪", "Target Hit!", "One more day and you've got a streak!")
            ))
            // No streak, has entries but goal not yet met
            hasEntriesToday -> pick(dayIndex, listOf(
                MotivationalMessage("💪", "You're On Your Way!", "Keep adding to hit your goal!"),
                MotivationalMessage("🎯", "Good Start!", "Chase that ${goal.toInt()}g target!"),
                MotivationalMessage("🔥", "First Entry Logged!", "Keep going — you've got this!")
            ))
            // No streak, no entries
            else -> pick(dayIndex, listOf(
                MotivationalMessage("💪", "Get Those Gains!", "Start tracking your protein today!"),
                MotivationalMessage("🎯", "Ready to Go!", "Log your first meal to begin!"),
                MotivationalMessage("🚀", "New Day, New Goals!", "Your protein journey starts here!")
            ))
        }
    }

    private fun <T> pick(dayIndex: Int, options: List<T>): T {
        return options[dayIndex % options.size]
    }
}
