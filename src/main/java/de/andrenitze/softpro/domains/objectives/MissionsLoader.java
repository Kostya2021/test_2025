package de.andrenitze.softpro.domains.objectives;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static de.andrenitze.softpro.Main.logger;

public class MissionsLoader {
    public static List<Mission> loadMissionsFromYamlFile(String fileName) {
        logger.info("Loading missions from YAML file: {}", fileName);
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream input = MissionsLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                logger.error("File not found: {}", fileName);
                throw new IOException("File not found: " + fileName);
            }
            MissionsWrapper wrapper = mapper.readValue(input, MissionsWrapper.class);
            logger.info("Successfully loaded {} missions from file: {}", wrapper.getMissions().size(), fileName);
            return wrapper.getMissions();
        } catch (IOException e) {
            logger.error("Failed to load missions from YAML file", e);
            throw new RuntimeException("Failed to load missions from YAML file", e);
        }
    }

    @Getter @Setter
    private static class MissionsWrapper {
        private List<Mission> missions;
    }
}