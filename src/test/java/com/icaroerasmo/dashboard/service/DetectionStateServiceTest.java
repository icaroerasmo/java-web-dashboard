package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.messaging.DetectionEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DetectionStateServiceTest {

    private final DetectionStateService service = new DetectionStateService();

    @Test
    void deveResolverLabelPorTemplate() {
        service.update(new DetectionEvent("e1", "garagem1", "PERSON_DETECTED", List.of()));
        service.update(new DetectionEvent("e2", "garagem2", "MOVEMENT_DETECTED", List.of()));
        service.update(new DetectionEvent("e3", "garagem3", "PET_DETECTED", List.of()));
        service.update(new DetectionEvent("e4", "area_de_servico", "CAR_DETECTED", List.of()));

        var detections = service.activeDetections();
        assertEquals(4, detections.size());
        assertEquals("Pessoa detectada", detections.get("garagem1"));
        assertEquals("Movimento detectado", detections.get("garagem2"));
        assertEquals("Animal detectado", detections.get("garagem3"));
        assertEquals("Carro detectado", detections.get("area_de_servico"));
    }

    @Test
    void deveUsarLabelPadraoParaTemplateDesconhecido() {
        service.update(new DetectionEvent("e1", "camera1", "UNKNOWN_TEMPLATE", List.of()));
        assertEquals("Detecção", service.activeDetections().get("camera1"));
    }

    @Test
    void deveIgnorarEventoInvalido() {
        service.update(new DetectionEvent("e1", "", "PERSON_DETECTED", List.of()));
        assertTrue(service.activeDetections().isEmpty());
    }

    @Test
    void deveExpirarAposTTL() throws Exception {
        service.update(new DetectionEvent("e1", "garagem1", "PERSON_DETECTED", List.of()));
        assertTrue(service.isActive("garagem1"));
        Thread.sleep(5_100);
        assertTrue(service.activeDetections().isEmpty());
        assertTrue(!service.isActive("garagem1"));
    }
}