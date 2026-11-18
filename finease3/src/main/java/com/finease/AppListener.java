package com.finease;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

@WebListener   // Tomcat otomatis menjalankan class ini
public class AppListener implements ServletContextListener {

    // Dipanggil Tomcat saat aplikasi dihentikan atau dipasang ulang
    @Override
    public void contextDestroyed(ServletContextEvent e) {
        HibernateUtil.shutdown();
    }
}