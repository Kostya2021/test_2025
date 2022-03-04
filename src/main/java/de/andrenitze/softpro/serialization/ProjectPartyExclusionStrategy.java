package de.andrenitze.softpro.serialization;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;

public class ProjectPartyExclusionStrategy implements ExclusionStrategy {

    @Override
    public boolean shouldSkipField(FieldAttributes f) {
        return (f.getName().equals("employees") || f.getName().equals("objectives") || f.getName().equals("funds"));
    }

    @Override
    public boolean shouldSkipClass(Class<?> aClass) {
        return false;
    }
}
