package org.janggilab;
import java.util.*;
import java.util.concurrent.*;
public final class EngineIntegration {
 public static void main(String[] args)throws Exception {
  Engine a=new Engine(args[0]), b=new Engine(args[0]);
  String fen="rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1";
  ArrayList<String> moves=new ArrayList<>();
  try{
   for(int i=0;i<20;i++){
    Engine e=i%2==0?a:b;e.position(fen,moves);Engine.State st=e.state();
    e.configure(i%2==0?20:10,1,16,1);
    String m=e.search(30,()->true,null);
    if(!st.legal.contains(m))throw new AssertionError("illegal "+m);
    moves.add(m);
   }
   a.position(fen,Collections.emptyList());a.configure(20,1,16,3);
   ExecutorService ex=Executors.newSingleThreadExecutor();
   Future<String> pending=ex.submit(()->a.search(600000,()->true,null));
   Thread.sleep(300);a.stop();String best=pending.get(5,TimeUnit.SECONDS);
   a.ready();a.position(fen,Collections.emptyList());
   if(!a.state().legal.contains(best))throw new AssertionError("invalid analysis bestmove");
   if(a.search(600000,()->false,null)!=null)throw new AssertionError("stale search ran");
   a.ready();ex.shutdown();
   System.out.println("PASS: Java Engine bridge; 20 alternating legal plies; MultiPV stop; resynchronization; pre-cancelled search.");
  }finally{a.close();b.close();}
 }
}
