package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Project;
import org.mockito.Mock;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

public class ProjectTest {
    @Mock
    private Project projectMock;

    @BeforeTest
    public void setUp() {
        openMocks(this);
    }

    @Test
    public void testProjectDomainGeneration() {
        when(projectMock.getDomain()).thenReturn(null);
    }
}
