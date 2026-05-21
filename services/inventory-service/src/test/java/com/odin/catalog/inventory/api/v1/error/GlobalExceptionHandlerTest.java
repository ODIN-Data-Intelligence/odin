package com.odin.catalog.inventory.api.v1.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithMessage() {
        NoSuchElementException ex = new NoSuchElementException("Dataset not found: abc");

        ProblemDetail result = handler.handleNotFound(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getDetail()).isEqualTo("Dataset not found: abc");
    }

    @Test
    void handleIllegalArg_returns400WithMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid lifecycle status");

        ProblemDetail result = handler.handleIllegalArg(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isEqualTo("Invalid lifecycle status");
    }

    @Test
    void handleValidation_returns400WithFieldErrors() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "dataProductRequest");
        bindingResult.addError(new FieldError("dataProductRequest", "title", "must not be blank"));
        bindingResult.addError(new FieldError("dataProductRequest", "ownerId", "must not be null"));

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.mockito.Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail result = handler.handleValidation(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail())
            .contains("title: must not be blank")
            .contains("ownerId: must not be null");
    }

    @Test
    void handleValidation_noFieldErrors_returnsGenericMessage() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "req");

        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        org.mockito.Mockito.when(ex.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail result = handler.handleValidation(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getDetail()).isEqualTo("Validation failed");
    }

    @Test
    void handleUnexpected_returns500() {
        RuntimeException ex = new RuntimeException("Something exploded");

        ProblemDetail result = handler.handleUnexpected(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(result.getDetail()).isEqualTo("An unexpected error occurred");
    }
}
