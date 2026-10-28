/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.janggilab;

import java.io.*;
import java.util.*;

/** Profiles are APK-packaged native executables, not writable-data ELF files. */
final class EngineProfile {
    static final String FAIRY="fairy";
    static final String DOTPROD="fairy-dotprod";
    static final String STOCKLESS="stockless";
    static final String CUSTOM="custom";
    static final String[] IDS={FAIRY,DOTPROD,STOCKLESS,CUSTOM};
    static String saved(String id) {
        for(String value:IDS) if(value.equals(id)) return value;
        return FAIRY;
    }
    static String title(String id) {
        switch(saved(id)) {
            case DOTPROD: return "FSF DOTPROD";
            case STOCKLESS: return "Stockless Variants";
            case CUSTOM: return "사용자 빌드 UCI";
            default: return "Fairy-Stockfish";
        }
    }
    static String library(String id) {
        switch(saved(id)) {
            case DOTPROD: return "libfairy_dotprod.so";
            case STOCKLESS: return "libstockless.so";
            case CUSTOM: return "libcustom.so";
            default: return "libfairy.so";
        }
    }
    static boolean supportsDotprod() {
        // Fail closed when Android masks CPU flags. An unsupported binary can SIGILL.
        try(BufferedReader r=new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while((line=r.readLine())!=null) {
                String s=line.toLowerCase(Locale.ROOT);
                if((s.startsWith("features")||s.startsWith("flags"))
                    && (Arrays.asList(s.split("[\\s:]+")).contains("asimddp")
                        || Arrays.asList(s.split("[\\s:]+")).contains("dotprod"))) return true;
            }
        } catch(IOException ignored) {}
        return false;
    }
    static boolean available(File nativeDir,String id) {
        if(DOTPROD.equals(id)&&!supportsDotprod()) return false;
        File f=new File(nativeDir,library(id));
        return f.isFile()&&f.length()>=4096;
    }
    static String locate(File nativeDir,String id) throws IOException {
        if(DOTPROD.equals(id)&&!supportsDotprod())
            throw new IOException("CPU DOTPROD 지원을 확인하지 못했습니다. 일반 ARMv8 엔진을 선택하세요.");
        File f=new File(nativeDir,library(id));
        if(!f.isFile()||f.length()<4096||!f.canExecute())
            throw new IOException(title(id)+" 실행 파일이 APK에 포함되지 않았습니다: "+f.getName());
        return f.getAbsolutePath();
    }
    static String[] labels(File nativeDir) {
        String[] labels=new String[IDS.length];
        for(int i=0;i<IDS.length;i++)
            labels[i]=title(IDS[i])+(available(nativeDir,IDS[i])?"":" · 사용 불가");
        return labels;
    }
    static int index(String id) {
        for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;
        return 0;
    }
}
