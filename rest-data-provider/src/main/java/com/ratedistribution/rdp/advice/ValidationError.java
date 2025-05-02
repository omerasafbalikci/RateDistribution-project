package com.ratedistribution.rdp.advice;

import lombok.Data;

/**
 * ValidationError represents details of a validation failure for a specific field.
 * It includes information about the object name, the field name, the rejected value, and an error message.
 * This class is commonly used to encapsulate field-specific validation issues in REST API responses.
 *
 * @author Ömer Asaf BALIKÇI
 */

@Data
public class ValidationError {
    private String object;
    private String field;
    private Object rejectedValue;
    private String message;

    public ValidationError(String object, String field, Object rejectedValue, String message) {
        this.object = object;
        this.field = field;
        this.rejectedValue = rejectedValue;
        this.message = message;
    }
}
