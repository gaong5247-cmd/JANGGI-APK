package org.janggilab;
import java.util.*;
import java.io.*;
import java.util.concurrent.*;

public final class SearchEngineIntegration {
    static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    static final class SearchWrapper implements AutoCloseable {
        final SearchEngine engine;
        SearchWrapper(String exe,String profile,String config)throws Exception{
            engine=new SearchEngine(exe,profile,VariantConfig.IDS[0],config,"");
        }
        public void close(){engine.close();}
    }
    public static void main(String[] args)throws Exception {
        String fairy=args[0],config=args[1];
        if(!"SKIP".equals(fairy))try(SearchWrapper w=new SearchWrapper(fairy,EngineProfile.DOTPROD,config)){
            w.engine.position("rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1",Collections.emptyList());
            String best=w.engine.search(100,()->true,null);
            check(best!=null,"Fairy UCI smoke");
            for(String variant:VariantConfig.IDS)w.engine.setVariant(variant);
        }
        if(args.length>2&&!"SKIP".equals(args[2]))try(SearchWrapper w=new SearchWrapper(args[2],EngineProfile.STOCKLESS,config)){
            for(String variant:VariantConfig.IDS){
                w.engine.setVariant(variant);
                w.engine.configure(15,1,16,1);
                w.engine.position("rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1",Collections.emptyList());
                String best=w.engine.search(100,()->true,null);
                check(best!=null,"Stockless failed: "+variant);
            }
        }
        if(args.length>3) {
            try(SearchWrapper w=new SearchWrapper(args[3],EngineProfile.DOTPROD,config)){
                w.engine.configure(15,1,16,1);
                for(int i=0;i<100;i++){
                    String best=w.engine.search(1,()->true,null);
                    check("e2e2".equals(best),"Fake search result "+i);
                }
                check(w.engine.search(10,()->false,null)==null,"Pre-cancelled search ran");
                try {
                    w.engine.search(779,()->true,null);
                    throw new AssertionError("Invalid UCI bestmove was accepted");
                }catch(IOException expected){}
                // This exercises the FSF-NNUE protocol handshake, not real NNUE math.
                java.nio.file.Path dummy=java.nio.file.Files.createTempFile("janggi-fake-", ".nnue");
                try{
                    java.nio.file.Files.write(dummy,new byte[]{1,2,3,4});
                    w.engine.useNetwork(dummy.toAbsolutePath().toString());
                    check(w.engine.activeNetwork().equals(dummy.toAbsolutePath().toString()),
                          "Mock NNUE handshake not recorded");
                }finally{java.nio.file.Files.deleteIfExists(dummy);}
                try {
                    w.engine.search(777,()->true,null);
                    throw new AssertionError("Engine exit went undetected");
                }catch(IOException expected){}
            }
        }
        System.out.println("PASS UCI protocol, 100 fast searches, invalid-move rejection, process-exit detection, NNUE handshake");
    }
}
