package com.igrgin.speachapp.cleanup;

import java.util.List;

public record CleanupResult(
    String rawTranscript,
    String cleanedText,
    List<CleanupUncertainty> uncertainties
) {
}
