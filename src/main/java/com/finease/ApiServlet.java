package com.finease;

import com.finease.entity.Account;
import com.finease.entity.Journal;
import com.finease.entity.JournalLine;
import com.google.gson.Gson;
import jakarta.persistence.PersistenceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.exception.ConstraintViolationException;

@WebServlet("/api")
public class ApiServlet extends HttpServlet {

    // ---------- Bentuk JSON (nama field HARUS sama dengan yang dipakai index.html) ----------
    static class ApiException extends Exception {
        ApiException(String m) { super(m); }
    }
    static class AccountDto { String code, name, type; }
    static class LineDto    { String acc; BigDecimal d, k; }
    static class JournalDto { long id; String date, desc; List<LineDto> lines = new ArrayList<>(); }
    static class IdReq      { long id; }

    private static final Gson GSON = new Gson();
    private static final Set<String> TYPES =
            new HashSet<>(Arrays.asList("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"));

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    // ---------- Pintu masuk: memilih aksi berdasarkan parameter ?a= ----------
    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("application/json;charset=UTF-8");
        resp.setHeader("Cache-Control", "no-store");

        String a = req.getParameter("a");
        if (a == null) a = "";
        Object out;
        try {
            switch (a) {
                case "load":        out = load(); break;
                case "addAccount":  out = addAccount(GSON.fromJson(req.getReader(), AccountDto.class)); break;
                case "saveJournal": out = saveJournal(GSON.fromJson(req.getReader(), JournalDto.class)); break;
                case "delJournal":  out = delJournal(GSON.fromJson(req.getReader(), IdReq.class)); break;
                default: throw new ApiException("Aksi tidak dikenal: " + a);
            }
        } catch (ApiException e) {
            resp.setStatus(400);
            out = Collections.singletonMap("error", e.getMessage());
        } catch (PersistenceException e) {            // semua kesalahan Hibernate/database
            e.printStackTrace();
            resp.setStatus(500);
            out = Collections.singletonMap("error", "Kesalahan database: " + e.getMessage());
        } catch (RuntimeException e) {                // JSON rusak / body kosong
            resp.setStatus(400);
            out = Collections.singletonMap("error", "Permintaan tidak valid");
        }
        resp.getWriter().write(GSON.toJson(out));
    }

    // ---------- a=load : ambil semua akun + semua jurnal ----------
    private Map<String, Object> load() {
        try (Session s = HibernateUtil.get().openSession()) {

            // Akun: satu baris HQL (dulu: query + ResultSet + while + map)
            List<AccountDto> accounts = new ArrayList<>();
            for (Account a : s.createQuery("from Account order by code", Account.class).list()) {
                AccountDto d = new AccountDto();
                d.code = a.getCode();
                d.name = a.getName();
                d.type = a.getType();
                accounts.add(d);
            }

            // Jurnal + baris + akunnya diambil sekaligus lewat "join fetch".
            // Pengelompokan per jurnal (dulu manual dengan LinkedHashMap) sekarang otomatis.
            List<Journal> list = s.createQuery(
                    "select distinct j from Journal j "
                    + "left join fetch j.lines l left join fetch l.account "
                    + "order by j.tanggal, j.id", Journal.class).list();

            List<JournalDto> journals = new ArrayList<>();
            for (Journal j : list) {
                JournalDto d = new JournalDto();
                d.id = j.getId();
                d.date = j.getTanggal().toString();     // format yyyy-MM-dd
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
    }

    // ---------- a=addAccount : tambah akun ----------
    private Map<String, Object> addAccount(AccountDto r) throws ApiException {
        if (r == null) throw new ApiException("Data akun kosong");
        String code = r.code == null ? "" : r.code.trim();
        String name = r.name == null ? "" : r.name.trim();
        String type = r.type == null ? "" : r.type.trim();

        if (code.isEmpty() || name.isEmpty()) throw new ApiException("Kode dan nama akun wajib diisi");
        if (code.length() > 20) throw new ApiException("Kode akun maksimal 20 karakter");
        if (name.length() > 100) throw new ApiException("Nama akun maksimal 100 karakter");
        if (!TYPES.contains(type)) throw new ApiException("Tipe akun tidak valid");

        try (Session s = HibernateUtil.get().openSession()) {
            if (s.find(Account.class, code) != null)          // cari berdasarkan primary key
                throw new ApiException("Kode akun sudah ada");

            Transaction tx = s.beginTransaction();
            try {
                s.persist(new Account(code, name, type));     // Hibernate membuat INSERT sendiri
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                if (e instanceof ConstraintViolationException)
                    throw new ApiException("Kode akun sudah ada");
                throw e;
            }
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    // ---------- a=saveJournal : simpan jurnal ----------
    private Map<String, Object> saveJournal(JournalDto r) throws ApiException {
        // --- Validasi (sama seperti sebelumnya) ---
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
        if (td.compareTo(tk) != 0)
            throw new ApiException("Jurnal tidak balance, selisih " + td.subtract(tk).abs().toPlainString());

        // --- Simpan: cukup persist(jurnal), baris-barisnya ikut tersimpan lewat cascade ---
        try (Session s = HibernateUtil.get().openSession()) {
            Transaction tx = s.beginTransaction();
            try {
                Journal j = new Journal();
                j.setTanggal(date);
                j.setKeterangan(desc);

                for (LineDto l : r.lines) {
                    Account acc = s.find(Account.class, l.acc);
                    if (acc == null) throw new ApiException("Akun " + l.acc + " tidak ditemukan");
                    JournalLine jl = new JournalLine();
                    jl.setAccount(acc);
                    jl.setDebit(l.d);
                    jl.setKredit(l.k);
                    j.addLine(jl);
                }

                s.persist(j);
                tx.commit();

                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("id", j.getId());
                return out;
            } catch (ApiException | RuntimeException e) {
                if (tx.isActive()) tx.rollback();        // gagal -> batalkan semuanya
                throw e;
            }
        }
    }

    // ---------- a=delJournal : hapus jurnal ----------
    private Map<String, Object> delJournal(IdReq r) throws ApiException {
        if (r == null || r.id <= 0) throw new ApiException("ID jurnal tidak valid");
        try (Session s = HibernateUtil.get().openSession()) {
            Transaction tx = s.beginTransaction();
            try {
                Journal j = s.find(Journal.class, r.id);
                if (j != null) s.remove(j);               // baris jurnal ikut terhapus (cascade)
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }
}