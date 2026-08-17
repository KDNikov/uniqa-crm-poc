package com.uniqa.crmpoc.controller;

import com.uniqa.crmpoc.domain.Email;
import com.uniqa.crmpoc.dto.CategoryOverrideRequest;
import com.uniqa.crmpoc.dto.EmailArchivedRequest;
import com.uniqa.crmpoc.dto.EmailReadRequest;
import com.uniqa.crmpoc.dto.SendEmailRequest;
import com.uniqa.crmpoc.email.EmailSendService;
import com.uniqa.crmpoc.email.EmailSource;
import com.uniqa.crmpoc.email.OutgoingEmail;
import com.uniqa.crmpoc.email.RawEmail;
import com.uniqa.crmpoc.repository.EmailRepository;
import com.uniqa.crmpoc.service.EmailProcessingService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/emails")
@Slf4j
public class EmailController {

    private final EmailRepository emailRepository;
    private final EmailSource emailSource;
    private final EmailProcessingService processingService;
    private final EmailSendService emailSendService;

    public EmailController(EmailRepository emailRepository, EmailSource emailSource,
                            EmailProcessingService processingService, EmailSendService emailSendService) {
        this.emailRepository = emailRepository;
        this.emailSource = emailSource;
        this.processingService = processingService;
        this.emailSendService = emailSendService;
    }

    @GetMapping
    public List<Email> listAll() {
        return emailRepository.findByArchivedFalseOrderByReceivedAtDesc();
    }

    @GetMapping("/category/{categoryName}")
    public List<Email> byCategory(@PathVariable String categoryName) {
        return emailRepository.findByFinalCategoryAndArchivedFalseOrderByReceivedAtDesc(categoryName);
    }

    /** Manually trigger a fetch cycle instead of waiting for the scheduler - handy for demos. */
    @PostMapping("/fetch-now")
    public List<Email> fetchNow() {
        List<Email> results = new ArrayList<>();
        List<String> processedIds = new ArrayList<>();
        for (RawEmail raw : emailSource.fetchNewEmails()) {
            try {
                Email email = processingService.process(raw);
                processedIds.add(raw.messageId());
                if (email != null) {
                    results.add(email);
                }
            } catch (Exception e) {
                log.error("Failed to process email {} ('{}'); leaving unseen for retry",
                        raw.messageId(), raw.subject(), e);
            }
        }
        emailSource.acknowledge(processedIds);
        return results;
    }

    /** Let a human override the auto-assigned category from the CRM inbox view. */
    @PutMapping("/{id}/category")
    public Email overrideCategory(@PathVariable Long id, @Valid @RequestBody CategoryOverrideRequest req) {
        Email email = emailRepository.findById(id).orElseThrow();
        email.setFinalCategory(req.category());
        return emailRepository.save(email);
    }

    @PutMapping("/{id}/read")
    public Email setRead(@PathVariable Long id, @RequestBody EmailReadRequest req) {
        Email email = emailRepository.findById(id).orElseThrow();
        email.setRead(req.read());
        return emailRepository.save(email);
    }

    /** Archiving is a soft-delete: the email is excluded from the active inbox views but not removed. */
    @PutMapping("/{id}/archived")
    public Email setArchived(@PathVariable Long id, @RequestBody EmailArchivedRequest req) {
        Email email = emailRepository.findById(id).orElseThrow();
        email.setArchived(req.archived());
        return emailRepository.save(email);
    }

    /** Reply / Reply All / Forward - the frontend prefills the request, this just sends it. */
    @PostMapping("/{id}/send")
    public void send(@PathVariable Long id, @Valid @RequestBody SendEmailRequest req) {
        Email original = emailRepository.findById(id).orElseThrow();
        emailSendService.send(new OutgoingEmail(req.to(), req.cc(), req.subject(), req.body(), original.getMessageId()));
    }
}
