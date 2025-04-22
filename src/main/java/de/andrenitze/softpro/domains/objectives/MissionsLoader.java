package de.andrenitze.softpro.domains.objectives;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class MissionsLoader {
    private static final Logger log = LoggerFactory.getLogger(MissionsLoader.class);
    private MissionsLoader() {
        // Private constructor to hide the implicit public one
    }

    public static List<Mission> loadMissionsFromYamlFile(String fileName) {
        log.info("Loading missions from YAML file: {}", fileName);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream input = MissionsLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                log.error("File not found: {}", fileName);
                throw new IOException("File not found: " + fileName);
            }
            MissionsWrapper wrapper = mapper.readValue(input, MissionsWrapper.class);
            log.info("Successfully loaded {} missions from file: {}", wrapper.getMissions().size(), fileName);
            return wrapper.getMissions();
        } catch (IOException e) {
            log.error("Failed to load missions from YAML file: {}", e.getMessage());
            return List.of();
        }
    }

    @Getter @Setter
    private static class MissionsWrapper {
        private List<Mission> missions;
    }
}