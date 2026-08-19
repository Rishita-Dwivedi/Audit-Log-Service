package com.auditlog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Enforces the append-only guarantee mechanically (docs/DECISIONS.md ADR-010), not just by
 * convention: fails the build if a mutation mapping is ever added to an audit controller.
 */
class AppendOnlyApiTest {

    @Test
    void controllersExposeNoMutationEndpoints() {
        for (Class<?> controller : new Class<?>[]{AuditEventController.class, AuditVerifyController.class}) {
            for (Method method : controller.getDeclaredMethods()) {
                assertThat(method.getAnnotation(PutMapping.class))
                        .as(controller.getSimpleName() + "." + method.getName() + " must not be a PUT mapping")
                        .isNull();
                assertThat(method.getAnnotation(PatchMapping.class))
                        .as(controller.getSimpleName() + "." + method.getName() + " must not be a PATCH mapping")
                        .isNull();
                assertThat(method.getAnnotation(DeleteMapping.class))
                        .as(controller.getSimpleName() + "." + method.getName() + " must not be a DELETE mapping")
                        .isNull();
            }
        }
    }
}
