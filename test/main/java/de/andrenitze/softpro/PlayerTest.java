package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Player;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class PlayerTest {
    @Test
    public void testPlayerFundsAfterCreation() {
        Player player = mock(Player.class);
        double additionalFunds = 10000;

        when(player.getFunds()).thenReturn((double) 50000);

        when(player.addFunds(additionalFunds)).thenReturn((double) 60000);
        assertEquals(60000, player.addFunds(additionalFunds), 0);

        when(player.addFunds(additionalFunds)).thenReturn((double) 60000);
        assertEquals(60000, player.addFunds(additionalFunds), 0);
    }

    @After
    public void validate() {
        validateMockitoUsage();
    }
}
