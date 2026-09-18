/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Distributed WITHOUT ANY WARRANTY; see LICENSE.
 */
package org.janggilab;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** One isolated UCI process. All reads belong to the controller's serial worker. */
final class Engine {
    final Process process;
    private final BufferedWriter writer;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private volatile boolean closed;
    private int lastSkill=-1, lastThreads=-1, lastHash=-1, lastPv=-1;
    interface Info { void line(String line); }
    Engine(String path) throws Exception {
        process = new ProcessBuilder(path).redirectErrorStream(true).start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String s; while ((s = r.readLine()) != null) lines.offer(s);
            } catch (IOException ignored) {} finally { lines.offer("__EOF__"); }
        }, "uci-reader");
        reader.setDaemon(true); reader.start();
        send("uci"); until("uciok", 30000, null);
        send("setoption name UCI_Variant value janggi");
        send("setoption name Use NNUE value false");
        ready();
    }
    synchronized void send(String s) throws IOException {
        if (closed) throw new IOException("엔진이 종료되었습니다");
        writer.write(s); writer.newLine(); writer.flush();
    }
    void stop() { try { send("stop"); } catch (Exception ignored) {} }
    String until(String prefix, long timeout, Info info) throws Exception {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout);
        while (true) {
            long left = end-System.nanoTime();
            if (left <= 0) throw new IOException("엔진 응답 시간 초과");
            String s = lines.poll(left, TimeUnit.NANOSECONDS);
            if (s == null) throw new IOException("엔진 응답 시간 초과");
            if (s.equals("__EOF__")) throw new IOException("엔진 프로세스가 종료되었습니다");
            if (info != null) info.line(s);
            if (s.startsWith(prefix)) return s;
        }
    }
    void ready() throws Exception { send("isready"); until("readyok", 30000, null); }
    void configure(int skill, int threads, int hash, int pv) throws Exception {
        if (lastSkill != skill) { send("setoption name Skill Level value " + skill); lastSkill=skill; }
        if (lastThreads != threads) { send("setoption name Threads value " + threads); lastThreads=threads; }
        if (lastHash != hash) { send("setoption name Hash value " + hash); lastHash=hash; }
        if (lastPv != pv) { send("setoption name MultiPV value " + pv); lastPv=pv; }
        ready();
    }
    void position(String fen, List<String> moves) throws Exception {
        send("position fen " + fen + (moves.isEmpty()?"":" moves " + String.join(" ",moves)));
    }
    State state() throws Exception {
        State s = new State(); send("appstate");
        until("appdone", 10000, line -> {
            if (line.startsWith("appfen ")) s.fen = line.substring(7);
            if (line.startsWith("applegal ")) s.legal.addAll(Arrays.asList(line.substring(9).trim().split(" +")));
            if (line.startsWith("appresult ")) s.result=line.substring(10);
            if (line.startsWith("appcheck ")) s.check=line.endsWith("1");
        });
        if (s.fen == null) throw new IOException("엔진 국면 응답 오류");
        s.white=s.fen.split(" ")[1].equals("w"); return s;
    }
    String search(int ms, java.util.function.BooleanSupplier valid, Info info) throws Exception {
        synchronized (this) {
            if (!valid.getAsBoolean()) return null;
            send("go movetime " + ms);
        }
        String line = until("bestmove ", ms + 30000L, info);
        return line.split(" +")[1];
    }
    synchronized void close() {
        if (closed) return;
        try { send("quit"); } catch (Exception ignored) {}
        closed=true; process.destroy();
    }
    static final class State {
        String fen, result="ongoing";
        boolean white, check;
        final ArrayList<String> legal=new ArrayList<>();
    }
}
