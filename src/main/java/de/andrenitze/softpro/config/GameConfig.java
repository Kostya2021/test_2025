package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
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
    public Game game(SkillServiceImpl skillService,
                     AccountingServiceImpl accountingService,
                     EmployeeServiceImpl employeeService,
                     ProjectServiceImpl projectService,
                     MessagingServiceImpl messagingService,
                     GamePlayerServiceImpl playerService,
                     GameEventHandler eventHandler,
                     TalentMarket talentMarket,
                     GameEventPublisher eventPublisher,
                     ObjectiveServiceImpl objectiveService,
                     LevelConsequencesService levelConsequencesService) {
        return new Game(skillService, accountingService, messagingService, playerService, talentMarket,
                projectService, employeeService, eventHandler, eventPublisher, objectiveService,
                levelConsequencesService);
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
    public ProjectServiceImpl projectService(MessagingServiceImpl messagingService,
                                             SkillServiceImpl skillService,
                                             AccountingServiceImpl accountingService,
                                             ProjectEmployeeMappingImpl projectEmployeeMapping,
                                             GamePlayerServiceImpl playerService
    ) {
        return new ProjectServiceImpl(messagingService, skillService, accountingService, projectEmployeeMapping, playerService);
    }

    @Bean
    @Scope("prototype")
    @Primary
    public GamePlayerServiceImpl playerService(TalentMarket talentMarket,
                                               ProjectServiceImpl projectService,
                                               ProjectEmployeeMappingImpl projectEmployeeMappingImpl,
                                               MessagingServiceImpl messagingService
    ) {
        return new GamePlayerServiceImpl(talentMarket, projectService, projectEmployeeMappingImpl, messagingService);
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
    public MessagingServiceImpl messagingService(@Lazy GamePlayerServiceImpl playerService) {
        return new MessagingServiceImpl(playerService);
    }

    @Bean
    @Scope("prototype")
    public GameEventHandler eventHandler(GamePlayerServiceImpl playerService) {
        return new GameEventHandler(null, playerService); // Will be set after game creation
    }

    @Bean
    @Scope("prototype")
    public GameEventPublisher eventPublisher() {
        return new GameEventPublisher();
    }

    @Bean
    @Scope("prototype")
    public ObjectiveServiceImpl objectiveService(@Lazy GamePlayerServiceImpl playerService) {
        return new ObjectiveServiceImpl(playerService);
    }

    @Bean
    @Scope("prototype")
    public LevelConsequencesService levelConsequencesService(@Lazy GamePlayerServiceImpl playerService, @Lazy TalentMarket talentMarket) {
        return new LevelConsequencesService(playerService, talentMarket);
    }
}