package com.icaroerasmo.dashboard.service;

import com.icaroerasmo.dashboard.config.DashboardProperties;
import com.icaroerasmo.dashboard.model.EnvVar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EnvService {

    private static final Pattern REF_PATTERN = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\\}$");
    private static final Pattern ENV_ENTRY_PATTERN = Pattern.compile("^      ([A-Za-z_][A-Za-z0-9_]*):\\s*(.*)$");
    private static final Pattern ENV_FILE_LINE_PATTERN = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");
    private static final String SECRET_MARKER = "# secret";

    private final DashboardProperties properties;

    public List<EnvVar> getEnvVars(String moduleName) {
        Path composeFile = Path.of(properties.getComposeFile());
        Map<String, EnvVar> envValues = readEnvFile(Path.of(properties.getEnvFile()));
        Map<String, String> serviceEnv = readServiceEnvironment(composeFile, moduleName);
        List<EnvVar> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : serviceEnv.entrySet()) {
            EnvVar envVar = parseEnvEntry(entry.getKey(), entry.getValue());
            if (envVar.getRef() != null) {
                EnvVar stored = envValues.get(envVar.getRef());
                envVar.setValue(stored != null ? stored.getValue() : envVar.getDefaultValue());
                envVar.setSecret(stored != null && stored.isSecret());
            }
            result.add(envVar);
        }
        return result;
    }

    public List<EnvVar> getGlobalEnvVars() {
        return new ArrayList<>(readEnvFile(Path.of(properties.getEnvFile())).values());
    }

    public void updateEnvVars(String moduleName, List<EnvVar> envVars) {
        updateFiles(moduleName, envVars);
        applyToContainer(moduleName);
    }

    public void updateGlobalEnvVars(List<EnvVar> envVars) {
        Path envFile = Path.of(properties.getEnvFile());
        Map<String, EnvVar> current = readEnvFile(envFile);

        Map<String, EnvVar> next = new LinkedHashMap<>();
        for (EnvVar envVar : envVars) {
            if (envVar.getKey() == null || envVar.getKey().isBlank()) {
                continue;
            }
            EnvVar stored = new EnvVar();
            stored.setKey(envVar.getKey());
            stored.setValue(envVar.getValue() != null ? envVar.getValue() : "");
            stored.setSecret(envVar.isSecret());
            next.put(stored.getKey(), stored);
        }

        Set<String> changedKeys = new HashSet<>();
        for (Map.Entry<String, EnvVar> entry : next.entrySet()) {
            EnvVar old = current.get(entry.getKey());
            if (old == null || !Objects.equals(old.getValue(), entry.getValue().getValue())) {
                changedKeys.add(entry.getKey());
            }
        }
        for (String key : current.keySet()) {
            if (!next.containsKey(key)) {
                changedKeys.add(key);
            }
        }

        writeEnvFile(envFile, next);

        for (String service : findServicesReferencing(changedKeys)) {
            applyToContainer(service);
        }
    }

    void updateFiles(String moduleName, List<EnvVar> envVars) {
        Path composeFile = Path.of(properties.getComposeFile());
        Path envFile = Path.of(properties.getEnvFile());
        Map<String, EnvVar> envValues = readEnvFile(envFile);
        List<String> composeLines = readLines(composeFile);

        Map<String, String> currentEnv = readServiceEnvironment(composeFile, moduleName);

        for (EnvVar envVar : envVars) {
            if (envVar.getKey() == null || envVar.getKey().isBlank()) {
                continue;
            }
            boolean isNew = !currentEnv.containsKey(envVar.getKey());
            if (isNew) {
                envVar.setRef(envVar.getKey());
                envVar.setDefaultValue(null);
                EnvVar stored = new EnvVar();
                stored.setKey(envVar.getKey());
                stored.setValue(envVar.getValue() != null ? envVar.getValue() : "");
                stored.setSecret(envVar.isSecret());
                envValues.put(envVar.getKey(), stored);
            } else if (envVar.getRef() != null && envVar.getValue() != null) {
                EnvVar stored = envValues.get(envVar.getRef());
                if (stored == null) {
                    stored = new EnvVar();
                    stored.setKey(envVar.getRef());
                    envValues.put(envVar.getRef(), stored);
                }
                stored.setValue(envVar.getValue());
                stored.setSecret(envVar.isSecret());
            }
        }

        writeEnvFile(envFile, envValues);
        writeLines(composeFile, updateComposeEnvironment(composeLines, moduleName, envVars));
    }

    void applyToContainer(String moduleName) {
        Path composeFile = Path.of(properties.getComposeFile());
        Path composeDir = composeFile.getParent();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    properties.getPodmanComposeBinary(), "-f", composeFile.toString(),
                    "up", "-d", "--force-recreate", moduleName);
            pb.directory(composeDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException("podman compose failed (exit " + exitCode + "): " + output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run podman compose: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running podman compose", e);
        }
    }

    private Set<String> findServicesReferencing(Set<String> keys) {
        if (keys.isEmpty()) {
            return Set.of();
        }
        Path composeFile = Path.of(properties.getComposeFile());
        List<String> lines = readLines(composeFile);
        Set<String> result = new HashSet<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.matches("^  [A-Za-z0-9_-]+:$")) {
                int serviceStart = i;
                int serviceEnd = findServiceEnd(lines, serviceStart);
                int envBlockStart = findEnvironmentBlockStart(lines, serviceStart, serviceEnd);
                if (envBlockStart >= 0) {
                    String serviceName = line.trim().substring(0, line.trim().length() - 1);
                    Map<String, String> env = readServiceEnvironment(lines, serviceStart, serviceEnd, envBlockStart);
                    for (String rawValue : env.values()) {
                        for (String key : keys) {
                            if (rawValue.contains("${" + key + "}") || rawValue.contains("${" + key + ":")) {
                                result.add(serviceName);
                                break;
                            }
                        }
                    }
                }
                i = serviceEnd + 1;
            } else {
                i++;
            }
        }
        return result;
    }

    private Map<String, String> readServiceEnvironment(Path composeFile, String moduleName) {
        List<String> lines = readLines(composeFile);
        int serviceStart = findServiceStart(lines, moduleName);
        if (serviceStart < 0) {
            throw new IllegalArgumentException("Service not found in compose file: " + moduleName);
        }
        int serviceEnd = findServiceEnd(lines, serviceStart);
        int envBlockStart = findEnvironmentBlockStart(lines, serviceStart, serviceEnd);
        return readServiceEnvironment(lines, serviceStart, serviceEnd, envBlockStart);
    }

    private Map<String, String> readServiceEnvironment(List<String> lines, int serviceStart, int serviceEnd, int envBlockStart) {
        Map<String, String> result = new LinkedHashMap<>();
        if (envBlockStart < 0) {
            return result;
        }
        for (int i = envBlockStart + 1; i < serviceEnd; i++) {
            Matcher matcher = ENV_ENTRY_PATTERN.matcher(lines.get(i));
            if (matcher.matches()) {
                result.put(matcher.group(1), matcher.group(2));
            } else if (!lines.get(i).isBlank() && !lines.get(i).startsWith("      ")) {
                break;
            }
        }
        return result;
    }

    private List<String> updateComposeEnvironment(List<String> lines, String moduleName, List<EnvVar> envVars) {
        int serviceStart = findServiceStart(lines, moduleName);
        if (serviceStart < 0) {
            throw new IllegalArgumentException("Service not found in compose file: " + moduleName);
        }
        int serviceEnd = findServiceEnd(lines, serviceStart);
        int envBlockStart = findEnvironmentBlockStart(lines, serviceStart, serviceEnd);

        List<String> newEntries = new ArrayList<>();
        for (EnvVar envVar : envVars) {
            if (envVar.getKey() == null || envVar.getKey().isBlank()) {
                continue;
            }
            newEntries.add("      " + envVar.getKey() + ": " + toComposeValue(envVar));
        }

        List<String> result = new ArrayList<>(lines);
        if (envBlockStart >= 0) {
            int envBlockEnd = envBlockStart + 1;
            while (envBlockEnd < result.size() && ENV_ENTRY_PATTERN.matcher(result.get(envBlockEnd)).matches()) {
                envBlockEnd++;
            }
            result.subList(envBlockStart + 1, envBlockEnd).clear();
            result.addAll(envBlockStart + 1, newEntries);
        } else {
            List<String> block = new ArrayList<>();
            block.add("    environment:");
            block.addAll(newEntries);
            result.addAll(serviceEnd + 1, block);
        }
        return result;
    }

    private String toComposeValue(EnvVar envVar) {
        if (envVar.getRef() != null) {
            if (envVar.getDefaultValue() != null) {
                return "${" + envVar.getRef() + ":-" + envVar.getDefaultValue() + "}";
            }
            return "${" + envVar.getRef() + "}";
        }
        return envVar.getValue() != null ? envVar.getValue() : "";
    }

    private EnvVar parseEnvEntry(String key, String rawValue) {
        EnvVar envVar = new EnvVar();
        envVar.setKey(key);
        Matcher matcher = REF_PATTERN.matcher(rawValue.trim());
        if (matcher.matches()) {
            envVar.setRef(matcher.group(1));
            envVar.setDefaultValue(matcher.group(2));
        } else {
            envVar.setValue(rawValue.trim());
        }
        return envVar;
    }

    private int findServiceStart(List<String> lines, String moduleName) {
        String target = "  " + moduleName + ":";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).equals(target)) {
                return i;
            }
        }
        return -1;
    }

    private int findServiceEnd(List<String> lines, int serviceStart) {
        for (int i = serviceStart + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && !line.startsWith(" ") && !line.startsWith("\t")) {
                return i - 1;
            }
            if (line.startsWith("  ") && !line.startsWith("    ") && !line.isBlank()) {
                return i - 1;
            }
        }
        return lines.size() - 1;
    }

    private int findEnvironmentBlockStart(List<String> lines, int serviceStart, int serviceEnd) {
        for (int i = serviceStart; i <= serviceEnd; i++) {
            if (lines.get(i).equals("    environment:")) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, EnvVar> readEnvFile(Path envFile) {
        Map<String, EnvVar> result = new LinkedHashMap<>();
        if (!Files.exists(envFile)) {
            return result;
        }
        boolean pendingSecret = false;
        for (String line : readLines(envFile)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) {
                if (trimmed.equals(SECRET_MARKER)) {
                    pendingSecret = true;
                }
                continue;
            }
            Matcher matcher = ENV_FILE_LINE_PATTERN.matcher(trimmed);
            if (matcher.matches()) {
                EnvVar envVar = new EnvVar();
                envVar.setKey(matcher.group(1));
                envVar.setValue(unquote(matcher.group(2)));
                envVar.setSecret(pendingSecret);
                result.put(envVar.getKey(), envVar);
            }
            pendingSecret = false;
        }
        return result;
    }

    private void writeEnvFile(Path envFile, Map<String, EnvVar> envValues) {
        List<String> lines = new ArrayList<>();
        for (EnvVar envVar : envValues.values()) {
            if (envVar.isSecret()) {
                lines.add(SECRET_MARKER);
            }
            lines.add(envVar.getKey() + "=" + quote(envVar.getValue()));
        }
        writeLines(envFile, lines);
    }

    private String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private String quote(String value) {
        if (value == null) {
            return "";
        }
        if (value.matches(".*[\\s#].*") || value.isEmpty()) {
            return "\"" + value + "\"";
        }
        return value;
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + path, e);
        }
    }

    private void writeLines(Path path, List<String> lines) {
        try {
            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write file: " + path, e);
        }
    }
}