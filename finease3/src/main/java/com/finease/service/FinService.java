package com.finease.service;

import com.finease.dto.AccountDto;
import com.finease.dto.JournalDto;
import com.finease.dto.LineDto;
import com.finease.entity.Account;
import com.finease.entity.Journal;
import com.finease.entity.JournalLine;
import com.finease.repository.AccountRepository;
import com.finease.repository.JournalRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service   // Spring membuat objek class ini dan mengelolanya (disebut "bean")
public class FinService {

    private static final Set<String> TYPES =
            new HashSet<>(Arrays.asList("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"));

    private final AccountRepository accountRepo;
    private final JournalRepository journalRepo;

    // Dependency Injection: Spring yang menyuntikkan kedua repository lewat constructor.
    // Tidak ada "new" dan tidak ada HibernateUtil.get() seperti versi lama.
    public FinService(AccountRepository accountRepo, JournalRepository journalRepo) {
        this.accountRepo = accountRepo;
        this.journalRepo = journalRepo;
    }

    // ---------- load: semua akun + semua jurnal ----------
    @Transactional(readOnly = true)
    public Map<String, Object> load() {
        List<AccountDto> accounts = new ArrayList<>();
        for (Account a : accountRepo.findAllByOrderByCodeAsc()) {
            AccountDto d = new AccountDto();
            d.code = a.getCode();
            d.name = a.getName();
            d.type = a.getType();
            accounts.add(d);
        }

        List<JournalDto> journals = new ArrayList<>();
        for (Journal j : journalRepo.findAllWithLines()) {
            JournalDto d = new JournalDto();
            d.id = j.getId();
            d.date = j.getTanggal().toString();
            d.desc = j.getKeterangan();

            List<JournalLine> lines = new ArrayList<>(j.getLines());
            lines.sort(Comparator.comparing(JournalLine::getId));
            for (JournalLine l : lines) {
                LineDto x = new LineDto();
                x.acc = l.getAccount().getCode();
                x.d = l.getDebit();
                x.k = l.getKredit();
                d.lines.add(x);
            }
            journals.add(d);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accounts", accounts);
        out.put("journals", journals);
        return out;
    }

    // ---------- tambah akun ----------
    @Transactional
    public void addAccount(AccountDto r) {
        if (r == null) throw new ApiException("Data akun kosong");
        String code = r.code == null ? "" : r.code.trim();
        String name = r.name == null ? "" : r.name.trim();
        String type = r.type == null ? "" : r.type.trim();

        if (code.isEmpty() || name.isEmpty()) throw new ApiException("Kode dan nama akun wajib diisi");
        if (code.length() > 20) throw new ApiException("Kode akun maksimal 20 karakter");
        if (name.length() > 100) throw new ApiException("Nama akun maksimal 100 karakter");
        if (!TYPES.contains(type)) throw new ApiException("Tipe akun tidak valid");
        if (accountRepo.existsById(code)) throw new ApiException("Kode akun sudah ada");

        accountRepo.save(new Account(code, name, type));
    }

    // ---------- simpan jurnal ----------
    // @Transactional: header + semua baris tersimpan, atau batal semuanya.
    // Dulu ini ditulis manual: beginTransaction, commit, rollback.
    @Transactional
    public long saveJournal(JournalDto r) {
        if (r == null) throw new ApiException("Data jurnal kosong");
        if (r.date == null || r.date.trim().isEmpty()) throw new ApiException("Tanggal wajib diisi");
        LocalDate date;
        try {
            date = LocalDate.parse(r.date.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException("Format tanggal tidak valid");
        }
        String desc = r.desc == null ? "" : r.desc.trim();
        if (desc.isEmpty()) throw new ApiException("Keterangan transaksi wajib diisi");
        if (desc.length() > 255) throw new ApiException("Keterangan maksimal 255 karakter");
        if (r.lines == null || r.lines.size() < 2) throw new ApiException("Jurnal minimal 2 baris akun");

        BigDecimal td = BigDecimal.ZERO, tk = BigDecimal.ZERO;
        for (LineDto l : r.lines) {
            if (l.acc == null || l.acc.trim().isEmpty()) throw new ApiException("Akun pada baris jurnal wajib dipilih");
            l.acc = l.acc.trim();
            l.d = (l.d == null ? BigDecimal.ZERO : l.d).setScale(2, RoundingMode.HALF_UP);
            l.k = (l.k == null ? BigDecimal.ZERO : l.k).setScale(2, RoundingMode.HALF_UP);
            if (l.d.signum() < 0 || l.k.signum() < 0) throw new ApiException("Nilai tidak boleh negatif");
            if (l.d.signum() > 0 && l.k.signum() > 0)
                throw new ApiException("Satu baris tidak boleh berisi debit dan kredit sekaligus");
            td = td.add(l.d);
            tk = tk.add(l.k);
        }
        if (td.signum() == 0) throw new ApiException("Nilai jurnal masih kosong");
        if (td.compareTo(tk) != 0)   // aturan dasar akuntansi: debit harus sama dengan kredit
            throw new ApiException("Jurnal tidak balance, selisih " + td.subtract(tk).abs().toPlainString());

        Journal j = new Journal();
        j.setTanggal(date);
        j.setKeterangan(desc);
        for (LineDto l : r.lines) {
            Account acc = accountRepo.findById(l.acc)
                    .orElseThrow(() -> new ApiException("Akun " + l.acc + " tidak ditemukan"));
            JournalLine jl = new JournalLine();
            jl.setAccount(acc);
            jl.setDebit(l.d);
            jl.setKredit(l.k);
            j.addLine(jl);
        }
        journalRepo.save(j);   // baris jurnal ikut tersimpan lewat cascade
        return j.getId();
    }

    // ---------- hapus jurnal ----------
    @Transactional
    public void delJournal(long id) {
        if (id <= 0) throw new ApiException("ID jurnal tidak valid");
        journalRepo.deleteById(id);   // baris jurnal ikut terhapus (cascade)
    }
}