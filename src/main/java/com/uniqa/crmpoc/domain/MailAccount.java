package com.uniqa.crmpoc.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One inbox the CRM ingests mail from. All accounts are polled for incoming
 * mail; only the ones with canSend=true are offered as a "From" choice when
 * an agent replies (a department alias may receive mail but never be a
 * legitimate reply-from address, e.g. a monitoring/BCC mailbox).
 */
@Entity
@Table(name = "mail_accounts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MailAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String address;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private boolean canSend = false;
}
