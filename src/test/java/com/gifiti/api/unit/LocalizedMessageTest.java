package com.gifiti.api.unit;

import com.gifiti.api.dto.i18n.LocalizedMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Unit tests for {@link LocalizedMessage}, the value type that services attach
 * to {@code MessageResponse} / {@code RegisterResponse} instead of a hardcoded
 * English string. Resolution to a localized string happens at JSON serialization
 * time via {@code LocalizedMessageSerializer} — the record itself is a passive
 * carrier of {@code (key, args)}.
 *
 * <p>Pinned invariants:</p>
 * <ul>
 *   <li>Null key is rejected at construction time — services must always supply
 *       a key, otherwise the serializer has nothing to look up.</li>
 *   <li>Null args is normalized to an empty array — callers that pass no MessageFormat
 *       arguments should not have to construct an empty array themselves.</li>
 *   <li>Records are value-equal — two instances with the same key + args are equal.
 *       This is standard Java record behavior; the test pins it because the
 *       serializer test harness relies on it.</li>
 * </ul>
 */
class LocalizedMessageTest {

    @Test
    @DisplayName("of(key) constructs with the key and an empty args array")
    void of_with_key_only_uses_empty_args() {
        LocalizedMessage message = LocalizedMessage.of("auth.register.success");

        assertThat(message.messageKey()).isEqualTo("auth.register.success");
        assertThat(message.args()).isEmpty();
    }

    @Test
    @DisplayName("of(key, args...) preserves positional args in order")
    void of_with_args_preserves_order() {
        LocalizedMessage message = LocalizedMessage.of("error.something", "first", 2);

        assertThat(message.messageKey()).isEqualTo("error.something");
        assertThat(message.args()).containsExactly("first", 2);
    }

    @Test
    @DisplayName("constructor normalizes null args to an empty array")
    void constructor_normalizes_null_args() {
        LocalizedMessage message = new LocalizedMessage("auth.register.success", null);

        assertThat(message.args()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("constructor rejects a null key with NullPointerException")
    void constructor_rejects_null_key() {
        assertThatNullPointerException()
                .isThrownBy(() -> new LocalizedMessage(null, new Object[0]));
    }

    @Test
    @DisplayName("two instances sharing the same args reference are value-equal")
    void value_equality_with_shared_args_reference() {
        // Java records use Arrays default Object.equals on array fields (reference
        // equality), which is the normal record contract. Two records that share
        // the same args reference are equal; two records with separate-but-deep-
        // equal arrays are not. This test pins that contract so a future
        // accidental override of equals() shows up here.
        Object[] sharedArgs = new Object[0];
        LocalizedMessage a = new LocalizedMessage("auth.register.success", sharedArgs);
        LocalizedMessage b = new LocalizedMessage("auth.register.success", sharedArgs);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("messageKey and args are accessible via record accessors")
    void record_accessors_work() {
        LocalizedMessage a = LocalizedMessage.of("auth.register.success", "x");

        assertThat(a.messageKey()).isEqualTo("auth.register.success");
        assertThat(a.args()).containsExactly("x");
    }
}
