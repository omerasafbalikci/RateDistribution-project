package com.ratedistribution.tdp.net;

import lombok.RequiredArgsConstructor;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@RequiredArgsConstructor
public class ClientSession implements Runnable {
    private final Socket socket;
    private final SubscriptionManager subs;
    private final BufferedReader in;
    private final BufferedWriter out;

    ClientSession(Socket s, SubscriptionManager subs) throws IOException {
        this.socket = s;
        this.subs = subs;
        this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
    }

    void start() {
        new Thread(this, "client-" + socket.getPort()).start();
    }

    @Override
    public void run() {
        try {
            send("WELCOME|Connected to Rate TCP Server");
            send("Escape character is '^]'");
            String l;
            while ((l = in.readLine()) != null) {
                System.out.println("[TCP IN] " + l);
                handle(normalizeBackspace(l.trim()));
            }
        } catch (IOException e) {
            System.out.println("[TCP] Client disconnected: " + socket.getInetAddress());
        } finally {
            close();
        }
    }

    private void handle(String cmd) throws IOException {
        if (cmd == null || cmd.isBlank() || !cmd.contains("|")) {
            send("ERROR|Invalid request format");
            return;
        }
        String[] p = cmd.split("\\|", 2);
        if (p.length != 2 || p[0].isBlank() || p[1].isBlank()) {
            send("ERROR|Invalid request format");
            return;
        }
        String op = p[0].toLowerCase(Locale.ROOT);
        String sym = p[1].toUpperCase(Locale.ROOT);
        switch (op) {
            case "subscribe" -> {
                subs.subscribe(sym, this);
            }
            case "unsubscribe" -> {
                subs.unsubscribe(sym, this);
            }
            default -> send("ERROR|Unknown command");
        }
    }

    void push(String json) {
        try {
            send(json);
        } catch (IOException e) {
            close();
        }
    }

    private void send(String s) throws IOException {
        out.write(s);
        out.write("\r\n");
        out.flush();
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
        subs.purge(this);
    }

    private String normalizeBackspace(String input) {
        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (c == '\b') {
                if (!sb.isEmpty()) sb.deleteCharAt(sb.length() - 1);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
