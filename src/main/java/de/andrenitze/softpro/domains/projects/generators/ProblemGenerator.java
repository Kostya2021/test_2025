package de.andrenitze.softpro.domains.projects.generators;

import com.google.gson.reflect.TypeToken;
import de.andrenitze.softpro.domains.projects.Problem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import de.andrenitze.softpro.GameServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.List;

import static de.andrenitze.softpro.GameServer.RANDOM;

@Setter
@Getter
@NoArgsConstructor
@Slf4j
public class ProblemGenerator {
    private static final String DEFAULT_FILE_PATH = "level-1-problems.yaml";
    private static final String FILE_PATH_TEMPLATE = "level-%d-problems.yaml";

    private List<Problem> problems;

    public void loadProblemsByLevel(int level) {
        String filePath = String.format(FILE_PATH_TEMPLATE, level);
        if (!loadProblemsFromFile(filePath)) {
            log.warn("File {} not found. Falling back to default problems file: {}", filePath, DEFAULT_FILE_PATH);
            loadProblemsFromFile(DEFAULT_FILE_PATH); // fallback to default file
        }
    }

    private boolean loadProblemsFromFile(String filePath) {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filePath)) {
            if (inputStream == null) {
                throw new IOException("File not found: " + filePath);
            }
            InputStreamReader reader = new InputStreamReader(inputStream);
            Type problemListType = new TypeToken<List<Problem>>() {}.getType();
            this.problems = GameServer.getGson().fromJson(convertYamlToJson(reader), problemListType);
            log.debug("Loaded {} problems from file: {}", problems.size(), filePath);
            return true;
        } catch (IOException e) {
            log.error("Error loading problems from file: {}. {}", filePath, e.getMessage());
            this.problems = List.of();
            return false;
        }
    }

    private String convertYamlToJson(InputStreamReader reader) throws IOException {
        try {
            Yaml yaml = new Yaml();
            Object data = yaml.load(reader);
            return GameServer.getGson().toJson(data);
        } catch (Exception e) {
            throw new IOException("Failed to open YAML file", e);
        }
    }

    public Problem generateRandomProblem() {
        if (problems == null || problems.isEmpty()) {
            throw new IllegalStateException("No problems available. Ensure the file is loaded correctly.");
        }
        Problem problem = problems.get(RANDOM.nextInt(problems.size()));
        problem.setSolvedAt(0);
        return problem;
    }

    public Problem generateRandomNewProblem(List<Problem> occurredProblems) {
        if (problems == null || problems.isEmpty()) {
            throw new IllegalStateException("No problems available. Ensure the file is loaded correctly.");
        }

        // If occurredProblems equals the problems (= all problems have already occurred), don't return a problem
        if (occurredProblems.size() == problems.size()) {
            return null;
        }

        // If the provided list is empty (i.e. no problems occurred yet), return a random problem
        if (occurredProblems.isEmpty()) {
            return generateRandomProblem();
        }

        // Else, return a random problem that has not occurred yet (= a random new problem)
        Problem problem = problems.get(RANDOM.nextInt(problems.size()));
        while (occurredProblems.contains(problem)) {
            problem = problems.get(RANDOM.nextInt(problems.size()));
        }

        problem.setSolvedAt(0);
        return problem;
    }
}