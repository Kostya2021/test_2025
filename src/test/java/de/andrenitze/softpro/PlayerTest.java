package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.players.Player;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class PlayerTest {
    @Test
    public void testPlayerFundsAfterCreation() {
        Player player = mock(Player.class);
        float additionalFunds = 10000;

        when(player.getFunds()).thenReturn((float) 100000);

        when(player.addFunds(additionalFunds)).thenReturn((float) 110000);
        assertEquals(110000, player.addFunds(additionalFunds), 0);

        when(player.addFunds(additionalFunds)).thenReturn((float) 110000);
        assertEquals(110000, player.addFunds(additionalFunds), 0);
    }

    @AfterTest
    public void validate() {
        validateMockitoUsage();
    }
}
