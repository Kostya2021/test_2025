package de.andrenitze.softpro.domains.projects;

import java.util.List;

import static de.andrenitze.softpro.services.impl.GameServerImpl.RANDOM;

public enum ProjectType {
    INTRODUCTION, CUSTOMIZATION, CONSULTING, DEVELOPMENT, MAINTENANCE, COMPLIANCE;
    public static final List<String> CONSULTING_DOMAINS = List.of("TechnologyEvaluation,FeasibilityStudy,SWOTAnalysis,PenetrationTesting,SecurityEngineering".split(","));
    public static final List<String> DEVELOPMENT_DOMAINS = List.of("Java,COBOL,C,dotNET,Python,Swift,Kotlin,JavaScript,Go,PHP,Scala,CSharp,MachineLearning,DeepLearning,DataScience,DataEngineering,Embedded,Frontend,Backend,Fullstack,Mobile,Web,CICD".split(","));
    public static final List<String> INTRODUCTION_DOMAINS = List.of("ProcessAssessment,StandardIntroduction,CustomIntroduction,MarketSurvey".split(","));
    public static final List<String> CUSTOMIZATION_DOMAINS = List.of("S4/MONTANA,Dynamix,TYPOW3".split(","));
    public static final List<String> MAINTENANCE_DOMAINS = List.of("PlatformMigration,Refactoring,QualityEvaluation,DataMigration".split(","));
    public static final List<String> COMPLIANCE_DOMAINS = List.of("Compliance".split(","));
    public static final List<String> COMPLIANCE_PROJECT_NAMES = List.of(
            "Digital Value Chains Act",
            "ISO 27001 Certification",
            "Sustainability Compliance",
            "HealthyHabits Workplace Certification",
            "Family-Friendly Workplace Certification",
            "GDPR Compliance",
            "Cloud Services - Statements of Compliance (CS-SoC)",
            "Accessibility Compliance",
            "Quality XPert Certificate",
            "'X Process Model' Organization Certificate",
            "'HELIX' Process Certificate",
            "'FAST' Process Model Certificate",
            "Anti-Bribery Compliance Program",
            "Cybersecurity Readiness Certification",
            "Carbon Neutrality Initiative",
            "Ethical Sourcing Certification",
            "Data Privacy Shield Certification",
            "IT Disaster Recovery Certification",
            "Risk Management Framework Implementation",
            "Vendor Compliance Check Program",
            "Whistleblower Protection Compliance",
            "Equal Opportunity Employer Certification",
            "Code of Conduct Implementation",
            "Conflict Minerals Reporting Compliance",
            "Energy Efficiency Certification",
            "Fair Trade Business Seal",
            "Cloud Security Alliance Certification",
            "Workplace Harassment Prevention Program",
            "AI Ethics Certification",
            "Supply Chain Risk Management",
            "Digital Records Retention Compliance",
            "Project Governance Certification",
            "Blockchain Transparency Standard",
            "Employee Wellness Standards Compliance",
            "Social Responsibility Certification",
            "Non-Discrimination Certification",
            "Open Data Transparency Compliance",
            "Financial Audit Readiness Program",
            "Intellectual Property Protection Program",
            "Smart Cities Compliance Certification",
            "Global Trade Compliance",
            "Industry 4.0 Security Certification",
            "Remote Work Policy Compliance",
            "Zero Waste Initiative Certification",
            "Stakeholder Engagement Certification"
    );

    public static ProjectType getTypeByDomain(String domainOfExpertise) {
        if (CONSULTING_DOMAINS.contains(domainOfExpertise)) {
            return CONSULTING;
        } else if (DEVELOPMENT_DOMAINS.contains(domainOfExpertise)) {
            return DEVELOPMENT;
        } else if (INTRODUCTION_DOMAINS.contains(domainOfExpertise)) {
            return INTRODUCTION;
        } else if (CUSTOMIZATION_DOMAINS.contains(domainOfExpertise)) {
            return CUSTOMIZATION;
        } else if (MAINTENANCE_DOMAINS.contains(domainOfExpertise)) {
            return MAINTENANCE;
        }
        return null;
    }

    public String getRandomDomain() {
        return switch (this) {
            case CONSULTING -> CONSULTING_DOMAINS.get(RANDOM.nextInt(CONSULTING_DOMAINS.size()));
            case DEVELOPMENT -> DEVELOPMENT_DOMAINS.get(RANDOM.nextInt(DEVELOPMENT_DOMAINS.size()));
            case INTRODUCTION -> INTRODUCTION_DOMAINS.get(RANDOM.nextInt(INTRODUCTION_DOMAINS.size()));
            case CUSTOMIZATION -> CUSTOMIZATION_DOMAINS.get(RANDOM.nextInt(CUSTOMIZATION_DOMAINS.size()));
            case MAINTENANCE -> MAINTENANCE_DOMAINS.get(RANDOM.nextInt(MAINTENANCE_DOMAINS.size()));
            case COMPLIANCE -> COMPLIANCE_DOMAINS.get(RANDOM.nextInt(COMPLIANCE_DOMAINS.size()));
        };
    }
}
