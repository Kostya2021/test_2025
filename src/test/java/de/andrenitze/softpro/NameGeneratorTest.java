package de.andrenitze.softpro;

import de.andrenitze.softpro.domains.employees.NameGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class NameGeneratorTest {

    private NameGenerator nameGenerator;

    @BeforeEach
    void setUp() {
        nameGenerator = NameGenerator.getInstance();
    }

    @Test
    void testSingletonInstance() {
        NameGenerator anotherInstance = NameGenerator.getInstance();
        assertSame(nameGenerator, anotherInstance, "Instances should be the same (singleton pattern)");
    }

    @Test
    void testGenerateName() {
        String[] name = nameGenerator.generateName();
        assertNotNull(name, "Generated name should not be null");
        assertEquals(3, name.length, "Generated name array should have 3 elements");
        assertNotNull(name[0], "First name should not be null");
        assertNotNull(name[1], "Last name should not be null");
        assertTrue(name[2].equals(NameGenerator.MALE) || name[2].equals(NameGenerator.FEMALE), "Gender should be either 'male' or 'female'");
    }

    @Test
    void testRandomlyAssignGender() {
        nameGenerator.generateName();
        String gender = nameGenerator.generateName()[2];
        assertTrue(gender.equals(NameGenerator.MALE) || gender.equals(NameGenerator.FEMALE), "Gender should be either 'male' or 'female'");
    }

    @Test
    void testSelectRegion() throws Exception {
        Method method = NameGenerator.class.getDeclaredMethod("selectRegion");
        method.setAccessible(true);
        int region = (int) method.invoke(nameGenerator);
        assertTrue(region >= 0 && region <= 5, "Region index should be between 0 and 5");
    }

    @Test
    void testGetFirstNamesByGenderAndRegion() throws Exception {
        Method method = NameGenerator.class.getDeclaredMethod("getFirstNamesByGenderAndRegion", String.class, int.class);
        method.setAccessible(true);

        String[] maleNames = (String[]) method.invoke(nameGenerator, NameGenerator.MALE, 0);
        assertNotNull(maleNames, "Male names array should not be null");
        assertTrue(maleNames.length > 0, "Male names array should not be empty");

        String[] femaleNames = (String[]) method.invoke(nameGenerator, NameGenerator.FEMALE, 0);
        assertNotNull(femaleNames, "Female names array should not be null");
        assertTrue(femaleNames.length > 0, "Female names array should not be empty");

        String[] unisexNames = (String[]) method.invoke(nameGenerator, "unisex", 0);
        assertNotNull(unisexNames, "Unisex names array should not be null");
        assertTrue(unisexNames.length > 0, "Unisex names array should not be empty");
    }
}