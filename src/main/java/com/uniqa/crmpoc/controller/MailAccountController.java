package com.uniqa.crmpoc.controller;

import com.uniqa.crmpoc.domain.MailAccount;
import com.uniqa.crmpoc.repository.MailAccountRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only: which mailboxes the CRM ingests from, and which of those an agent may reply as. */
@RestController
@RequestMapping("/api/mail-accounts")
public class MailAccountController {

    private final MailAccountRepository mailAccountRepository;

    public MailAccountController(MailAccountRepository mailAccountRepository) {
        this.mailAccountRepository = mailAccountRepository;
    }

    @GetMapping
    public List<MailAccount> listAll() {
        return mailAccountRepository.findAll();
    }
}
