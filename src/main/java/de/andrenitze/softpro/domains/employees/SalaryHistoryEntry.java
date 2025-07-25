package de.andrenitze.softpro.domains.employees;

public record SalaryHistoryEntry(int tick, int salary) {

}

//public record ---> автоматически создаёт класс с:
//двумя финальными полями: tick и salary
//публичным конструктором SalaryHistoryEntry(int tick, int salary)
//автоматически сгенерированными:
//getters (tick() и salary())
//equals(), hashCode(), toString()