package de.andrenitze.softpro.config;

import de.andrenitze.softpro.services.impl.*;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;

@Configuration
public class GameConfig {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public Game game(
            StoryService storyService,
            SkillServiceImpl skillService,
            AccountingServiceImpl accountingService,
            TalentMarket talentMarket,
            GameEventPublisher eventPublisher,
            GameLifeCycleService gameLifeCycleService,
            DecisionDAO decisionDAO
    ) {
        // Manually create the beans to avoid circular dependencies
        GamePlayerServiceImpl playerService = new GamePlayerServiceImpl(talentMarket);
        MessagingServiceImpl messagingService = new MessagingServiceImpl(playerService);
        ProjectEmployeeMappingImpl projectEmployeeService = new ProjectEmployeeMappingImpl();
        ProjectServiceImpl projectService = new ProjectServiceImpl(accountingService, skillService, projectEmployeeService);
        EmployeeServiceImpl employeeService = new EmployeeServiceImpl(messagingService, projectEmployeeService);
        LevelConsequencesService levelConsequencesService = new LevelConsequencesService(playerService, talentMarket);
        ObjectiveServiceImpl objectiveService = new ObjectiveServiceImpl(playerService, gameLifeCycleService, projectService, skillService, projectEmployeeService);
        GameEventHandler eventHandler = new GameEventHandler(
                playerService,
                employeeService,
                talentMarket,
                projectService,
                messagingService,
                gameLifeCycleService,
                skillService,
                projectEmployeeService,
                accountingService
        );

        return new Game(
                storyService,
                playerService,
                skillService,
                accountingService,
                messagingService,
                talentMarket,
                projectService,
                employeeService,
                eventHandler,
                eventPublisher,
                projectEmployeeService,
                gameLifeCycleService,
                levelConsequencesService,
                objectiveService,
                decisionDAO
        );
    }

    // Global services within one game instance
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public StoryService storyService() {
        return new StoryService();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public TalentMarket talentMarket() {
        return new TalentMarket();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SkillServiceImpl skillService() {
        return new SkillServiceImpl();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public AccountingServiceImpl accountingService() {
        return new AccountingServiceImpl();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GameLifeCycleService gameLifeCycleService() {
        return new GameLifeCycleService();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GameEventPublisher eventPublisher() {
        return new GameEventPublisher();
    }
}