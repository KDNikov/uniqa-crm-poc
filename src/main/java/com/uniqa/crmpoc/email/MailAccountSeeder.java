package com.uniqa.crmpoc.email;

import com.uniqa.crmpoc.domain.MailAccount;
import com.uniqa.crmpoc.repository.MailAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Persists the DemoMailAccounts roster as MailAccount rows on first boot, so
 * the rest of the app (send-from picker, "which inbox did this arrive at")
 * has something to query. Independent of GreenMailTestServerConfig's IMAP
 * user registration - both just read the same DemoMailAccounts.ALL list.
 */
@Component
class MailAccountSeeder implements CommandLineRunner {

    private final MailAccountRepository mailAccountRepository;

    MailAccountSeeder(MailAccountRepository mailAccountRepository) {
        this.mailAccountRepository = mailAccountRepository;
    }

    @Override
    public void run(String... args) {
        for (DemoMailAccounts.Def def : DemoMailAccounts.ALL) {
            if (!mailAccountRepository.existsByAddress(def.address())) {
                mailAccountRepository.save(new MailAccount(null, def.address(), def.displayName(), def.canSend()));
            }
        }
    }
}
