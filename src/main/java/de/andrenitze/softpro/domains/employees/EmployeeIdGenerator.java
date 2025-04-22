package de.andrenitze.softpro.domains.employees;

import de.andrenitze.softpro.GlobalIdManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@RequiredArgsConstructor
public class EmployeeIdGenerator {
    public int generateId() {
        return GlobalIdManager.generateGlobalId();
    }
}
