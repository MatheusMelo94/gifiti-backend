package com.gifiti.api.model;

/**
 * Outcome of a single attempt to hand an email to the upstream provider.
 *
 * <p>Deliberately narrow: this records what the <em>send call</em> returned, not
 * what the mailbox did with it. {@code SENT} means Resend accepted the message,
 * not that it was delivered, opened, or escaped a spam filter.
 */
public enum EmailDeliveryStatus {

    /** Resend accepted the message and returned an ID. */
    SENT,

    /** The send call threw — the message never reached Resend. */
    FAILED
}
