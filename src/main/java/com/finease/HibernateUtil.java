package com.finease;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {

    private static SessionFactory factory;

    // Dibuat sekali saat pertama kali dibutuhkan, lalu dipakai ulang
    public static synchronized SessionFactory get() {
        if (factory == null) {
            // configure() otomatis membaca file hibernate.cfg.xml (Tahap 2)
            factory = new Configuration().configure().buildSessionFactory();
        }
        return factory;
    }

    // Menutup semua koneksi database
    public static synchronized void shutdown() {
        if (factory != null) {
            factory.close();
            factory = null;
        }
    }
}