package com.icaroerasmo.dashboard.service;

import java.util.Map;

final class LabelTranslator {

    private static final String DEFAULT_LABEL = "Detecção";
    private static final Map<String, String> LABELS = Map.of(
            "PERSON_DETECTED", "Pessoa detectada",
            "MOVEMENT_DETECTED", "Movimento detectado",
            "PET_DETECTED", "Animal detectado",
            "CAR_DETECTED", "Carro detectado"
    );

    private LabelTranslator() {
    }

    static String translate(String template, Object... args) {
        if (template == null || template.isBlank()) {
            return DEFAULT_LABEL;
        }
        return LABELS.getOrDefault(template, DEFAULT_LABEL);
    }
}