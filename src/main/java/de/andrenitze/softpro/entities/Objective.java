package de.andrenitze.softpro.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Objective {
    //private Integer id;
    @JsonProperty
    private String title;
    //private String description;

    @JsonProperty
    private int order;

    private int rewardFunds;
    /*
    private String successMessage;
    private String failureMessage;
    private String mission; // Five words or less
    */
}