package com.gifiti.api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "account_lockouts")
public class AccountLockout {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    @Builder.Default
    private int failedAttempts = 0;

    private Instant lockedUntil;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;
}
