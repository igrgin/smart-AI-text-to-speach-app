package com.igrgin.speachapp.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.dictation")
public record DictationProperties(
    long maxAudioBytes,
    double maxDurationSeconds,
    List<String> supportedAudioTypes
) {
}
