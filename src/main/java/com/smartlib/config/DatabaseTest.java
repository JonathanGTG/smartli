package com.smartlib.app;

import com.smartlib.config.DatabaseConnection;
import java.sql.Connection;

public class DatabaseTest {

    public static void main(String[] args) {

        try (Connection connection =
                     DatabaseConnection.getConnection()) {

            System.out.println("Koneksi MySQL berhasil!");

        } catch (Exception e) {

            System.out.println("Koneksi database gagal!");
            e.printStackTrace();

        }
    }
}
