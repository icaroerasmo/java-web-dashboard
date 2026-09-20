package com.icaroerasmo.dashboard.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "dashboard")
public class DashboardProperties {

    private List<Module> modules = new ArrayList<>();
    private RabbitMq rabbitmq = new RabbitMq();
    private Go2Rtc go2rtc = new Go2Rtc();

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules;
    }

    public RabbitMq getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(RabbitMq rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public Go2Rtc getGo2rtc() {
        return go2rtc;
    }

    public void setGo2rtc(Go2Rtc go2rtc) {
        this.go2rtc = go2rtc;
    }

    public static class Module {
        private String name;
        private String baseUrl;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class RabbitMq {
        private String managementUrl;
        private String username;
        private String password;

        public String getManagementUrl() {
            return managementUrl;
        }

        public void setManagementUrl(String managementUrl) {
            this.managementUrl = managementUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class Go2Rtc {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}