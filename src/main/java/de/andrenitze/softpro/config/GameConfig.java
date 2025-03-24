package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.events.GameEventHandler;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
    @Scope("prototype")
    public Game game(SkillServiceImpl skillService,
                     AccountingServiceImpl accountingService,
                     EmployeeServiceImpl employeeService,
                     ProjectServiceImpl projectService,
                     MessagingServiceImpl messagingService,
                     PlayerServiceImpl playerService,
                     GameEventHandler eventHandler) {
        Game game = new Game();
        game.setSkillService(skillService);
        game.setAccountingService(accountingService);
        game.setEmployeeService(employeeService);
        game.setProjectService(projectService);
        game.setMessagingService(messagingService);
        game.setPlayerService(playerService);
        game.setEventHandler(eventHandler);
        return game;
    }

    @Bean
    public EmployeeIdGenerator employeeIdGenerator() {
        return new EmployeeIdGenerator();
    }

    @Bean
    public TalentMarket talentMarket(EmployeeIdGenerator employeeIdGenerator) {
        TalentMarket market = new TalentMarket(employeeIdGenerator);
        market.clear();
        return market;
    }

    @Bean
    public SkillServiceImpl skillService() {
        return new SkillServiceImpl();
    }

    @Bean
    public AccountingServiceImpl accountingService() {
        return new AccountingServiceImpl();
    }

    @Bean
    public ProjectServiceImpl projectService(SkillServiceImpl skillService, AccountingServiceImpl accountingService) {
        // Pass the skillService directly instead of expecting to get it from game
        return new ProjectServiceImpl(null, skillService, accountingService);
    }

    @Bean
    public MessagingServiceImpl messagingService() {
        return new MessagingServiceImpl();
    }

    @Bean
    public EmployeeServiceImpl employeeService(ProjectServiceImpl projectService, MessagingServiceImpl messagingService) {
        EmployeeServiceImpl service = new EmployeeServiceImpl(projectService);
        service.setMessagingService(messagingService);
        return service;
    }

    @Bean
    public GameEventHandler eventHandler() {
        return new GameEventHandler(null); // Will be set after game creation
    }

    @Bean
    public BeanPostProcessor gameWiringPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof Game game) {
                    // Wire cyclic dependencies
                    ProjectServiceImpl projectService = game.getProjectService();
                    if (projectService != null) {
                        projectService.setGame(game);
                    }

                    GameEventHandler eventHandler = game.getEventHandler();
                    if (eventHandler != null) {
                        eventHandler.setGame(game);
                    }
                }
                return bean;
            }
        };
    }
}