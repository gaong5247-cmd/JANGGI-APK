/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.janggilab;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Search-only UCI engine. Fairy-Stockfish remains the authoritative Janggi arbiter. */
final class SearchEngine {
    interface Info { void line(String line); }
    final Process process;
    private final BufferedWriter writer;
    private final BlockingQueue<String> lines=new ArrayBlockingQueue<>(4096);
    private final Object responseLock=new Object();
    private final Set<String> options=new HashSet<>();
    private final Set<String> variants=new HashSet<>();
    private final String profile, rulesPath;
    private volatile boolean closed;
    private volatile String lastLine="";
    private String selectedVariant, network="";
    private int skill=-1, threads=-1, hash=-1, pv=-1;
    private static final String EOF="__JANGGI_ENGINE_EOF__";

    SearchEngine(String executable,String profile,String variant,String rulesPath,String nnue) throws Exception {
        this.profile=profile;this.rulesPath=rulesPath;
        File engine=new File(executable);
        ProcessBuilder launcher;
        if(engine.getName().startsWith("lib")&&engine.getName().endsWith(".so")){
            int elfClass=0;
            try(InputStream in=new FileInputStream(engine)){
                byte[] header=new byte[5];
                if(in.read(header)==5&&header[0]==127&&header[1]=='E'
                   &&header[2]=='L'&&header[3]=='F')elfClass=header[4];
            }
            if(elfClass!=1&&elfClass!=2)throw new IOException("올바른 ELF 엔진이 아닙니다");
            File linker=new File(elfClass==2?"/system/bin/linker64":"/system/bin/linker");
            launcher=linker.isFile()?new ProcessBuilder(linker.getAbsolutePath(),executable)
                                    :new ProcessBuilder(executable);
        } else launcher=new ProcessBuilder(executable);
        if(engine.getParentFile()!=null)
            launcher.environment().put("LD_LIBRARY_PATH",engine.getParentFile().getAbsolutePath());
        process=launcher.redirectErrorStream(true).start();
        writer=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),"UTF-8"));
        Thread reader=new Thread(()->{
            try(BufferedReader r=new BufferedReader(new InputStreamReader(process.getInputStream(),"UTF-8"))){
                String line;
                while((line=r.readLine())!=null)offer(line);
            }catch(IOException ignored){}finally{offer(EOF);}
        },"search-uci-reader");
        reader.setDaemon(true);reader.start();
        try{
            send("uci");
            until("uciok",30000,line->{
                if(line.startsWith("option name ")){
                    int type=line.indexOf(" type ");
                    if(type>12) {
                        String name=line.substring(12,type).trim();
                        options.add(name);
                        if(name.equals("UCI_Variant")){
                            String[] parts=line.split(" var ");
                            for(int i=1;i<parts.length;i++)variants.add(parts[i].trim());
                        }
                    }
                }
            });
            setVariant(variant);
            if(nnue==null||nnue.isEmpty()){
                if(options.contains("Use NNUE"))send("setoption name Use NNUE value false");
                ready();
            }else useNetwork(nnue);
        }catch(Exception ex){close();throw ex;}
    }
    private void offer(String s){
        if(!lines.offer(s)){lines.poll();lines.offer(s);}
    }
    synchronized void send(String command) throws IOException {
        if(closed||!process.isAlive())throw new IOException("선택한 엔진 프로세스가 종료되었습니다");
        if(command.indexOf('\n')>=0||command.indexOf('\r')>=0)
            throw new IOException("잘못된 엔진 명령");
        writer.write(command);writer.newLine();writer.flush();
    }
    private String until(String prefix,long timeout,Info info) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
        for(;;){
            long left=deadline-System.nanoTime();
            if(left<=0)throw new IOException("UCI 엔진 응답 시간 초과: "+prefix);
            String line=lines.poll(left,TimeUnit.NANOSECONDS);
            if(line==null)throw new IOException("UCI 엔진 응답 시간 초과: "+prefix);
            if(line.equals(EOF)){
                int exit=-1;try{exit=process.exitValue();}catch(IllegalThreadStateException ignored){}
                throw new IOException("UCI 엔진이 종료됨 (exit="+exit+", last="+lastLine+")");
            }
            lastLine=line;
            if(line.startsWith("info string ERROR")||line.startsWith("info string ERROR:"))
                throw new IOException("UCI 엔진 오류: "+line);
            if(info!=null)info.line(line);
            if(line.startsWith(prefix))return line;
        }
    }
    void ready() throws Exception {send("isready");until("readyok",30000,null);}
    private static String labVariant(String id){
        switch(id){
            case "janggitraditional":return "janggilabtraditional";
            case "janggimodern":return "janggilabmodern";
            case "jangginopass":return "janggilabnopass";
            case "janggiblitz":return "janggilabblitz";
            default:throw new IllegalArgumentException("지원하지 않는 장기 규칙: "+id);
        }
    }
    void setVariant(String id) throws Exception {
        VariantConfig.require(id);
        synchronized(responseLock){
            stop();
            // The Stockless source variants differ from Janggi Lab variants in
            // scoring/repetition: load the exact config instead of guessing.
            if(EngineProfile.STOCKLESS.equals(profile)){
                if(rulesPath==null||rulesPath.isEmpty()
                    ||!options.contains("VariantConfigPath")||!options.contains("VariantName"))
                    throw new IOException("Stockless의 사용자 장기 규칙 설정 인터페이스가 없습니다");
                send("setoption name VariantConfigPath value "+rulesPath);
                ready();
                send("setoption name VariantName value "+labVariant(id));
            }else if(variants.contains(id)){
                send("setoption name UCI_Variant value "+id);
            }else if(rulesPath!=null&&!rulesPath.isEmpty()
                     &&options.contains("VariantConfigPath")&&options.contains("VariantName")){
                send("setoption name VariantConfigPath value "+rulesPath);
                ready();
                send("setoption name VariantName value "+labVariant(id));
            }else throw new IOException("엔진이 해당 장기 규칙을 지원하지 않습니다: "+id);
            ready();send("ucinewgame");send("position startpos");ready();
            selectedVariant=id;
            skill=threads=hash=pv=-1;
        }
    }
    void useNetwork(String path) throws Exception {
        if(path==null||path.isEmpty())return;
        if(path.indexOf('\n')>=0||path.indexOf('\r')>=0||!new File(path).isFile())
            throw new IOException("NNUE 경로가 잘못되었습니다");
        if(!options.contains("Use NNUE")||!options.contains("EvalFile"))
            throw new IOException("선택한 엔진에는 FSF NNUE 로더가 없습니다");
        synchronized(responseLock){
            send("setoption name Use NNUE value false");
            send("setoption name EvalFile value "+path);
            send("setoption name Use NNUE value true");
            ready();send("position startpos");
            final boolean[] enabled={false};
            send("go depth 1");
            until("bestmove ",45000,line->{
                if(line.contains("NNUE evaluation using")&&line.contains("enabled"))enabled[0]=true;
            });
            if(!enabled[0]){
                send("eval");
                until("Final evaluation",30000,line->{
                    if(line.contains("NNUE evaluation using")&&line.contains("enabled"))enabled[0]=true;
                });
            }
            if(!enabled[0])throw new IOException("선택한 엔진에서 FSF 장기 NNUE 사용을 확인하지 못했습니다");
            network=path;ready();
        }
    }
    void configure(int level,int nThreads,int hashMb,int multiPv) throws Exception {
        synchronized(responseLock){
            if(skill!=level&&options.contains("Skill Level")){
                send("setoption name Skill Level value "+level);skill=level;
            }
            if(threads!=nThreads&&options.contains("Threads")){
                send("setoption name Threads value "+nThreads);threads=nThreads;
            }
            if(hash!=hashMb&&options.contains("Hash")){
                send("setoption name Hash value "+hashMb);hash=hashMb;
            }
            if(pv!=multiPv&&options.contains("MultiPV")){
                send("setoption name MultiPV value "+multiPv);pv=multiPv;
            }
            ready();
        }
    }
    void position(String fen,List<String> moves) throws Exception {
        if(fen==null||fen.indexOf('\n')>=0||fen.indexOf('\r')>=0
           ||moves==null||moves.size()>2048)throw new IOException("잘못된 장기 국면");
        for(String move:moves)if(!move.matches("[a-i](10|[1-9])[a-i](10|[1-9])"))
            throw new IOException("잘못된 수순: "+move);
        send("position fen "+fen+(moves.isEmpty()?"":" moves "+String.join(" ",moves)));
    }
    String search(int ms,BooleanSupplier valid,Info info) throws Exception {
        synchronized(responseLock){
            if(!valid.getAsBoolean())return null;
            send("go movetime "+Math.max(1,ms));
            String result=until("bestmove ",Math.max(1000L,ms+30000L),info);
            String[] fields=result.split(" +");
            if(fields.length<2||!fields[1].matches("[a-i](10|[1-9])[a-i](10|[1-9])"))
                throw new IOException("엔진이 잘못된 착수 형식을 반환: "+result);
            return fields[1];
        }
    }
    void newGame() throws Exception {synchronized(responseLock){send("ucinewgame");ready();}}
    void stop(){try{send("stop");}catch(Exception ignored){}}
    synchronized void close() {
        if(closed)return;
        try{send("quit");}catch(Exception ignored){}
        closed=true;process.destroy();
    }
    String variant(){return selectedVariant;}
    String activeNetwork(){return network;}
}
