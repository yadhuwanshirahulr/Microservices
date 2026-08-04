package com.eazybytes.accounts.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.support.ManagedProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "accounts")
@Getter
@Setter
public class AccountsContatctInfoDto{
    private String message;
    private Map<String,String> contactDetails;
    private List<String> onCallSupport;
}
//change record to class so we can change the values at runtime form the configServer at runtime without starting the application again and again.