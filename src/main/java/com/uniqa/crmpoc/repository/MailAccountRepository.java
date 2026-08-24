package com.uniqa.crmpoc.repository;

import com.uniqa.crmpoc.domain.MailAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MailAccountRepository extends JpaRepository<MailAccount, Long> {
    boolean existsByAddress(String address);

    Optional<MailAccount> findByAddress(String address);

    List<MailAccount> findByCanSendTrue();

    boolean existsByAddressAndCanSendTrue(String address);
}
