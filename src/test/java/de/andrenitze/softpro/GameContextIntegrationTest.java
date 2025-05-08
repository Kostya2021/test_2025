package de.andrenitze.softpro;

import de.andrenitze.softpro.config.GameConfig;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertSame;

class GameContextIntegrationTest {

    @Test
    void testGameContextCreatesConsistentPlayerServiceInstance() {
        AnnotationConfigApplicationContext parentContext = new AnnotationConfigApplicationContext();
        parentContext.register(TestDaoConfig.class);
        parentContext.refresh();

        AnnotationConfigApplicationContext gameContext = new AnnotationConfigApplicationContext();
        gameContext.setParent(parentContext);
        gameContext.register(GameConfig.class);
        gameContext.refresh();

        Game game = gameContext.getBean(Game.class);

        GamePlayerServiceImpl ps1 = (GamePlayerServiceImpl) game.getPlayerService();
        GamePlayerServiceImpl ps2 = (GamePlayerServiceImpl) game.getMessagingService().getPlayerService();

        assertSame(ps1, ps2, "MessagingService should use the same GamePlayerServiceImpl instance as Game");

        gameContext.close();
        parentContext.close();
    }
}
