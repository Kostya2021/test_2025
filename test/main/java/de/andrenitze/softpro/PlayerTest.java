package main.java.de.andrenitze.softpro;

import de.andrenitze.softpro.Player;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class PlayerTest {
    @Test
    public void testPlayerFundsAfterCreation() {
        Player player = mock(Player.class);
        double additionalFunds = 10000;

        when(player.getFunds()).thenReturn((double) 100000);

        when(player.addFunds(additionalFunds)).thenReturn((double) 110000);
        assertEquals(110000, player.addFunds(additionalFunds), 0);

        when(player.addFunds(additionalFunds)).thenReturn((double) 110000);
        assertEquals(110000, player.addFunds(additionalFunds), 0);
    }

    @AfterTest
    public void validate() {
        validateMockitoUsage();
    }
}
