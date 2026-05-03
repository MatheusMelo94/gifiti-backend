package com.gifiti.api.dto.response;

import com.gifiti.api.dto.i18n.LocalizedMessage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic single-field success-message response.
 *
 * <p>Per Task 7 of {@code 005-i18n-backend-support}, the {@code message} field
 * carries a {@link LocalizedMessage} (key + args) rather than a hardcoded
 * English string. The registered Jackson serializer resolves it to a plain
 * JSON string at write time using {@code LocaleContextHolder.getLocale()}, so
 * the wire shape ({@code {"message":"..."}}) is unchanged from the pre-i18n era.
 *
 * <p>Convention citation: {@code architecture-conventions.md § Layer Rules} —
 * services own business logic and produce locale-agnostic message keys; the
 * serializer translates to wire format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Generic confirmation message")
public class MessageResponse {

    @Schema(description = "Localized confirmation message", example = "Operation successful", type = "string")
    private LocalizedMessage message;
}
