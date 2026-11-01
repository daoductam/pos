package com.pos.identityaccess.api;

import com.pos.identityaccess.application.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    @Test
    void shouldReturnSafeValidationEnvelopeWithoutExceptionDetails() {
        HttpServletRequest request = new MockHttpServletRequest();
        var response = new ApiExceptionHandler().handleIdentity(
                new ValidationException("email contains user supplied text"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody().error().code()).isEqualTo("validation_error");
        assertThat(response.getBody().error().message()).isEqualTo("Request is invalid");
        assertThat(response.getBody().error().correlationId()).startsWith("cor_");
    }
}
