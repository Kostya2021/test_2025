package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.domains.decisions.DecisionDAO;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.events.GameEventForwarder;
import de.andrenitze.softpro.events.GameEventHandler;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

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
            DecisionDAO decisionDAO,
            StatusEffectService statusEffectService //добавил сюда
    ) {
        // Manually create the beans to avoid circular dependencies
        GamePlayerServiceImpl playerService = new GamePlayerServiceImpl(talentMarket);
        MessagingServiceImpl messagingService = new MessagingServiceImpl(playerService);
        ProjectEmployeeMappingImpl projectEmployeeService = new ProjectEmployeeMappingImpl();
        EmployeeServiceImpl employeeService = new EmployeeServiceImpl(messagingService, projectEmployeeService); //поменял местами с нижним и конструктор у нижнего сервиса поменял - ProjectServiceImpl
        ProjectServiceImpl projectService = new ProjectServiceImpl(accountingService, skillService, projectEmployeeService, employeeService);
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
                accountingService,
                statusEffectService//добавил сюда
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
                decisionDAO,
                statusEffectService //добавил сюда
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

    //добавил параметр этому бину - StatusEffectService
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public SkillServiceImpl skillService(StatusEffectService statusEffectService) {
        return new SkillServiceImpl(statusEffectService);
    }

    //добавил бин
    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public StatusEffectService statusEffectService() {
        return new StatusEffectService();
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
    // ⚠️ This bean is currently unused and inactive.
    // The Game class uses a GameEventPublisher from the parent (global) context,
    // so events published by Game go directly to the global context (e.g., GameServer),
    // and are NOT visible to this GameEventForwarder, which resides in the local game context
    public GameEventForwarder gameEventForwarder(ApplicationEventPublisher parentEventPublisher) {
        return new GameEventForwarder(parentEventPublisher);
    }

//    @Bean
//    public GameEventPublisher gameEventPublisher(ApplicationEventPublisher parentEventPublisher) {
//        return new GameEventPublisher(parentEventPublisher);
//    }
}