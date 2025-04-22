package de.andrenitze.softpro.config;

import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.domains.employees.TalentMarket;
import de.andrenitze.softpro.events.GameEventPublisher;
import de.andrenitze.softpro.services.ProjectEmployeeMappingService;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.services.impl.player.GamePlayerServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;

@Configuration
@ComponentScan("de.andrenitze.softpro")
@Slf4j
public class GameConfig {
    public GameConfig(ApplicationContext parentContext) {
        log.debug("GameConfig with parentContext '{}' created.", parentContext.getId());
    }

    @Bean
    @Scope("singleton")
    public EmployeeIdGenerator employeeIdGenerator() {
        return new EmployeeIdGenerator();
    }

    @Bean
    @Scope("singleton")
    public TalentMarket talentMarket() {
        TalentMarket market = new TalentMarket();
        market.clear();
        return market;
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
    public ProjectEmployeeMappingImpl projectEmployeeMapping() {
        return new ProjectEmployeeMappingImpl();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ProjectServiceImpl projectService(SkillServiceImpl skillService,
                                             ProjectEmployeeMappingImpl projectEmployeeMapping) {
        return new ProjectServiceImpl(skillService, projectEmployeeMapping);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    @Primary
    public GamePlayerServiceImpl playerService(TalentMarket talentMarket) {
        return new GamePlayerServiceImpl(talentMarket);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public EmployeeServiceImpl employeeService(ProjectEmployeeMappingImpl mapping,
                                               @Lazy MessagingServiceImpl messagingService){
        return new EmployeeServiceImpl(messagingService, mapping);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public GameEventPublisher eventPublisher() {
        return new GameEventPublisher();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ObjectiveServiceImpl objectiveService(@Lazy GamePlayerServiceImpl playerService,
                                                 GameLifeCycleService lifeCycleService,
                                                 ProjectServiceImpl projectService,
                                                 SkillServiceImpl skillService,
                                                 ProjectEmployeeMappingService projectEmployeeService) {
        return new ObjectiveServiceImpl(playerService, lifeCycleService, projectService, skillService, projectEmployeeService);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public LevelConsequencesService levelConsequencesService(
            @Lazy GamePlayerServiceImpl playerService,
            @Lazy TalentMarket talentMarket) {
        return new LevelConsequencesService(playerService, talentMarket);
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public StoryService storyService() {
        return new StoryService();
    }
}