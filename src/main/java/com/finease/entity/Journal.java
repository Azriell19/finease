package com.finease.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journals")
public class Journal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // id dibuat otomatis (AUTO_INCREMENT)
    private Long id;

    @Column(name = "tanggal", nullable = false)
    private LocalDate tanggal;

    @Column(name = "keterangan", nullable = false)
    private String keterangan;

    // Satu jurnal punya BANYAK baris.
    // cascade = ALL : simpan atau hapus jurnal otomatis ikut menyimpan atau menghapus barisnya.
    // mappedBy      : relasinya diatur oleh field "journal" di class JournalLine.
    @OneToMany(mappedBy = "journal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<JournalLine> lines = new ArrayList<>();

    // Menambah baris sekaligus menghubungkannya ke jurnal ini
    public void addLine(JournalLine line) {
        lines.add(line);
        line.setJournal(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getTanggal() { return tanggal; }
    public void setTanggal(LocalDate tanggal) { this.tanggal = tanggal; }
    public String getKeterangan() { return keterangan; }
    public void setKeterangan(String keterangan) { this.keterangan = keterangan; }
    public List<JournalLine> getLines() { return lines; }
    public void setLines(List<JournalLine> lines) { this.lines = lines; }
}