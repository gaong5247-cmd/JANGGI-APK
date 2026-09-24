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
    private volatile String lastLine = "";
    private int lastSkill=-1, lastThreads=-1, lastHash=-1, lastPv=-1;
    private final Object responseLock = new Object();
    private final Set<String> supportedVariants = new HashSet<>();
    private String variant;
    interface Info { void line(String line); }
    Engine(String path) throws Exception { this(path, "janggi"); }
    Engine(String path, String variant) throws Exception {
        VariantConfig.require(variant);
        // On some Android 14/15 devices a child ELF extracted from the APK
        // exits immediately when executed directly. Invoke the matching 32/64-bit system
        // linker explicitly and expose the APK native-library directory so
        // libc++_shared.so is resolved for the child process.
        ProcessBuilder launcher;
        File engineFile=new File(path);
        File nativeDir=engineFile.getParentFile();
        boolean elf64=true;
        if(path.endsWith("libfairy.so"))try(InputStream elf=new FileInputStream(engineFile)){
            byte[] header=new byte[5];if(elf.read(header)==5)elf64=header[4]==2;
        }
        File linker=new File(elf64?"/system/bin/linker64":"/system/bin/linker");
        if (linker.isFile() && path.endsWith("libfairy.so"))
            launcher=new ProcessBuilder(linker.getAbsolutePath(),path);
        else
            launcher=new ProcessBuilder(path);
        if(nativeDir!=null)launcher.environment().put("LD_LIBRARY_PATH",nativeDir.getAbsolutePath());
        process = launcher.redirectErrorStream(true).start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), "UTF-8"));
        Thread reader = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"))) {
                String s; while ((s = r.readLine()) != null) lines.offer(s);
            } catch (IOException ignored) {} finally { lines.offer("__EOF__"); }
        }, "uci-reader");
        reader.setDaemon(true); reader.start();
        try {
            send("uci"); until("uciok", 30000, line -> {
                if (line.startsWith("option name UCI_Variant ")) {
                    String[] parts=line.split(" var ");
                    for (int i=1;i<parts.length;i++) supportedVariants.add(parts[i].trim());
                }
            });
            send("setoption name Use NNUE value false");
            setVariant(variant);
        } catch (Exception ex) { close(); throw ex; }
    }
    /** Call on the controller's serial worker. stop() may be called from UI.
     * The response lock waits for an active search to consume its bestmove. */
    State setVariant(String selected) throws Exception {
        VariantConfig.require(selected);
        if (!supportedVariants.contains(selected)) throw new IOException("엔진이 지원하지 않는 규칙: " + selected);
        stop();
        synchronized (responseLock) {
            send("setoption name UCI_Variant value " + selected);
            ready();
            send("ucinewgame");
            send("position startpos");
            ready();
            State initial=state();
            if (!selected.equals(initial.variant) || initial.startFen==null || !initial.ongoing())
                throw new IOException("장기 규칙 초기화 실패: " + selected);
            variant=selected;
            return initial;
        }
    }
    String variant() { return variant; }
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
            lastLine=s;
            if (s.equals("__EOF__")) {
                int code=-1;
                try { code=process.exitValue(); } catch (IllegalThreadStateException ignored) {}
                throw new IOException("엔진 프로세스가 종료되었습니다 (exit="+code+", last='"+lastLine+"')");
            }
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
    // Run on the serial worker, on a fresh process before replacing live engines.
    void useNetwork(String path) throws Exception {
        if (path == null || path.isEmpty()) return;
        if (path.contains("\n") || path.contains("\r")) throw new IOException("잘못된 NNUE 경로");
        send("setoption name EvalFile value " + path);
        send("setoption name Use NNUE value true");
        ready();
        send("position startpos");
        send("go depth 1");
        boolean[] enabled={false};
        until("bestmove ", 30000, line -> {
            if (line.startsWith("info string NNUE evaluation using ") && line.endsWith(" enabled")) enabled[0]=true;
        });
        if (!enabled[0]) throw new IOException("호환되는 장기 NNUE로 확인되지 않았습니다");
    }
    State state() throws Exception {
        ArrayList<String> response=new ArrayList<>(); send("appstate");
        until("appdone", 10000, response::add);
        return State.parse(response);
    }
    String search(int ms, java.util.function.BooleanSupplier valid, Info info) throws Exception {
        synchronized (responseLock) {
            synchronized (this) {
                if (!valid.getAsBoolean()) return null;
                send("go movetime " + ms);
            }
            String line = until("bestmove ", ms + 30000L, info);
            return line.split(" +")[1];
        }
    }
    synchronized void close() {
        if (closed) return;
        try { send("quit"); } catch (Exception ignored) {}
        closed=true; process.destroy();
    }
    static final class State {
        String fen, result, reason, variant, startFen;
        boolean white, check, bikjang;
        final ArrayList<String> legal=new ArrayList<>();
        boolean ongoing() { return "ongoing".equals(result); }
        boolean canPlay(String move) { return ongoing() && legal.contains(move); }
        static State parse(List<String> lines) throws IOException {
            State s=new State();
            HashMap<String,String> fields=new HashMap<>();
            for (String line:lines) {
                String[] pair=line.split(" ",2);
                if (pair[0].startsWith("app") && !pair[0].equals("appdone")) {
                    if (fields.put(pair[0],pair.length==2?pair[1].trim():"")!=null)
                        throw new IOException("중복 엔진 국면 응답");
                }
            }
            s.variant=fields.get("appvariant");s.startFen=fields.get("appstartfen");
            s.fen=fields.get("appfen");s.result=fields.get("appresult");s.reason=fields.get("appreason");
            if (s.fen==null || !s.fen.matches("\\S+ [wb] .*" )
                || !Arrays.asList("ongoing","win","loss","draw").contains(s.result)
                || s.reason==null || !s.reason.matches("[a-z_]+")
                || !Arrays.asList("0","1").contains(fields.get("appcheck"))
                || !Arrays.asList("0","1").contains(fields.get("appbikjang"))
                || !fields.containsKey("applegal")) throw new IOException("불완전한 엔진 국면 응답");
            s.check=fields.get("appcheck").equals("1");s.bikjang=fields.get("appbikjang").equals("1");
            String moves=fields.get("applegal");
            if (!moves.isEmpty()) for (String m:moves.split(" +")) {
                if (!m.matches("[a-i](10|[1-9])[a-i](10|[1-9])")) throw new IOException("잘못된 합법수 응답");
                s.legal.add(m);
            }
            if (s.ongoing() != s.reason.equals("none") || (s.ongoing() && s.legal.isEmpty())
                || (!s.ongoing() && !s.legal.isEmpty())
                || (s.reason.equals("checkmate") && (!s.check || !s.result.equals("loss"))))
                throw new IOException("모순된 엔진 국면 응답");
            s.white=s.fen.split(" ")[1].equals("w");
            return s;
        }
    }
}
