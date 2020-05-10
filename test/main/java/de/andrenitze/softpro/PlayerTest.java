package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Player;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlayerTest {
    @Test
    public void testPlayerFundsAfterCreation() {
        Player player = mock(Player.class);
        when(player.getFunds()).thenReturn(100000.0);
        when(player.addFunds(10000)).thenReturn(110000.0);
    }
}
