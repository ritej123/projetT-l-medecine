package com.telemed.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("!dev")
public class RequiredSecretsValidator {

    public RequiredSecretsValidator(@Value("${spring.datasource.password:}") String databasePassword) {
        if (!StringUtils.hasText(databasePassword)) {
            throw new IllegalStateException("DB_PASSWORD must be configured outside the dev profile.");
        }
    }
}
