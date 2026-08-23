package com.example.newsmaterial.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.analysis.scheduling-enabled", havingValue = "true")
public class AnalysisSchedulingConfiguration {
}
