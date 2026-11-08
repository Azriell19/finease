package com.finease;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class db {

    // Alamat database. "finease" = nama database dari Tahap 1.
    private static final String URL = "jdbc:mysql://localhost:3306/finease"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Jakarta&characterEncoding=UTF-8";
    private static final String USER = "root";   // sesuaikan dengan MySQL Anda
    private static final String PASS = "";       // XAMPP default: kosong

    // Dijalankan sekali saat class dimuat: mendaftarkan driver MySQL.
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Dipanggil setiap kali butuh koneksi baru.
    public static Connection get() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}