package com.sitesweep.data.model

import java.util.Locale

/**
 * Closed-loop Issue Lifecycle states:
 * OPEN -> ASSIGNED -> IN_REPAIR -> REINSPECTION -> RESOLVED
 *
 * Designed for offline-first structural inspection and maintenance management.
 */
enum class IssueStatus(val displayName: String) {
    OPEN("Open"),
    ASSIGNED("Assigned"),
    IN_REPAIR("In Repair"),
    REINSPECTION("Awaiting Reinspection"),
    RESOLVED("Resolved");

    companion object {
        fun fromString(value: String): IssueStatus {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OPEN
        }

        /**
         * Validates allowed transitions in the closed-loop lifecycle:
         * - OPEN -> ASSIGNED
         * - OPEN -> IN_REPAIR
         * - ASSIGNED -> IN_REPAIR
         * - IN_REPAIR -> REINSPECTION
         * - REINSPECTION -> RESOLVED
         * - REINSPECTION -> IN_REPAIR (re-repair required if distress persists)
         *
         * Also permits sensible operational corrections:
         * - ASSIGNED -> OPEN (unassign)
         * - IN_REPAIR -> ASSIGNED (reassign during work)
         *
         * Invalid transitions (e.g., direct OPEN -> RESOLVED, or RESOLVED -> IN_REPAIR) return false.
         */
        fun canTransition(from: IssueStatus, to: IssueStatus): Boolean {
            if (from == to) return true
            return when (from) {
                OPEN -> to == ASSIGNED || to == IN_REPAIR
                ASSIGNED -> to == IN_REPAIR || to == OPEN
                IN_REPAIR -> to == REINSPECTION || to == ASSIGNED
                REINSPECTION -> to == RESOLVED || to == IN_REPAIR
                RESOLVED -> false // Requires explicit reopen
            }
        }

        /**
         * Validates if an issue can be explicitly reopened.
         * Only RESOLVED issues can be reopened back to OPEN.
         */
        fun canReopen(from: IssueStatus): Boolean = from == RESOLVED
    }
}

/**
 * Action operations available in the Engineer Dashboard / Issue Detail lifecycle.
 */
enum class IssueAction {
    ASSIGN,
    START_REPAIR,
    SCHEDULE_REINSPECTION,
    START_REINSPECTION,
    MARK_RESOLVED,
    RETURN_TO_REPAIR,
    REOPEN
}

/**
 * Maps each IssueStatus to its strictly permitted user actions as defined in Section 3:
 * - OPEN: [Assign], [Start Repair]
 * - ASSIGNED: [Start Repair]
 * - IN_REPAIR: [Schedule Reinspection]
 * - REINSPECTION: [Start Reinspection], [Mark Resolved], [Return to Repair]
 * - RESOLVED: [Reopen Issue]
 */
fun IssueStatus.allowedActions(): Set<IssueAction> = when (this) {
    IssueStatus.OPEN -> setOf(IssueAction.ASSIGN, IssueAction.START_REPAIR)
    IssueStatus.ASSIGNED -> setOf(IssueAction.START_REPAIR)
    IssueStatus.IN_REPAIR -> setOf(IssueAction.SCHEDULE_REINSPECTION)
    IssueStatus.REINSPECTION -> setOf(
        IssueAction.START_REINSPECTION,
        IssueAction.MARK_RESOLVED,
        IssueAction.RETURN_TO_REPAIR
    )
    IssueStatus.RESOLVED -> setOf(IssueAction.REOPEN)
}
