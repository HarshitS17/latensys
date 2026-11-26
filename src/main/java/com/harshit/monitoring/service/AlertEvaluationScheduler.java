package com.harshit.monitoring.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "alerts", name = "enabled", havingValue = "true")
public class AlertEvaluationScheduler {
    private final AlertEvaluator evaluator;

    public AlertEvaluationScheduler(AlertEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Scheduled(fixedDelayString = "${alerts.evaluation-delay-ms:60000}", initialDelayString = "${alerts.evaluation-delay-ms:60000}")
    public void evaluate() {
        evaluator.evaluate();
    }
}
