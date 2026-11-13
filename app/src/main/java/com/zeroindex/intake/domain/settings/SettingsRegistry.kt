package com.zeroindex.intake.domain.settings

/** Salt or sodium on the Nutrition dashboard (Settings > Nutrition). */
enum class SodiumDisplay(val label: String) { OFF("Off"), SODIUM("Sodium"), SALT("Salt") }

/** The five optional Today cards. Calories is always shown. */
enum class TodayCard { NEXT, WATER, WEIGHT, MEALS, ACTIVITY }

/** The twelve Settings pages, in the order the home lists them (docs/SETTINGS-INVENTORY.md). */
enum class SettingsGroup(val id: String, val title: String, val blurb: String) {
    APPEARANCE("appearance", "Appearance and units", "How numbers and colours look. Nothing here changes your data."),
    NUTRITION("nutrition", "Nutrition", "How your targets are worked out and what the dashboard shows. Your allergens are on the Allergens screen in Nutrition, because they are safety data."),
    WATER("water", "Water and drinks", "The water target, how it adjusts, and which sizes you tap. Logging a drink is in Nutrition > Drinks."),
    TRAINING("training", "Training", "Rest timer, bar and plates, equipment and volume targets."),
    HEALTH("health", "Health Connect", "Which records Intake may read from and write to Health Connect."),
    FOOD_DB("fooddb", "Food database", "Where food data comes from. The USDA key is under API keys."),
    VIDEOS("videos", "Videos", "Videos need internet and your own YouTube key (under API keys). Intake never downloads a video."),
    REMINDERS("reminders", "Reminders", "When Intake may nudge you. Nothing is sent anywhere."),
    SYNC("sync", "Sync and export", "Your own server and your own files."),
    AI("ai", "AI (optional)", "Optional. Intake works with no network, no account and no AI. AI runs only when you ask."),
    KEYS("keys", "API keys", "Keys live in the encrypted key store on this phone. Keys never go in a link or a log."),
    ABOUT("about", "About and help", "Help, credits and the privacy statement."),
    ;

    companion object {
        fun byId(id: String?): SettingsGroup? = entries.firstOrNull { it.id == id }
    }
}

/** What kind of control a setting is drawn with. */
enum class ControlKind { SWITCH, SEGMENTED, RADIO, NUMBER, TEXT, SECRET, CHIPS, PICKER, TABLE, ACTION, READ_ONLY }

/**
 * One row of docs/SETTINGS-INVENTORY.md. [id] is the inventory key, which is also the string the Settings UI
 * renders the control by (SettingsControls.kt). Actions and the read-only line are controls but not stored values.
 */
data class SettingSpec(
    val number: Int,
    val id: String,
    val group: SettingsGroup,
    val kind: ControlKind,
    val label: String,
    val advanced: Boolean = false,
    val isNew: Boolean = false,
) {
    val isStored: Boolean get() = kind != ControlKind.ACTION && kind != ControlKind.READ_ONLY
}

/**
 * The settings registry: all 125 controls of the inventory (108 stored settings, 16 actions, 1 read-only line), each
 * with its group. A test checks it against the inventory file and that the UI renders every id.
 */
object SettingsRegistry {
    val all: List<SettingSpec> = listOf(
        SettingSpec(1, "weightUnit", SettingsGroup.APPEARANCE, ControlKind.SEGMENTED, "Weight", advanced = false, isNew = false),
        SettingSpec(2, "lengthUnit", SettingsGroup.APPEARANCE, ControlKind.SEGMENTED, "Length", advanced = false, isNew = false),
        SettingSpec(3, "volumeUnit", SettingsGroup.APPEARANCE, ControlKind.SEGMENTED, "Drinks and water", advanced = false, isNew = false),
        SettingSpec(4, "portionUnit", SettingsGroup.APPEARANCE, ControlKind.SEGMENTED, "Food portions", advanced = false, isNew = true),
        SettingSpec(5, "themeMode", SettingsGroup.APPEARANCE, ControlKind.SEGMENTED, "Theme", advanced = false, isNew = false),
        SettingSpec(6, "dynamicColor", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Use wallpaper colours", advanced = false, isNew = false),
        SettingSpec(7, "reduceMotion", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Reduce motion", advanced = false, isNew = false),
        SettingSpec(8, "todayCard.next", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Today card: Next up", advanced = false, isNew = true),
        SettingSpec(9, "todayCard.water", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Today card: Water", advanced = false, isNew = true),
        SettingSpec(10, "todayCard.weight", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Today card: Weight", advanced = false, isNew = true),
        SettingSpec(11, "todayCard.meals", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Today card: Meals", advanced = false, isNew = true),
        SettingSpec(116, "todayCard.activity", SettingsGroup.APPEARANCE, ControlKind.SWITCH, "Today card: Activity", advanced = false, isNew = true),
        SettingSpec(12, "autoTargets", SettingsGroup.NUTRITION, ControlKind.SWITCH, "Work my targets out from my data", advanced = false, isNew = false),
        SettingSpec(13, "manualCalories", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Calories by hand", advanced = false, isNew = false),
        SettingSpec(14, "proteinPerKg", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Protein", advanced = false, isNew = false),
        SettingSpec(15, "fatPerKg", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Fat", advanced = false, isNew = false),
        SettingSpec(16, "addActiveKcal", SettingsGroup.NUTRITION, ControlKind.SWITCH, "Add active calories to my target", advanced = false, isNew = false),
        SettingSpec(17, "showSodium", SettingsGroup.NUTRITION, ControlKind.SEGMENTED, "Show salt or sodium on the dashboard", advanced = false, isNew = true),
        SettingSpec(18, "eatOutRadius", SettingsGroup.NUTRITION, ControlKind.SEGMENTED, "Eat out nearby: how far", advanced = false, isNew = true),
        SettingSpec(19, "recalc", SettingsGroup.NUTRITION, ControlKind.ACTION, "Recalculate from my data", advanced = false, isNew = false),
        SettingSpec(20, "allowBelowFloor", SettingsGroup.NUTRITION, ControlKind.SWITCH, "Allow calories below the floor", advanced = true, isNew = false),
        SettingSpec(21, "carbCycling", SettingsGroup.NUTRITION, ControlKind.SWITCH, "Carb cycling", advanced = true, isNew = false),
        SettingSpec(22, "carbCycleShiftPct", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Carb cycle shift", advanced = true, isNew = false),
        SettingSpec(23, "refeedDaysPerWeek", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Refeed days per week", advanced = true, isNew = false),
        SettingSpec(24, "dietBreakEveryWeeks", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Diet break every", advanced = true, isNew = false),
        SettingSpec(25, "contestWeeklyLossPct", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Contest prep: weekly loss", advanced = true, isNew = false),
        SettingSpec(26, "reverseStepKcal", SettingsGroup.NUTRITION, ControlKind.NUMBER, "Reverse diet: calories added per week", advanced = true, isNew = false),
        SettingSpec(27, "hydrationBase", SettingsGroup.WATER, ControlKind.NUMBER, "Daily water target", advanced = false, isNew = false),
        SettingSpec(28, "hydrationDynamic", SettingsGroup.WATER, ControlKind.SWITCH, "Adjust the target for the day", advanced = false, isNew = false),
        SettingSpec(29, "hydrationTrainingBonus", SettingsGroup.WATER, ControlKind.NUMBER, "Training-day increase", advanced = false, isNew = false),
        SettingSpec(30, "weatherEnabled", SettingsGroup.WATER, ControlKind.SWITCH, "Adjust water for weather", advanced = false, isNew = false),
        SettingSpec(31, "locationMode", SettingsGroup.WATER, ControlKind.SEGMENTED, "Weather location", advanced = false, isNew = false),
        SettingSpec(32, "place", SettingsGroup.WATER, ControlKind.TEXT, "City or ZIP code", advanced = false, isNew = false),
        SettingSpec(33, "forgetLocation", SettingsGroup.WATER, ControlKind.ACTION, "Forget this location", advanced = false, isNew = false),
        SettingSpec(34, "quickSizes", SettingsGroup.WATER, ControlKind.CHIPS, "Quick sizes", advanced = false, isNew = false),
        SettingSpec(35, "drinkOverrides", SettingsGroup.WATER, ControlKind.TABLE, "Drink values", advanced = true, isNew = false),
        SettingSpec(36, "sweatMeasure", SettingsGroup.WATER, ControlKind.NUMBER, "Measure my sweat rate", advanced = true, isNew = false),
        SettingSpec(37, "sweatUse", SettingsGroup.WATER, ControlKind.SWITCH, "Use my measured sweat rate", advanced = true, isNew = false),
        SettingSpec(38, "defaultRestSec", SettingsGroup.TRAINING, ControlKind.NUMBER, "Default rest", advanced = false, isNew = false),
        SettingSpec(39, "restVibrate", SettingsGroup.TRAINING, ControlKind.SWITCH, "Vibrate when rest ends", advanced = false, isNew = false),
        SettingSpec(40, "barWeight", SettingsGroup.TRAINING, ControlKind.NUMBER, "Bar weight", advanced = false, isNew = false),
        SettingSpec(41, "plates", SettingsGroup.TRAINING, ControlKind.CHIPS, "Plates available per side", advanced = false, isNew = false),
        SettingSpec(42, "equipmentPreset", SettingsGroup.TRAINING, ControlKind.CHIPS, "My equipment: preset", advanced = false, isNew = false),
        SettingSpec(43, "equipment", SettingsGroup.TRAINING, ControlKind.CHIPS, "My equipment: items", advanced = false, isNew = false),
        SettingSpec(44, "jumpTests", SettingsGroup.TRAINING, ControlKind.SWITCH, "Show jump tests in Progress", advanced = false, isNew = true),
        SettingSpec(45, "weeklySets", SettingsGroup.TRAINING, ControlKind.NUMBER, "Weekly sets target per muscle region", advanced = true, isNew = true),
        SettingSpec(61, "hcOpen", SettingsGroup.HEALTH, ControlKind.ACTION, "Allow all Health Connect permissions", advanced = false, isNew = false),
        SettingSpec(46, "hc.read.Exercisesessions", SettingsGroup.HEALTH, ControlKind.SWITCH, "Exercise sessions", advanced = false, isNew = false),
        SettingSpec(47, "hc.read.Totalcaloriesburned", SettingsGroup.HEALTH, ControlKind.SWITCH, "Total calories burned", advanced = false, isNew = false),
        SettingSpec(48, "hc.read.Activecalories", SettingsGroup.HEALTH, ControlKind.SWITCH, "Active calories", advanced = false, isNew = false),
        SettingSpec(49, "hc.read.Basalmetabolicrate", SettingsGroup.HEALTH, ControlKind.SWITCH, "Basal metabolic rate", advanced = false, isNew = false),
        SettingSpec(50, "hc.read.Steps", SettingsGroup.HEALTH, ControlKind.SWITCH, "Steps", advanced = false, isNew = false),
        SettingSpec(51, "hc.read.Sleep", SettingsGroup.HEALTH, ControlKind.SWITCH, "Sleep", advanced = false, isNew = false),
        SettingSpec(52, "hc.read.Heartrate", SettingsGroup.HEALTH, ControlKind.SWITCH, "Heart rate", advanced = false, isNew = false),
        SettingSpec(53, "hc.read.Restingheartrate", SettingsGroup.HEALTH, ControlKind.SWITCH, "Resting heart rate", advanced = false, isNew = false),
        SettingSpec(54, "hc.read.Weight", SettingsGroup.HEALTH, ControlKind.SWITCH, "Weight", advanced = false, isNew = false),
        SettingSpec(55, "hc.read.Bodyfat", SettingsGroup.HEALTH, ControlKind.SWITCH, "Body fat", advanced = false, isNew = false),
        SettingSpec(56, "hc.read.Leanbodymass", SettingsGroup.HEALTH, ControlKind.SWITCH, "Lean body mass", advanced = false, isNew = false),
        SettingSpec(57, "hc.write.Nutrition", SettingsGroup.HEALTH, ControlKind.SWITCH, "Nutrition", advanced = false, isNew = false),
        SettingSpec(58, "hc.write.Weight", SettingsGroup.HEALTH, ControlKind.SWITCH, "Weight", advanced = false, isNew = false),
        SettingSpec(59, "hc.write.Hydration", SettingsGroup.HEALTH, ControlKind.SWITCH, "Hydration", advanced = false, isNew = false),
        SettingSpec(60, "hc.write.Workouts", SettingsGroup.HEALTH, ControlKind.SWITCH, "Workouts", advanced = false, isNew = false),
        SettingSpec(117, "hc.read.Olderhistory", SettingsGroup.HEALTH, ControlKind.SWITCH, "Older history", advanced = false, isNew = true),
        SettingSpec(62, "offSearch", SettingsGroup.FOOD_DB, ControlKind.SWITCH, "Search Open Food Facts while typing", advanced = false, isNew = false),
        SettingSpec(115, "offLanguage", SettingsGroup.FOOD_DB, ControlKind.CHIPS, "Food search language", advanced = false, isNew = true),
        SettingSpec(63, "offOtherLanguages", SettingsGroup.FOOD_DB, ControlKind.SWITCH, "Show products in other languages", advanced = false, isNew = false),
        SettingSpec(64, "offContribute", SettingsGroup.FOOD_DB, ControlKind.SWITCH, "Allow contributing to Open Food Facts", advanced = false, isNew = false),
        SettingSpec(65, "offUsername", SettingsGroup.FOOD_DB, ControlKind.TEXT, "Open Food Facts username", advanced = false, isNew = false),
        SettingSpec(66, "offPassword", SettingsGroup.FOOD_DB, ControlKind.SECRET, "Open Food Facts password", advanced = false, isNew = false),
        SettingSpec(67, "onlyWorkouts", SettingsGroup.VIDEOS, ControlKind.SWITCH, "Show only workouts by default", advanced = false, isNew = true),
        SettingSpec(68, "personalise", SettingsGroup.VIDEOS, ControlKind.SWITCH, "Personalise Recommended from this phone's data", advanced = false, isNew = true),
        SettingSpec(114, "hideShorts", SettingsGroup.VIDEOS, ControlKind.SWITCH, "Hide Shorts", advanced = false, isNew = true),
        SettingSpec(130, "directVideos", SettingsGroup.VIDEOS, ControlKind.SWITCH, "Offline downloads and direct playback", advanced = false, isNew = true),
        SettingSpec(69, "coachNotify", SettingsGroup.REMINDERS, ControlKind.SWITCH, "Weekly check-in notification", advanced = false, isNew = false),
        SettingSpec(70, "coachWeekday", SettingsGroup.REMINDERS, ControlKind.CHIPS, "Check-in day", advanced = false, isNew = false),
        SettingSpec(71, "photoReminder", SettingsGroup.REMINDERS, ControlKind.SEGMENTED, "Progress photo reminder", advanced = false, isNew = true),
        SettingSpec(72, "exportReminder", SettingsGroup.REMINDERS, ControlKind.SWITCH, "Export reminder", advanced = false, isNew = true),
        SettingSpec(122, "workoutRemind", SettingsGroup.REMINDERS, ControlKind.SWITCH, "Workout reminder", advanced = false, isNew = true),
        SettingSpec(123, "workoutTime", SettingsGroup.REMINDERS, ControlKind.CHIPS, "Workout reminder time", advanced = false, isNew = true),
        SettingSpec(124, "mealRemind", SettingsGroup.REMINDERS, ControlKind.SWITCH, "Meal reminders", advanced = false, isNew = true),
        SettingSpec(125, "breakfastTime", SettingsGroup.REMINDERS, ControlKind.CHIPS, "Breakfast reminder time", advanced = false, isNew = true),
        SettingSpec(126, "lunchTime", SettingsGroup.REMINDERS, ControlKind.CHIPS, "Lunch reminder time", advanced = false, isNew = true),
        SettingSpec(127, "dinnerTime", SettingsGroup.REMINDERS, ControlKind.CHIPS, "Dinner reminder time", advanced = false, isNew = true),
        SettingSpec(128, "waterRemind", SettingsGroup.REMINDERS, ControlKind.SWITCH, "Water reminders", advanced = false, isNew = true),
        SettingSpec(129, "waterHours", SettingsGroup.REMINDERS, ControlKind.CHIPS, "Water reminder hours", advanced = false, isNew = true),
        SettingSpec(73, "syncServer", SettingsGroup.SYNC, ControlKind.TEXT, "Self-hosted sync server URL", advanced = false, isNew = false),
        SettingSpec(74, "pairCode", SettingsGroup.SYNC, ControlKind.TEXT, "Pairing code", advanced = false, isNew = false),
        SettingSpec(75, "deviceName", SettingsGroup.SYNC, ControlKind.TEXT, "Name this device", advanced = false, isNew = false),
        SettingSpec(76, "devices", SettingsGroup.SYNC, ControlKind.ACTION, "Linked devices", advanced = false, isNew = false),
        SettingSpec(77, "unpair", SettingsGroup.SYNC, ControlKind.ACTION, "Stop syncing here", advanced = false, isNew = false),
        SettingSpec(78, "exportPass", SettingsGroup.SYNC, ControlKind.SECRET, "Export passphrase", advanced = false, isNew = false),
        SettingSpec(79, "exportPhotos", SettingsGroup.SYNC, ControlKind.SWITCH, "Include progress photos in the export", advanced = false, isNew = true),
        SettingSpec(80, "exportJson", SettingsGroup.SYNC, ControlKind.ACTION, "Export everything (JSON)", advanced = false, isNew = false),
        SettingSpec(81, "exportCsv", SettingsGroup.SYNC, ControlKind.ACTION, "Export spreadsheets (CSV)", advanced = false, isNew = false),
        SettingSpec(82, "import", SettingsGroup.SYNC, ControlKind.ACTION, "Import a backup", advanced = false, isNew = false),
        SettingSpec(83, "aiEnabled", SettingsGroup.AI, ControlKind.SWITCH, "Use AI features", advanced = false, isNew = false),
        SettingSpec(84, "aiBudget", SettingsGroup.AI, ControlKind.NUMBER, "Monthly budget", advanced = false, isNew = false),
        SettingSpec(85, "aiPrivacy", SettingsGroup.AI, ControlKind.RADIO, "Privacy for AI requests", advanced = false, isNew = false),
        SettingSpec(86, "aiAllowRetaining", SettingsGroup.AI, ControlKind.SWITCH, "Suggest free models", advanced = false, isNew = false),
        SettingSpec(87, "dailyInsight", SettingsGroup.AI, ControlKind.SWITCH, "Daily insight", advanced = false, isNew = false),
        SettingSpec(88, "coachAiReword", SettingsGroup.AI, ControlKind.SWITCH, "Reword my check-in", advanced = false, isNew = false),
        SettingSpec(89, "menuAutoRefresh", SettingsGroup.AI, ControlKind.SWITCH, "Refresh saved menus weekly", advanced = false, isNew = false),
        SettingSpec(90, "aiModel.Photoestimates", SettingsGroup.AI, ControlKind.PICKER, "Model: Photo estimates", advanced = true, isNew = false),
        SettingSpec(91, "aiModel.RecipeHunter", SettingsGroup.AI, ControlKind.PICKER, "Model: Recipe Hunter", advanced = true, isNew = false),
        SettingSpec(92, "aiModel.PlanBfinder", SettingsGroup.AI, ControlKind.PICKER, "Model: Plan B finder", advanced = true, isNew = false),
        SettingSpec(93, "aiModel.Menuimport", SettingsGroup.AI, ControlKind.PICKER, "Model: Menu import", advanced = true, isNew = false),
        SettingSpec(94, "aiModel.Dailyinsight", SettingsGroup.AI, ControlKind.PICKER, "Model: Daily insight", advanced = true, isNew = false),
        SettingSpec(95, "aiUses.Photoestimates", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Photo estimates", advanced = true, isNew = false),
        SettingSpec(96, "aiUses.RecipeHunter", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Recipe Hunter", advanced = true, isNew = false),
        SettingSpec(97, "aiUses.PlanBfinder", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Plan B finder", advanced = true, isNew = false),
        SettingSpec(98, "aiUses.Menuimport", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Menu import", advanced = true, isNew = false),
        SettingSpec(99, "aiUses.Dailyinsight", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Daily insight", advanced = true, isNew = false),
        SettingSpec(100, "aiUses.Coachreword", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Coach reword", advanced = true, isNew = false),
        SettingSpec(101, "aiUses.RecipeTrywithAI", SettingsGroup.AI, ControlKind.NUMBER, "Uses a day: Recipe: Try with AI", advanced = true, isNew = false),
        SettingSpec(102, "aiReset", SettingsGroup.AI, ControlKind.ACTION, "Reset usage counters", advanced = true, isNew = false),
        SettingSpec(103, "aiBench", SettingsGroup.AI, ControlKind.ACTION, "Run benchmark", advanced = true, isNew = false),
        SettingSpec(104, "key.openrouter", SettingsGroup.KEYS, ControlKind.SECRET, "OpenRouter key", advanced = false, isNew = false),
        SettingSpec(105, "key.usda", SettingsGroup.KEYS, ControlKind.SECRET, "USDA FoodData Central key", advanced = false, isNew = false),
        SettingSpec(106, "key.youtube", SettingsGroup.KEYS, ControlKind.SECRET, "YouTube Data API key", advanced = false, isNew = true),
        SettingSpec(107, "tour", SettingsGroup.ABOUT, ControlKind.ACTION, "Replay the tour", advanced = false, isNew = false),
        SettingSpec(108, "welcome", SettingsGroup.ABOUT, ControlKind.ACTION, "Show the welcome again", advanced = false, isNew = false),
        SettingSpec(109, "tips", SettingsGroup.ABOUT, ControlKind.ACTION, "Show screen tips again", advanced = false, isNew = false),
        SettingSpec(110, "scripts", SettingsGroup.ABOUT, ControlKind.ACTION, "Script language help", advanced = false, isNew = false),
        SettingSpec(111, "privacy", SettingsGroup.ABOUT, ControlKind.ACTION, "What leaves this phone", advanced = false, isNew = false),
        SettingSpec(112, "credits", SettingsGroup.ABOUT, ControlKind.ACTION, "Credits and licences", advanced = false, isNew = false),
        SettingSpec(113, "version", SettingsGroup.ABOUT, ControlKind.READ_ONLY, "Version", advanced = false, isNew = false),
    )

    fun inGroup(group: SettingsGroup): List<SettingSpec> = all.filter { it.group == group }

    fun byId(id: String): SettingSpec? = all.firstOrNull { it.id == id }

    /** Settings search: every control whose label or group title contains all the words typed. Empty query = none. */
    fun search(query: String): List<SettingSpec> {
        val words = query.lowercase().split(' ', ',').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return all.filter { spec ->
            val hay = (spec.label + " " + spec.group.title).lowercase()
            words.all { it in hay }
        }
    }
}
