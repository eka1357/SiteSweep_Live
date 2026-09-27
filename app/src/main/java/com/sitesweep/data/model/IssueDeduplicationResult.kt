package com.sitesweep.data.model

import com.sitesweep.data.local.entity.IssueEntity

/**
 * Result returned by the deterministic issue deduplication and candidate helper.
 */
sealed interface IssueDeduplicationResult {
    /**
     * A brand new Issue was created from the trigger capture.
     */
    data class Created(val issue: IssueEntity) : IssueDeduplicationResult

    /**
     * The capture occurred at an already tracked distress location;
     * it was linked to the existing active Issue instead of spawning a duplicate.
     */
    data class LinkedToExisting(val issue: IssueEntity, val wasAlreadyLatest: Boolean) : IssueDeduplicationResult

    /**
     * The candidate could not be promoted to an issue (e.g. invalid location).
     */
    data class Rejected(val reason: String) : IssueDeduplicationResult
}
