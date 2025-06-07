package com.ratedistribution.rdp.config;

import com.ratedistribution.rdp.utilities.exceptions.ConfigErrorException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ConfigValidationListener {
    private final SimulatorProperties simulatorProperties;

    public ConfigValidationListener(SimulatorProperties simulatorProperties) {
        this.simulatorProperties = simulatorProperties;
    }

    @EventListener
    public void onRefresh(RefreshScopeRefreshedEvent event) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Set<ConstraintViolation<SimulatorProperties>> violations = validator.validate(simulatorProperties);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new ConfigErrorException("Config refresh failed due to validation errors: " + message);
        }
    }
}
