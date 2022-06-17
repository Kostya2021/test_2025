package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Project;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ProjectTest {
    @Mock
    private Project project;

    @BeforeTest
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testProjectDomainGeneration() {
        when(project.getDomain()).thenReturn(anyString());
    }
}
