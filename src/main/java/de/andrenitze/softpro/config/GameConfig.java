package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.impl.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;

import static de.andrenitze.softpro.Main.logger;

@Configuration
@ComponentScan("de.andrenitze.softpro")
public class GameConfig {
    public GameConfig(ApplicationContext parentContext) {
        logger.debug("GameConfig created.");
    }

    @Bean
    @Scope("prototype")
    @DependsOn({"playerService", "eventHandler"})
    public Game game(SkillServiceImpl skillService,
                     AccountingServiceImpl accountingService,
                     EmployeeServiceImpl employeeService,
                     ProjectServiceImpl projectService,
                     MessagingServiceImpl messagingService,
                     PlayerServiceImpl playerService,
                     GameEventHandler eventHandler,
                     TalentMarket talentMarket,
                     @Qualifier("gameEventPublisher") GameEventPublisher eventPublisher) {
        Game game = new Game();
        game.setSkillService(skillService);
        game.setAccountingService(accountingService);
        game.setEmployeeService(employeeService);
        game.setProjectService(projectService);
        game.setMessagingService(messagingService);
        game.setPlayerService(playerService);
        game.setEventHandler(eventHandler);
        game.setTalentMarket(talentMarket);
        game.setEventPublisher(eventPublisher);
        eventHandler.setGame(game);
        return game;
    }

    @Bean
    @Scope("singleton")
    public EmployeeIdGenerator employeeIdGenerator() {
        return new EmployeeIdGenerator();
    }

    @Bean
    @Scope("singleton")
    public TalentMarket talentMarket(EmployeeIdGenerator employeeIdGenerator) {
        TalentMarket market = new TalentMarket(employeeIdGenerator);
        market.clear();
        return market;
    }

    @Bean
    @Scope("prototype")
    public SkillServiceImpl skillService() {
        return new SkillServiceImpl();
    }

    @Bean
    @Scope("prototype")
    public AccountingServiceImpl accountingService() {
        return new AccountingServiceImpl();
    }

    @Bean
    @Scope("prototype")
    public ProjectEmployeeMappingImpl projectEmployeeMapping() {
        return new ProjectEmployeeMappingImpl();
    }

    @Bean
    @Scope("prototype")
    @DependsOn({"messagingService", "skillService", "accountingService", "projectEmployeeMapping"})
    public ProjectServiceImpl projectService(MessagingServiceImpl messagingService,
                                             SkillServiceImpl skillService,
                                             AccountingServiceImpl accountingService,
                                             ProjectEmployeeMappingImpl projectEmployeeMapping,
                                             PlayerServiceImpl playerService
    ) {
        return new ProjectServiceImpl(messagingService, skillService, accountingService, projectEmployeeMapping, playerService);
    }

    @Bean
    @Scope("prototype")
    @Qualifier("playerServiceImpl")
    @DependsOn({"projectService"})
    public PlayerServiceImpl playerService(TalentMarket talentMarket,
                                           ProjectServiceImpl projectService,
                                           ProjectEmployeeMappingImpl projectEmployeeMappingImpl,
                                           MessagingServiceImpl messagingService
    ) {
        return new PlayerServiceImpl(talentMarket, projectService, projectEmployeeMappingImpl, messagingService);
    }

    @Bean
    @Scope("prototype")
    public EmployeeServiceImpl employeeService(MessagingServiceImpl messagingService,
                                               ProjectEmployeeMappingImpl projectEmployeeMappingImpl) {
        EmployeeServiceImpl service = new EmployeeServiceImpl(projectEmployeeMappingImpl);
        service.setMessagingService(messagingService);
        return service;
    }

    @Bean
    @Scope("prototype")
    public MessagingServiceImpl messagingService() {
        return new MessagingServiceImpl();
    }

    @Bean
    @Scope("prototype")
    public GameEventHandler eventHandler() {
        return new GameEventHandler(null); // Will be set after game creation
    }

    @Bean
    @Scope("prototype")
    public GameEventPublisher eventPublisher() {
        return new GameEventPublisher();
    }

    @Bean
    @Scope("prototype")
    public ObjectiveServiceImpl objectiveService(PlayerServiceImpl playerService) {
        return new ObjectiveServiceImpl(playerService);
    }
}