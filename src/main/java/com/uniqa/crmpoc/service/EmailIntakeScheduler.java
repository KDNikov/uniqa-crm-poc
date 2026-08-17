package com.uniqa.crmpoc.service;

import com.uniqa.crmpoc.email.EmailSource;
import com.uniqa.crmpoc.email.RawEmail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Polls the configured EmailSource on a fixed interval and processes anything new. */
@Component
@Slf4j
public class EmailIntakeScheduler {

    private final EmailSource emailSource;
    private final EmailProcessingService processingService;

    public EmailIntakeScheduler(EmailSource emailSource, EmailProcessingService processingService) {
        this.emailSource = emailSource;
        this.processingService = processingService;
    }

    @Scheduled(fixedDelayString = "${email.poll.fixed-delay-ms}", initialDelay = 5000)
    public void pollInbox() {
        List<RawEmail> newEmails = emailSource.fetchNewEmails();
        if (newEmails.isEmpty()) {
            return;
        }
        log.info("Fetched {} new email(s)", newEmails.size());
        List<String> processedIds = new ArrayList<>();
        for (RawEmail raw : newEmails) {
            try {
                processingService.process(raw);
                processedIds.add(raw.messageId());
            } catch (Exception e) {
                log.error("Failed to process email {} ('{}'); leaving unseen for retry",
                        raw.messageId(), raw.subject(), e);
            }
        }
        emailSource.acknowledge(processedIds);
    }
}
