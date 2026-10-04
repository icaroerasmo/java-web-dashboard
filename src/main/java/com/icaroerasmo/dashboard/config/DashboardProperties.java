package com.icaroerasmo.dashboard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "dashboard")
public class DashboardProperties {

    private List<Module> modules = new ArrayList<>();
    private RabbitMq rabbitmq = new RabbitMq();
    private Go2Rtc go2rtc = new Go2Rtc();
    private Elasticsearch elasticsearch = new Elasticsearch();
    private Push push = new Push();
    private Recorder recorder = new Recorder();
    private String composeFile = "/cafofo/compose.yaml";
    private String envFile = "/cafofo/.env";
    private String podmanComposeBinary = "docker-compose";

    @Data
    public static class Module {
        private String name;
        private String baseUrl;
    }

    @Data
    public static class Push {
        private String publicKey;
        private String privateKey;
        private String subject = "mailto:cafofo@example.com";
    }

    @Data
    public static class RabbitMq {
        private String managementUrl;
        private String username;
        private String password;
    }

    @Data
    public static class Go2Rtc {
        private String baseUrl;
    }

    @Data
    public static class Elasticsearch {
        private String baseUrl = "http://elasticsearch:9200";
        private int ttlDays = 10;
    }

    @Data
    public static class Recorder {
        private String baseUrl = "http://java-rtsp-recorder:8080";
        private int syncIntervalMinutes = 10;
    }
}