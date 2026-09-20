package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.model.EnvVar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvServiceTest {

    @TempDir
    Path tempDir;

    private Path composeFile;
    private Path envFile;
    private DashboardProperties properties;
    private EnvService envService;

    @BeforeEach
    void setUp() throws Exception {
        composeFile = tempDir.resolve("compose.yaml");
        envFile = tempDir.resolve(".env");
        Files.writeString(composeFile, COMPOSE_YAML);
        Files.writeString(envFile, ENV_FILE);
        properties = new DashboardProperties();
        properties.setComposeFile(composeFile.toString());
        properties.setEnvFile(envFile.toString());
        envService = new EnvService(properties);
    }

    @Test
    void getEnvVars_resolvesRefsAndLiterals() {
        List<EnvVar> envVars = envService.getEnvVars("java-telegram-notifier");
        assertEquals(4, envVars.size());

        EnvVar tz = envVars.get(0);
        assertEquals("TZ", tz.getKey());
        assertEquals("America/Bahia", tz.getValue());
        assertNull(tz.getRef());

        EnvVar chatId = envVars.get(2);
        assertEquals("TELEGRAM_CHAT_ID", chatId.getKey());
        assertEquals("12345", chatId.getValue());
        assertEquals("TELEGRAM_CHAT_ID", chatId.getRef());
        assertNull(chatId.getDefaultValue());

        EnvVar host = envVars.get(3);
        assertEquals("RABBITMQ_HOST", host.getKey());
        assertEquals("rabbitmq", host.getValue());
        assertEquals("RABBITMQ_HOST", host.getRef());
        assertEquals("rabbitmq", host.getDefaultValue());
    }

    @Test
    void updateFiles_editsRefValueInEnvFile() throws Exception {
        List<EnvVar> envVars = envService.getEnvVars("java-telegram-notifier");
        envVars.get(3).setValue("rabbitmq2");
        envService.updateFiles("java-telegram-notifier", envVars);

        String envContent = Files.readString(envFile);
        assertTrue(envContent.contains("RABBITMQ_HOST=rabbitmq2"));

        String composeContent = Files.readString(composeFile);
        assertTrue(composeContent.contains("RABBITMQ_HOST: ${RABBITMQ_HOST:-rabbitmq}"));
    }

    @Test
    void updateFiles_editsLiteralValue() throws Exception {
        List<EnvVar> envVars = envService.getEnvVars("java-telegram-notifier");
        envVars.get(0).setValue("America/Sao_Paulo");
        envService.updateFiles("java-telegram-notifier", envVars);

        String composeContent = Files.readString(composeFile);
        assertTrue(composeContent.contains("TZ: America/Sao_Paulo"));
    }

    @Test
    void updateFiles_addsNewVarAsRef() throws Exception {
        List<EnvVar> envVars = envService.getEnvVars("java-telegram-notifier");
        EnvVar newVar = new EnvVar();
        newVar.setKey("FOO");
        newVar.setValue("bar");
        envVars.add(newVar);
        envService.updateFiles("java-telegram-notifier", envVars);

        String composeContent = Files.readString(composeFile);
        assertTrue(composeContent.contains("      FOO: ${FOO}"));

        String envContent = Files.readString(envFile);
        assertTrue(envContent.contains("FOO=bar"));
    }

    @Test
    void updateFiles_removesVar() throws Exception {
        List<EnvVar> envVars = envService.getEnvVars("java-telegram-notifier");
        envVars.removeIf(v -> v.getKey().equals("LANGUAGE"));
        envService.updateFiles("java-telegram-notifier", envVars);

        String composeContent = Files.readString(composeFile);
        assertFalse(composeContent.contains("LANGUAGE: pt_BR"));
    }

    @Test
    void updateFiles_addsEnvironmentBlockWhenMissing() throws Exception {
        List<EnvVar> envVars = envService.getEnvVars("java-rtsp-recorder");
        EnvVar newVar = new EnvVar();
        newVar.setKey("TZ");
        newVar.setValue("America/Bahia");
        envVars.add(newVar);
        envService.updateFiles("java-rtsp-recorder", envVars);

        String composeContent = Files.readString(composeFile);
        assertTrue(composeContent.contains("    environment:"));
        assertTrue(composeContent.contains("      TZ: ${TZ}"));
    }

    @Test
    void applyToContainer_runsPodmanCompose() throws Exception {
        Path fakeBin = tempDir.resolve("fakebin");
        Files.createDirectories(fakeBin);
        Path argsFile = tempDir.resolve("args.txt");
        Path fakeScript = fakeBin.resolve("podman-compose");
        Files.writeString(fakeScript, "#!/bin/bash\necho \"$@\" > " + argsFile + "\nexit 0\n");
        fakeScript.toFile().setExecutable(true);
        properties.setPodmanComposeBinary(fakeScript.toString());

        envService.applyToContainer("java-telegram-notifier");

        String args = Files.readString(argsFile);
        assertTrue(args.contains("-f"));
        assertTrue(args.contains(composeFile.toString()));
        assertTrue(args.contains("--force-recreate"));
        assertTrue(args.contains("java-telegram-notifier"));
    }

    @Test
    void getEnvVars_unknownServiceThrows() {
        assertThrows(IllegalArgumentException.class, () -> envService.getEnvVars("unknown-service"));
    }

    @Test
    void getGlobalEnvVars_returnsAllVarsWithSecrets() throws Exception {
        Files.writeString(envFile, """
                TELEGRAM_CHAT_ID=12345
                # secret
                TELEGRAM_BOT_TOKEN=abc123
                RABBITMQ_HOST=rabbitmq
                """);
        List<EnvVar> envVars = envService.getGlobalEnvVars();
        assertEquals(3, envVars.size());

        EnvVar chatId = envVars.get(0);
        assertEquals("TELEGRAM_CHAT_ID", chatId.getKey());
        assertEquals("12345", chatId.getValue());
        assertFalse(chatId.isSecret());

        EnvVar token = envVars.get(1);
        assertEquals("TELEGRAM_BOT_TOKEN", token.getKey());
        assertEquals("abc123", token.getValue());
        assertTrue(token.isSecret());

        EnvVar host = envVars.get(2);
        assertEquals("RABBITMQ_HOST", host.getKey());
        assertFalse(host.isSecret());
    }

    @Test
    void updateGlobalEnvVars_writesEnvAndRecreatesAffectedServices() throws Exception {
        Path fakeBin = tempDir.resolve("fakebin");
        Files.createDirectories(fakeBin);
        Path argsFile = tempDir.resolve("args.txt");
        Path fakeScript = fakeBin.resolve("docker-compose");
        Files.writeString(fakeScript, "#!/bin/bash\necho \"$@\" >> " + argsFile + "\nexit 0\n");
        fakeScript.toFile().setExecutable(true);
        properties.setPodmanComposeBinary(fakeScript.toString());

        List<EnvVar> envVars = envService.getGlobalEnvVars();
        envVars.get(0).setValue("99999");
        envVars.get(1).setValue("rabbitmq2");
        envVars.get(2).setValue("OPENCL");

        envService.updateGlobalEnvVars(envVars);

        String args = Files.readString(argsFile);
        assertTrue(args.contains("java-telegram-notifier"));
        assertTrue(args.contains("java-object-detection"));
        assertFalse(args.contains("java-rtsp-recorder"));

        String envContent = Files.readString(envFile);
        assertTrue(envContent.contains("TELEGRAM_CHAT_ID=99999"));
        assertTrue(envContent.contains("RABBITMQ_HOST=rabbitmq2"));
        assertTrue(envContent.contains("ACCELERATION_BACKEND=OPENCL"));
    }

    @Test
    void updateGlobalEnvVars_secretToggleDoesNotRecreate() throws Exception {
        Path fakeBin = tempDir.resolve("fakebin");
        Files.createDirectories(fakeBin);
        Path argsFile = tempDir.resolve("args.txt");
        Path fakeScript = fakeBin.resolve("docker-compose");
        Files.writeString(fakeScript, "#!/bin/bash\necho \"$@\" >> " + argsFile + "\nexit 0\n");
        fakeScript.toFile().setExecutable(true);
        properties.setPodmanComposeBinary(fakeScript.toString());

        List<EnvVar> envVars = envService.getGlobalEnvVars();
        envVars.get(0).setSecret(true);

        envService.updateGlobalEnvVars(envVars);

        String args = Files.exists(argsFile) ? Files.readString(argsFile) : "";
        assertTrue(args.isEmpty());

        String envContent = Files.readString(envFile);
        assertTrue(envContent.contains("# secret"));
        assertTrue(envContent.contains("TELEGRAM_CHAT_ID=12345"));
    }

    private static final String COMPOSE_YAML = """
            version: "3.9"

            services:
              java-telegram-notifier:
                image: ghcr.io/icaroerasmo/java-telegram-notifier:latest
                container_name: java-telegram-notifier
                restart: unless-stopped
                environment:
                  TZ: America/Bahia
                  LANGUAGE: pt_BR
                  TELEGRAM_CHAT_ID: ${TELEGRAM_CHAT_ID}
                  RABBITMQ_HOST: ${RABBITMQ_HOST:-rabbitmq}
                volumes:
                  - ./java-telegram-notifier/config:/app/config

              java-object-detection:
                image: ghcr.io/icaroerasmo/java-object-detection:latest
                container_name: java-object-detection
                restart: unless-stopped
                environment:
                  TZ: America/Bahia
                  OBJECT_DETECTION_ACCELERATION_BACKEND: ${ACCELERATION_BACKEND:-AUTO}

              java-rtsp-recorder:
                image: ghcr.io/icaroerasmo/java-rtsp-recorder:latest
                container_name: java-rtsp-recorder
                restart: unless-stopped
                volumes:
                  - ./java-rtsp-recorder/config:/app/config
            """;

    private static final String ENV_FILE = """
            TELEGRAM_CHAT_ID=12345
            RABBITMQ_HOST=rabbitmq
            ACCELERATION_BACKEND=CUDA
            """;
}