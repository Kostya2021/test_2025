package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.employees.generators.EmployeeNameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeNameGeneratorTest {

    private EmployeeNameGenerator employeeNameGenerator;

    @BeforeEach
    void setUp() {
        employeeNameGenerator = EmployeeNameGenerator.getInstance();
    }

    @Test
    void testSingletonInstance() {
        EmployeeNameGenerator anotherInstance = EmployeeNameGenerator.getInstance();
        assertSame(employeeNameGenerator, anotherInstance, "Instances should be the same (singleton pattern)");
    }

    @Test
    void testGenerateName() {
        String[] name = employeeNameGenerator.generateName();
        assertNotNull(name, "Generated name should not be null");
        assertEquals(3, name.length, "Generated name array should have 3 elements");
        assertNotNull(name[0], "First name should not be null");
        assertNotNull(name[1], "Last name should not be null");
        assertTrue(name[2].equals(EmployeeNameGenerator.MALE) || name[2].equals(EmployeeNameGenerator.FEMALE), "Gender should be either 'male' or 'female'");
    }

    @Test
    void testRandomlyAssignGender() {
        employeeNameGenerator.generateName();
        String gender = employeeNameGenerator.generateName()[2];
        assertTrue(gender.equals(EmployeeNameGenerator.MALE) || gender.equals(EmployeeNameGenerator.FEMALE), "Gender should be either 'male' or 'female'");
    }

    @Test
    void testSelectRegion() throws Exception {
        Method method = EmployeeNameGenerator.class.getDeclaredMethod("selectRegion");
        method.setAccessible(true);
        int region = (int) method.invoke(employeeNameGenerator);
        assertTrue(region >= 0 && region <= 5, "Region index should be between 0 and 5");
    }

    @Test
    void testGetFirstNamesByGenderAndRegion() throws Exception {
        Method method = EmployeeNameGenerator.class.getDeclaredMethod("getFirstNamesByGenderAndRegion", String.class, int.class);
        method.setAccessible(true);

        String[] maleNames = (String[]) method.invoke(employeeNameGenerator, EmployeeNameGenerator.MALE, 0);
        assertNotNull(maleNames, "Male names array should not be null");
        assertTrue(maleNames.length > 0, "Male names array should not be empty");

        String[] femaleNames = (String[]) method.invoke(employeeNameGenerator, EmployeeNameGenerator.FEMALE, 0);
        assertNotNull(femaleNames, "Female names array should not be null");
        assertTrue(femaleNames.length > 0, "Female names array should not be empty");

        String[] unisexNames = (String[]) method.invoke(employeeNameGenerator, "unisex", 0);
        assertNotNull(unisexNames, "Unisex names array should not be null");
        assertTrue(unisexNames.length > 0, "Unisex names array should not be empty");
    }
}