package com.eazybytes.accounts.dto;

import org.springframework.beans.factory.support.ManagedProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "accounts")
public record AccountsContatctInfoDto(String message, Map<String,String> contactDetails, List<String> onCallSupport) {
}
// record is used for when we want to fetch data only. only getter method will be applicable inside the record by default all method are final we cannot change the value as setter method is not applicable for record