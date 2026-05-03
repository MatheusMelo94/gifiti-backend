package com.gifiti.api.dto.i18n;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;

import java.io.IOException;
import java.util.Locale;

/**
 * Jackson {@link JsonSerializer} that resolves {@link LocalizedMessage} values
 * to a plain JSON string at write time using the project {@link MessageSource}
 * and the locale carried on {@link LocaleContextHolder} (set by
 * {@code GifitiLocaleResolver}).
 *
 * <p>Wired in {@code I18nConfig} via a {@code Jackson2ObjectMapperBuilderCustomizer}
 * so every {@code ObjectMapper} Spring constructs picks up the serializer
 * automatically — DTOs declare {@code LocalizedMessage} fields without per-field
 * Jackson annotations.</p>
 *
 * <h3>Wire-format guarantees (pinned by {@code LocalizedMessageSerializerTest})</h3>
 * <ul>
 *   <li>The output is always a plain JSON string — never a nested object.</li>
 *   <li>The internal {@code messageKey} and {@code args} fields are <em>never</em>
 *       written to the JSON output. Internal key namespaces stay inside the
 *       service. This is a security guarantee, not just a style choice.</li>
 *   <li>Missing keys never propagate as exceptions to the response pipeline.
 *       The serializer falls back to {@code error.unexpected} (localized), then
 *       to the hardcoded constant {@link #LAST_RESORT_MESSAGE} if even that key
 *       is absent — same backstop pattern as {@code GlobalExceptionHandler.localize}
 *       (Risk #3 in {@code 005-i18n-backend-support/plan.md}).</li>
 *   <li>Missing keys are logged at {@code WARN} so operators see them — bundles
 *       drifting from the code are surfaced, not silently swallowed.</li>
 * </ul>
 */
@Slf4j
public class LocalizedMessageSerializer extends JsonSerializer<LocalizedMessage> {

    /**
     * Last-resort literal returned when both the requested key AND
     * {@code error.unexpected} are missing from the bundle. Hardcoded English
     * because at that point the i18n infrastructure itself is broken — emitting
     * an English fallback is strictly better than throwing through Jackson.
     */
    static final String LAST_RESORT_MESSAGE = "An error occurred";

    private final MessageSource messageSource;

    public LocalizedMessageSerializer(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public void serialize(LocalizedMessage value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        Locale locale = LocaleContextHolder.getLocale();
        gen.writeString(resolve(value, locale));
    }

    private String resolve(LocalizedMessage value, Locale locale) {
        try {
            return messageSource.getMessage(value.messageKey(), value.args(), locale);
        } catch (NoSuchMessageException missing) {
            log.warn("i18n bundle missing key '{}' for locale {} — falling back to error.unexpected",
                    value.messageKey(), locale);
            try {
                return messageSource.getMessage("error.unexpected", null, locale);
            } catch (NoSuchMessageException backstopMissing) {
                log.warn("i18n bundle missing fallback key 'error.unexpected' for locale {} — using last-resort literal",
                        locale);
                return LAST_RESORT_MESSAGE;
            }
        }
    }
}
