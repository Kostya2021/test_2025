package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Project;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ProjectTest {
    @Mock
    private Project project;

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void testProjectDomainGeneration() {
        when(project.getDomain()).thenReturn(anyString());
    }
}
