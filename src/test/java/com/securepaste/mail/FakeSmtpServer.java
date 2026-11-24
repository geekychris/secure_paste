package com.securepaste.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * A fake SMTP server for tests: plain SMTP on 127.0.0.1 (no STARTTLS, so a
 * client that requires TLS must refuse it), AUTH PLAIN if asked, and every
 * message recorded. Tests never talk to a real mail provider.
 */
public class FakeSmtpServer implements AutoCloseable {

    /** One received message. */
    public record Message(String from, List<String> to, String data, String authUser, String authPassword) { }

    private final ServerSocket socket;
    private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());
    private final Thread thread;

    public FakeSmtpServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        thread = new Thread(this::serve, "fake-smtp");
        thread.setDaemon(true);
        thread.start();
    }

    public int port() { return socket.getLocalPort(); }

    public List<Message> messages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public void clear() { messages.clear(); }

    private void serve() {
        while (!socket.isClosed()) {
            try (Socket c = socket.accept()) {
                handle(c);
            } catch (IOException e) {
                // closed, or a client went away
            }
        }
    }

    private void handle(Socket c) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        OutputStream out = c.getOutputStream();
        String from = null, user = null, pass = null;
        List<String> to = new ArrayList<>();
        say(out, "220 fake ESMTP");
        String line;
        while ((line = in.readLine()) != null) {
            String verb = line.split(" ", 2)[0].toUpperCase();
            switch (verb) {
                case "EHLO" -> say(out, "250-fake\r\n250-AUTH PLAIN\r\n250 8BITMIME");
                case "HELO" -> say(out, "250 fake");
                case "AUTH" -> {
                    String[] f = line.split(" ");
                    String raw = f.length >= 3 ? f[2] : "";
                    if (raw.isEmpty()) {
                        say(out, "334 ");
                        raw = in.readLine();
                    }
                    String[] parts = new String(Base64.getDecoder().decode(raw.trim()), StandardCharsets.UTF_8).split("\0", -1);
                    if (parts.length == 3) {
                        user = parts[1];
                        pass = parts[2];
                    }
                    say(out, "235 ok");
                }
                case "MAIL" -> { from = between(line); say(out, "250 ok"); }
                case "RCPT" -> { to.add(between(line)); say(out, "250 ok"); }
                case "DATA" -> {
                    say(out, "354 send");
                    StringBuilder b = new StringBuilder();
                    String l;
                    while ((l = in.readLine()) != null && !l.equals(".")) {
                        b.append(l.startsWith("..") ? l.substring(1) : l).append("\r\n");
                    }
                    messages.add(new Message(from, List.copyOf(to), b.toString(), user, pass));
                    to.clear();
                    say(out, "250 queued");
                }
                case "RSET", "NOOP" -> say(out, "250 ok");
                case "QUIT" -> { say(out, "221 bye"); return; }
                default -> say(out, "502 unknown");
            }
        }
    }

    private static String between(String line) {
        int i = line.indexOf('<'), j = line.indexOf('>');
        return i >= 0 && j > i ? line.substring(i + 1, j) : "";
    }

    private static void say(OutputStream out, String s) throws IOException {
        out.write((s + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
