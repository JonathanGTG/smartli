package com.smartlib.ui;

import javax.swing.*;
import java.awt.*;

/** Menu demo untuk membuka form-form modul Anggota 4. */
public class Member4MenuFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    public Member4MenuFrame() {
        super("SmartLib - Modul Transaksi Anggota 4");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 330);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridLayout(4, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 40, 25, 40));

        JButton loanButton = new JButton("Peminjaman Buku");
        loanButton.addActionListener(e -> new LoanFrame().setVisible(true));
        JButton returnButton = new JButton("Pengembalian Buku");
        returnButton.addActionListener(e -> new ReturnFrame().setVisible(true));
        JButton queueButton = new JButton("Antrean Peminjaman");
        queueButton.addActionListener(e -> new ReservationFrame().setVisible(true));
        JButton fineButton = new JButton("Denda Anggota");
        fineButton.addActionListener(e -> new FineFrame().setVisible(true));

        panel.add(loanButton);
        panel.add(returnButton);
        panel.add(queueButton);
        panel.add(fineButton);
        add(panel);
    }
}
