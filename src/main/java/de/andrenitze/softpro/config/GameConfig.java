package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.*;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;

import static de.andrenitze.softpro.Main.logger;

@Configuration
@ComponentScan("de.andrenitze.softpro")
public class GameConfig {
    public GameConfig(ApplicationContext parentContext) {
        logger.debug("GameConfig with parentContext '{}' created.", parentContext.getId());
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
                     LevelConsequencesService levelConsequencesService,
                     GameLifeCycleService lifeCycleService,
                     ProjectEmployeeMappingImpl projectEmployeeMapping) {
        return new Game(skillService, accountingService, messagingService, playerService, talentMarket,
                projectService, employeeService, eventHandler, eventPublisher,
                levelConsequencesService, lifeCycleService, projectEmployeeMapping);
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
    public AccountingServiceImpl accountingService(@Lazy MessagingService messagingService, @Lazy LobbyPlayerService playerService) {
        return new AccountingServiceImpl(messagingService, playerService);
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
                                             @Lazy GamePlayerServiceImpl playerService) {
        return new ProjectServiceImpl(accountingService, skillService, projectEmployeeMapping, messagingService, playerService);
    }

    @Bean
    @Scope("prototype")
    @Primary
    public GamePlayerServiceImpl playerService(TalentMarket talentMarket) {
        return new GamePlayerServiceImpl(talentMarket);
    }

    @Bean
    @Scope("prototype")
    public EmployeeServiceImpl employeeService(MessagingServiceImpl messagingService,
                                               ProjectEmployeeMappingImpl projectEmployeeMappingImpl,
                                               GamePlayerServiceImpl playerService){
        EmployeeServiceImpl service = new EmployeeServiceImpl(projectEmployeeMappingImpl, playerService);
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
    public GameEventHandler eventHandler(MessagingService messagingService,
                                         GamePlayerServiceImpl playerService,
                                         EmployeeService employeeService,
                                         TalentMarket talentMarket,
                                         ProjectService projectService,
                                         GameLifeCycleService lifeCycleService,
                                         SkillServiceImpl skillService,
                                         ProjectEmployeeMappingService projectEmployeeService) {
        return new GameEventHandler(messagingService,
                playerService,
                employeeService,
                talentMarket,
                projectService,
                lifeCycleService,
                skillService,
                projectEmployeeService);
    }

    @Bean
    @Scope("prototype")
    public GameEventPublisher eventPublisher() {
        return new GameEventPublisher();
    }

    @Bean
    @Scope("prototype")
    public ObjectiveServiceImpl objectiveService(@Lazy GamePlayerServiceImpl playerService,
                                                 GameLifeCycleService lifeCycleService,
                                                 ProjectServiceImpl projectService,
                                                 SkillServiceImpl skillService,
                                                 ProjectEmployeeMappingService projectEmployeeService) {
        return new ObjectiveServiceImpl(playerService, lifeCycleService, projectService, skillService, projectEmployeeService);
    }

    @Bean
    @Scope("prototype")
    public LevelConsequencesService levelConsequencesService(
            @Lazy GamePlayerServiceImpl playerService,
            @Lazy TalentMarket talentMarket) {
        return new LevelConsequencesService(playerService, talentMarket);
    }

    @Bean
    @Scope("prototype")
    public GameLifeCycleService lifeCycleService() {
        return new GameLifeCycleService();
    }
}