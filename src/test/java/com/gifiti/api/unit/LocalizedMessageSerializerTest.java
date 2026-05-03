package com.gifiti.api.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.gifiti.api.dto.i18n.LocalizedMessage;
import com.gifiti.api.dto.i18n.LocalizedMessageSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LocalizedMessageSerializer}. These tests prove the
 * Jackson layer resolves {@link LocalizedMessage} values via {@code MessageSource}
 * + {@code LocaleContextHolder.getLocale()} at write time, never serializes the
 * internal {@code messageKey} field, and falls back safely on missing keys.
 *
 * <p>Test isolation: each test resets {@link LocaleContextHolder} before and
 * after to avoid leaking thread-local state to sibling tests.</p>
 */
class LocalizedMessageSerializerTest {

    private MessageSource messageSource;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        messageSource = mock(MessageSource.class);
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalizedMessage.class, new LocalizedMessageSerializer(messageSource));
        objectMapper = new ObjectMapper().registerModule(module);
        LocaleContextHolder.resetLocaleContext();
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    @DisplayName("serializes a LocalizedMessage to a plain JSON string under en-US")
    void serializes_to_plain_string_under_en_US() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        when(messageSource.getMessage(eq("test.greeting"), any(), eq(Locale.US))).thenReturn("hello");

        String json = objectMapper.writeValueAsString(LocalizedMessage.of("test.greeting"));

        assertThat(json).isEqualTo("\"hello\"");
    }

    @Test
    @DisplayName("uses the pt-BR locale on LocaleContextHolder when present")
    void serializes_under_pt_BR_when_locale_context_holds_pt_BR() throws Exception {
        Locale ptBR = Locale.forLanguageTag("pt-BR");
        LocaleContextHolder.setLocale(ptBR);
        when(messageSource.getMessage(eq("test.greeting"), any(), eq(ptBR))).thenReturn("olá");

        String json = objectMapper.writeValueAsString(LocalizedMessage.of("test.greeting"));

        assertThat(json).isEqualTo("\"olá\"");
    }

    @Test
    @DisplayName("never writes the internal messageKey field to the JSON output")
    void does_not_leak_messageKey_to_json() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        when(messageSource.getMessage(eq("auth.register.success"), any(), eq(Locale.US)))
                .thenReturn("Registration successful. Please check your email to verify your account.");

        String json = objectMapper.writeValueAsString(LocalizedMessage.of("auth.register.success"));

        assertThat(json)
                .as("the serializer must not leak internal key names to clients")
                .doesNotContain("messageKey")
                .doesNotContain("auth.register.success")
                .doesNotContain("args");
    }

    @Test
    @DisplayName("falls back to error.unexpected when the requested key is missing")
    void falls_back_to_error_unexpected_on_missing_key() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        when(messageSource.getMessage(eq("missing.key"), any(), eq(Locale.US)))
                .thenThrow(new NoSuchMessageException("missing.key"));
        when(messageSource.getMessage(eq("error.unexpected"), any(), eq(Locale.US)))
                .thenReturn("An unexpected error occurred");

        String json = objectMapper.writeValueAsString(LocalizedMessage.of("missing.key"));

        assertThat(json).isEqualTo("\"An unexpected error occurred\"");
    }

    @Test
    @DisplayName("falls back to a hardcoded constant when even error.unexpected is missing")
    void falls_back_to_hardcoded_constant_when_error_unexpected_missing() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        when(messageSource.getMessage(eq("missing.key"), any(), eq(Locale.US)))
                .thenThrow(new NoSuchMessageException("missing.key"));
        when(messageSource.getMessage(eq("error.unexpected"), any(), eq(Locale.US)))
                .thenThrow(new NoSuchMessageException("error.unexpected"));

        String json = objectMapper.writeValueAsString(LocalizedMessage.of("missing.key"));

        assertThat(json)
                .as("the serializer must never throw — it always emits a string")
                .isNotBlank()
                .startsWith("\"")
                .endsWith("\"");
    }

    @Test
    @DisplayName("serializes correctly inside a larger object")
    void serializes_inside_larger_object() throws Exception {
        LocaleContextHolder.setLocale(Locale.US);
        when(messageSource.getMessage(eq("test.greeting"), any(), eq(Locale.US))).thenReturn("hello");

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", "abc");
        envelope.put("message", LocalizedMessage.of("test.greeting"));

        String json = objectMapper.writeValueAsString(envelope);

        assertThat(json).isEqualTo("{\"id\":\"abc\",\"message\":\"hello\"}");
    }
}
