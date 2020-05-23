package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Game;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

class GameTest {

    @Mock
    Game game;

    @BeforeEach
    void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void testGameIsRunning() {
       verify(game);
    }

}
