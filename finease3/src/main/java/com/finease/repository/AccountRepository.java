package com.finease.repository;

import com.finease.entity.Account;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {

    List<Account> findAllByOrderByCodeAsc();
}