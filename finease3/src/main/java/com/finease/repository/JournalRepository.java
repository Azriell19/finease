package com.finease.repository;

import com.finease.entity.Journal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface JournalRepository extends JpaRepository<Journal, Long> {

    @Query("select distinct j from Journal j "
         + "left join fetch j.lines l left join fetch l.account "
         + "order by j.tanggal, j.id")
    List<Journal> findAllWithLines();
}