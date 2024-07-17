package de.andrenitze.softpro.types;

import java.util.List;
import java.util.Random;

public enum ProjectType {
    INTRODUCTION, CUSTOMIZATION, CONSULTING, DEVELOPMENT, MAINTENANCE;
    public static final List<String> CONSULTING_DOMAINS = List.of("TechnologyEvaluation,FeasibilityStudy,SWOTAnalysis,PenetrationTesting,SecurityEngineering".split(","));
    public static final List<String> DEVELOPMENT_DOMAINS = List.of("Java,COBOL,C,dotNET,Python,Swift,Kotlin,JavaScript,Go,PHP,Scala,CSharp,MachineLearning,DeepLearning,DataScience,DataEngineering,Embedded,Frontend,Backend,Fullstack,Mobile,Web,CICD".split(","));
    public static final List<String> INTRODUCTION_DOMAINS = List.of("ProcessAssessment,StandardIntroduction,CustomIntroduction,MarketSurvey".split(","));
    public static final List<String> CUSTOMIZATION_DOMAINS = List.of("S4/MONTANA,Dynamix,TYPOW3".split(","));
    public static final List<String> MAINTENANCE_DOMAINS = List.of("PlatformMigration,Refactoring,QualityEvaluation,DataMigration".split(","));

    public String getDomain() {
        return switch (this) {
            case CONSULTING -> CONSULTING_DOMAINS.get(new Random().nextInt(CONSULTING_DOMAINS.size()));
            case DEVELOPMENT -> DEVELOPMENT_DOMAINS.get(new Random().nextInt(DEVELOPMENT_DOMAINS.size()));
            case INTRODUCTION -> INTRODUCTION_DOMAINS.get(new Random().nextInt(INTRODUCTION_DOMAINS.size()));
            case CUSTOMIZATION -> CUSTOMIZATION_DOMAINS.get(new Random().nextInt(CUSTOMIZATION_DOMAINS.size()));
            case MAINTENANCE -> MAINTENANCE_DOMAINS.get(new Random().nextInt(MAINTENANCE_DOMAINS.size()));
        };
    }
}
