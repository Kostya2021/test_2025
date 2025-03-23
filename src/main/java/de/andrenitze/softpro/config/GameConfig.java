package de.andrenitze.softpro.config;

import de.andrenitze.softpro.Game;
import de.andrenitze.softpro.services.impl.*;
import de.andrenitze.softpro.TalentMarket;
import de.andrenitze.softpro.domains.employees.EmployeeIdGenerator;
import de.andrenitze.softpro.events.GameEventHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfig {
    @Bean
    public Game game() {
        return new Game();  // Create a new Game instance
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
    public AccountingServiceImpl accountingService(Game game) {
        return new AccountingServiceImpl(game);
    }

    @Bean
    public ProjectServiceImpl projectService(Game game) {
        return new ProjectServiceImpl(game);
    }

    @Bean
    public EmployeeServiceImpl employeeService(Game game, ProjectServiceImpl projectService,
                                               MessagingServiceImpl messagingServiceImpl) {
        EmployeeServiceImpl service = new EmployeeServiceImpl(game, projectService);
        service.setMessagingService(messagingServiceImpl);
        return service;
    }

    @Bean
    public PlayerServiceImpl playerService(Game game, TalentMarket talentMarket,
                                           ProjectServiceImpl projectService) {
        return new PlayerServiceImpl(game, talentMarket, projectService);
    }

    @Bean
    public MessagingServiceImpl messagingService(Game game) {
        return new MessagingServiceImpl(game);
    }

    @Bean
    public GameEventHandler eventHandler(Game game) {
        return new GameEventHandler(game);
    }
}