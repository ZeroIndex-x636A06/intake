package com.zeroindex.intake.ui.navigation

/** What choosing a tab in the pill should do to the back stacks. */
enum class TabAction {
    /** Another tab: save this tab's stack and restore the target's (`saveState` / `restoreState`). */
    SWITCH,

    /** The current tab again, while deeper than its root: pop back to the root. */
    POP_TO_ROOT,

    /** The current tab again, already at its root: nothing to do. */
    NONE,
}

/** The pure rule for tab selection, so the policy can be tested without a NavController. */
object TabPolicy {
    fun <T> actionFor(currentTab: T?, targetTab: T, currentIsAtRoot: Boolean): TabAction = when {
        currentTab != targetTab -> TabAction.SWITCH
        currentIsAtRoot -> TabAction.NONE
        else -> TabAction.POP_TO_ROOT
    }

    /**
     * Fade-through is for moving between tabs; everything else is a push or pop inside one tab and
     * gets shared-axis X. A destination with no tab (for example during start-up) counts as a push.
     */
    fun <T> isTabSwitch(fromTab: T?, toTab: T?): Boolean =
        fromTab != null && toTab != null && fromTab != toTab
}
