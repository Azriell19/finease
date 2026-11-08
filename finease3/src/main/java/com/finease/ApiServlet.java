package com.finease;

import com.google.gson.Gson;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/api")
public class ApiServlet extends HttpServlet {

    static class ApiException extends Exception {
        ApiException(String m) { super(m); }
    }
    static class Account { String code, name, type; }
    static class Line    { String acc; BigDecimal d, k; }  // acc=kode akun, d=debit, k=kredit
    static class Journal { long id; String date, desc; List<Line> lines = new ArrayList<>(); }
    static class IdReq   { long id; }

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
                case "addAccount":  out = addAccount(GSON.fromJson(req.getReader(), Account.class)); break;
                case "saveJournal": out = saveJournal(GSON.fromJson(req.getReader(), Journal.class)); break;
                case "delJournal":  out = delJournal(GSON.fromJson(req.getReader(), IdReq.class)); break;
                default: throw new ApiException("Aksi tidak dikenal: " + a);
            }
        } catch (ApiException e) {                  // kesalahan input -> {"error":"..."} (toast merah di layar)
            resp.setStatus(400);
            out = Collections.singletonMap("error", e.getMessage());
        } catch (SQLException e) {                  // kesalahan database
            resp.setStatus(500);
            out = Collections.singletonMap("error", "Kesalahan database: " + e.getMessage());
        } catch (RuntimeException e) {              // JSON rusak / body kosong
            resp.setStatus(400);
            out = Collections.singletonMap("error", "Permintaan tidak valid");
        }
        resp.getWriter().write(GSON.toJson(out));   // objek Java -> teks JSON
    }

    // ---------- a=load : ambil semua akun + semua jurnal ----------
    private Map<String, Object> load() throws SQLException {
        List<Account> accounts = new ArrayList<>();
        Map<Long, Journal> journals = new LinkedHashMap<>();   // LinkedHashMap menjaga urutan

        try (Connection c = db.get()) {
            // 1) Akun
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT code,name,type FROM accounts ORDER BY code")) {
                while (rs.next()) {
                    Account x = new Account();
                    x.code = rs.getString(1);
                    x.name = rs.getString(2);
                    x.type = rs.getString(3);
                    accounts.add(x);
                }
            }
            // 2) Jurnal + barisnya (join), lalu dikelompokkan per jurnal
            String sql = "SELECT j.id,j.tanggal,j.keterangan,l.acc_code,l.debit,l.kredit "
                    + "FROM journals j JOIN journal_lines l ON l.journal_id=j.id "
                    + "ORDER BY j.tanggal,j.id,l.id";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                while (rs.next()) {
                    long id = rs.getLong(1);
                    Journal j = journals.get(id);
                    if (j == null) {                         // jurnal baru ditemui -> buat objeknya
                        j = new Journal();
                        j.id = id;
                        j.date = rs.getDate(2).toString();   // format yyyy-MM-dd
                        j.desc = rs.getString(3);
                        journals.put(id, j);
                    }
                    Line l = new Line();
                    l.acc = rs.getString(4);
                    l.d = rs.getBigDecimal(5);
                    l.k = rs.getBigDecimal(6);
                    j.lines.add(l);
                }
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accounts", accounts);
        out.put("journals", new ArrayList<>(journals.values()));
        return out;
    }

    // ---------- a=addAccount : tambah akun ----------
    private Map<String, Object> addAccount(Account r) throws ApiException, SQLException {
        if (r == null) throw new ApiException("Data akun kosong");
        String code = r.code == null ? "" : r.code.trim();
        String name = r.name == null ? "" : r.name.trim();
        String type = r.type == null ? "" : r.type.trim();

        if (code.isEmpty() || name.isEmpty()) throw new ApiException("Kode dan nama akun wajib diisi");
        if (code.length() > 20) throw new ApiException("Kode akun maksimal 20 karakter");
        if (name.length() > 100) throw new ApiException("Nama akun maksimal 100 karakter");
        if (!TYPES.contains(type)) throw new ApiException("Tipe akun tidak valid");

        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("INSERT INTO accounts(code,name,type) VALUES(?,?,?)")) {
            ps.setString(1, code);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) throw new ApiException("Kode akun sudah ada");  // duplikat primary key
            throw e;
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }

    // ---------- a=saveJournal : simpan jurnal (validasi dulu, baru simpan) ----------
    private Map<String, Object> saveJournal(Journal r) throws ApiException, SQLException {
        // --- Validasi header ---
        if (r == null) throw new ApiException("Data jurnal kosong");
        if (r.date == null || r.date.trim().isEmpty()) throw new ApiException("Tanggal wajib diisi");
        Date date;
        try {
            date = Date.valueOf(r.date.trim());
        } catch (IllegalArgumentException e) {
            throw new ApiException("Format tanggal tidak valid");
        }
        String desc = r.desc == null ? "" : r.desc.trim();
        if (desc.isEmpty()) throw new ApiException("Keterangan transaksi wajib diisi");
        if (desc.length() > 255) throw new ApiException("Keterangan maksimal 255 karakter");
        if (r.lines == null || r.lines.size() < 2) throw new ApiException("Jurnal minimal 2 baris akun");

        // --- Validasi baris + hitung total ---
        BigDecimal td = BigDecimal.ZERO, tk = BigDecimal.ZERO;
        for (Line l : r.lines) {
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
        if (td.compareTo(tk) != 0)   // aturan dasar akuntansi: debit harus = kredit
            throw new ApiException("Jurnal tidak balance, selisih " + td.subtract(tk).abs().toPlainString());

        // --- Simpan dalam satu TRANSAKSI: header + semua baris berhasil, atau batal semua ---
        try (Connection c = db.get()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO journals(tanggal,keterangan) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setDate(1, date);
                    ps.setString(2, desc);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {   // ambil id jurnal yang baru dibuat
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO journal_lines(journal_id,acc_code,debit,kredit) VALUES(?,?,?,?)")) {
                    for (Line l : r.lines) {
                        ps.setLong(1, id);
                        ps.setString(2, l.acc);
                        ps.setBigDecimal(3, l.d);
                        ps.setBigDecimal(4, l.k);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("ok", true);
                out.put("id", id);
                return out;
            } catch (SQLException e) {
                c.rollback();                                          // gagal -> batalkan semuanya
                if (e.getErrorCode() == 1452) throw new ApiException("Salah satu akun tidak ditemukan");
                throw e;
            }
        }
    }

    // ---------- a=delJournal : hapus jurnal ----------
    private Map<String, Object> delJournal(IdReq r) throws ApiException, SQLException {
        if (r == null || r.id <= 0) throw new ApiException("ID jurnal tidak valid");
        try (Connection c = db.get();
             PreparedStatement ps = c.prepareStatement("DELETE FROM journals WHERE id=?")) {
            ps.setLong(1, r.id);
            ps.executeUpdate();   // baris di journal_lines ikut terhapus (ON DELETE CASCADE)
        }
        return Collections.<String, Object>singletonMap("ok", true);
    }
}