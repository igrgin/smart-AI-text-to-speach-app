package com.igrgin.speachapp.cleanup;

public record CleanupUncertainty(
    String text,
    UncertaintyReasonCategory reasonCategory,
    String reason
) {
}
