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
    private String composeFile = "/cafofo/compose.yaml";
    private String envFile = "/cafofo/.env";
    private String podmanComposeBinary = "podman-compose";

    @Data
    public static class Module {
        private String name;
        private String baseUrl;
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
}