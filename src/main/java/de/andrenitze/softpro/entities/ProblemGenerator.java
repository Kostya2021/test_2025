package de.andrenitze.softpro.entities;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

import static de.andrenitze.softpro.GameServer.RANDOM;
import static de.andrenitze.softpro.Main.logger;

@Setter
@Getter
@NoArgsConstructor
public class ProblemGenerator {
    private static final String DEFAULT_FILE_PATH = "level-1-problems.yaml";
    private static final String FILE_PATH_TEMPLATE = "level-%d-problems.yaml";

    private List<Problem> problems;

    public void loadProblemsByLevel(int level) {
        String filePath = String.format(FILE_PATH_TEMPLATE, level);
        loadProblemsFromFile(filePath);
    }

    public void loadDefaultProblems() {
        loadProblemsFromFile(DEFAULT_FILE_PATH);
    }

    private void loadProblemsFromFile(String filePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath)) {
            if (inputStream == null) {
                throw new IOException("File not found: " + filePath);
            }
            InputStreamReader reader = new InputStreamReader(inputStream);
            Gson gson = new Gson();
            Type problemListType = new TypeToken<List<Problem>>() {}.getType();
            this.problems = gson.fromJson(convertYamlToJson(reader), problemListType);
            logger.debug("Loaded {} problems from file: {}", problems.size() , filePath);
        } catch (IOException e) {
            System.err.println("Error loading problems from file: " + e.getMessage());
            this.problems = List.of();
        }
    }


    private String convertYamlToJson(InputStreamReader reader) throws IOException {
        Yaml yaml = new Yaml();
        Object data = yaml.load(reader);
        Gson gson = new Gson();
        return gson.toJson(data);
    }

    public Problem generateRandomProblem() {
        if (problems == null || problems.isEmpty()) {
            throw new IllegalStateException("No problems available. Ensure the file is loaded correctly.");
        }
        return problems.get(RANDOM.nextInt(problems.size()));
    }
}