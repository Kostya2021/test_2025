package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.events.GameEventHandler;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;

import static de.andrenitze.softpro.Main.logger;

// TODO POSSIBLE CONCURRENCY PROBLEMS: Check for all beans if they are in the right (game-specific) context
//  or can be moved to the global context (Skill file management, Messaging??? etc.)
@Configuration
@ComponentScan("de.andrenitze.softpro")
public class GameConfig {
    public GameConfig(ApplicationContext parentContext) {
        logger.debug("GameConfig created.");
    }

    @Bean
    public Game game(SkillServiceImpl skillService) {
        Game game = new Game();
        game.setSkillService(skillService);
        // weitere Setter
        return game;
    }

    @Bean
    public EmployeeIdGenerator employeeIdGenerator() {
        return new EmployeeIdGenerator();
    }

    @Bean
    public TalentMarket talentMarket(EmployeeIdGenerator employeeIdGenerator, Game game) {
        TalentMarket market = new TalentMarket(employeeIdGenerator);
        market.clear();
        game.setTalentMarket(market);
        return market;
    }

    @Bean
    public SkillServiceImpl skillService() {
        return new SkillServiceImpl();
    }

    @Bean
    public AccountingServiceImpl accountingService(Game game) {
        AccountingServiceImpl service = new AccountingServiceImpl(game);
        game.setAccountingService(service);
        return service;
    }

    @Bean
    public ProjectServiceImpl projectService(Game game) {
        ProjectServiceImpl service = new ProjectServiceImpl(game);
        game.setProjectService(service);
        return service;
    }

    @Bean
    public MessagingServiceImpl messagingService(Game game) {
        MessagingServiceImpl service = new MessagingServiceImpl();
        game.setMessagingService(service);
        return service;
    }

    @Bean
    public PlayerServiceImpl playerService(Game game, TalentMarket talentMarket,
                                           ProjectServiceImpl projectService) {
        PlayerServiceImpl service = new PlayerServiceImpl(game, talentMarket, projectService);
        game.setPlayerService(service);
        return service;
    }

    @Bean
    public EmployeeServiceImpl employeeService(Game game, ProjectServiceImpl projectService,
                                               MessagingServiceImpl messagingService) {
        EmployeeServiceImpl service = new EmployeeServiceImpl(game, projectService);
        service.setMessagingService(messagingService);
        game.setEmployeeService(service);
        return service;
    }

    /*
    @Bean
    public GameEventPublisher gameEventPublisher() {
        return new GameEventPublisher();
    }

     */

    @Bean
    public GameEventHandler eventHandler(Game game) {
        GameEventHandler handler = new GameEventHandler(game);
        game.setEventHandler(handler);
        return handler;
    }
}