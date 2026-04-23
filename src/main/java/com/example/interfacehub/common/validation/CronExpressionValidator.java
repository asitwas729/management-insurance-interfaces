package com.example.interfacehub.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.scheduling.support.CronExpression;

public class CronExpressionValidator implements ConstraintValidator<ValidCron, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // @NotBlank에서 처리하도록 위임
        }
        return CronExpression.isValidExpression(value);
    }
}
