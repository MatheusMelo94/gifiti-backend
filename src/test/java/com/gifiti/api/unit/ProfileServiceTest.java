package com.gifiti.api.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.gifiti.api.dto.request.UpdateProfileRequest;
import com.gifiti.api.dto.response.ProfileResponse;
import com.gifiti.api.model.User;
import com.gifiti.api.model.enums.Language;
import com.gifiti.api.repository.UserRepository;
import com.gifiti.api.service.ProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProfileService}.
 *
 * <p>Covers Task 9 of {@code 005-i18n-backend-support}:
 * <ul>
 *   <li>Persistence of {@code preferredLanguage} on profile update.</li>
 *   <li>No-op behavior when the field is {@code null} or unchanged.</li>
 *   <li>Security finding F-3: audit log emitted on actual change of
 *       {@code preferredLanguage}, suppressed on no-op or unchanged updates.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final String EMAIL = "maria@example.com";
    private static final String USER_ID = "65f1a2b3c4d5e6f7a8b9c0d1";

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProfileService profileService;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger profileServiceLogger;

    @BeforeEach
    void attachLogAppender() {
        profileServiceLogger = (Logger) LoggerFactory.getLogger(ProfileService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        profileServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        profileServiceLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    private User userWith(Language preferred) {
        return User.builder()
                .id(USER_ID)
                .email(EMAIL)
                .displayName("Maria")
                .preferredLanguage(preferred)
                .build();
    }

    private List<ILoggingEvent> auditEvents() {
        return logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .filter(e -> e.getFormattedMessage().contains("field=preferredLanguage"))
                .toList();
    }

    @Nested
    @DisplayName("updateProfile preferredLanguage persistence")
    class PersistenceTests {

        @Test
        @DisplayName("persists preferredLanguage when set on a user with no prior preference")
        void updateProfile_persists_preferredLanguage_when_set() {
            User existing = userWith(null);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .preferredLanguage(Language.PT_BR)
                    .build();

            ProfileResponse response = profileService.updateProfile(EMAIL, request);

            ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
            org.mockito.Mockito.verify(userRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getPreferredLanguage()).isEqualTo(Language.PT_BR);
            assertThat(response.getPreferredLanguage()).isEqualTo(Language.PT_BR);
        }

        @Test
        @DisplayName("changes preferredLanguage from EN_US to PT_BR")
        void updateProfile_changes_preferredLanguage_from_one_to_another() {
            User existing = userWith(Language.EN_US);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .preferredLanguage(Language.PT_BR)
                    .build();

            profileService.updateProfile(EMAIL, request);

            ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
            org.mockito.Mockito.verify(userRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getPreferredLanguage()).isEqualTo(Language.PT_BR);
        }

        @Test
        @DisplayName("does not change preferredLanguage when request field is null")
        void updateProfile_does_not_change_preferredLanguage_when_field_is_null() {
            User existing = userWith(Language.PT_BR);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .displayName("Maria Santos")
                    .build();

            profileService.updateProfile(EMAIL, request);

            ArgumentCaptor<User> savedCaptor = ArgumentCaptor.forClass(User.class);
            org.mockito.Mockito.verify(userRepository).save(savedCaptor.capture());
            assertThat(savedCaptor.getValue().getPreferredLanguage()).isEqualTo(Language.PT_BR);
        }
    }

    @Nested
    @DisplayName("Security finding F-3 — audit log on language change")
    class AuditLogTests {

        @Test
        @DisplayName("emits INFO audit line when preferredLanguage actually changes")
        void updateProfile_logs_audit_line_when_preferredLanguage_changes() {
            User existing = userWith(Language.EN_US);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .preferredLanguage(Language.PT_BR)
                    .build();

            profileService.updateProfile(EMAIL, request);

            List<ILoggingEvent> audit = auditEvents();
            assertThat(audit).hasSize(1);
            String formatted = audit.get(0).getFormattedMessage();
            // Email masked: "ma***@example.com" (first 2 chars + *** + @domain).
            assertThat(formatted)
                    .contains("user=ma***@example.com")
                    .contains("field=preferredLanguage")
                    .contains("from=EN_US")
                    .contains("to=PT_BR");
        }

        @Test
        @DisplayName("emits audit line treating legacy null preferredLanguage as EN_US")
        void updateProfile_logs_audit_line_when_legacy_null_changes_to_PT_BR() {
            User existing = userWith(null);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .preferredLanguage(Language.PT_BR)
                    .build();

            profileService.updateProfile(EMAIL, request);

            List<ILoggingEvent> audit = auditEvents();
            assertThat(audit).hasSize(1);
            // Legacy nulls are reported as EN_US (User.effectiveLanguage())
            // so the audit log never carries a null sentinel.
            assertThat(audit.get(0).getFormattedMessage())
                    .contains("from=EN_US")
                    .contains("to=PT_BR");
        }

        @Test
        @DisplayName("does not log when preferredLanguage is unchanged (no-op update)")
        void updateProfile_does_not_log_audit_line_when_preferredLanguage_unchanged() {
            User existing = userWith(Language.PT_BR);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .preferredLanguage(Language.PT_BR)
                    .build();

            profileService.updateProfile(EMAIL, request);

            assertThat(auditEvents()).isEmpty();
        }

        @Test
        @DisplayName("does not log when preferredLanguage is omitted from the request")
        void updateProfile_does_not_log_audit_line_when_field_is_null() {
            User existing = userWith(Language.EN_US);
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing));
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            UpdateProfileRequest request = UpdateProfileRequest.builder()
                    .displayName("Maria Santos")
                    .build();

            profileService.updateProfile(EMAIL, request);

            assertThat(auditEvents()).isEmpty();
        }
    }
}
