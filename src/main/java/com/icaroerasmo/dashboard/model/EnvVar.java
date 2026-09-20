package com.icaroerasmo.dashboard.model;

import lombok.Data;

@Data
public class EnvVar {

    private String key;
    private String value;
    private String ref;
    private String defaultValue;
}