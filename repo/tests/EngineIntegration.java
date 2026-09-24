package org.janggilab;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
public final class EngineIntegration {
 static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
 static void stateChecks(Engine a,String fen)throws Exception {
  a.position(fen,Collections.emptyList());Engine.State initial=a.state();
  check(initial.ongoing()&&initial.reason.equals("none")&&!initial.check&&initial.legal.size()==32,"start state");
  String capture="9/3k5/9/p8/9/9/R8/9/4K4/9 w - - 0 1";
  a.position(capture,Arrays.asList("a4a7","d9d9"));Engine.State before=a.state();
  ArrayList<String> scores=new ArrayList<>();
  a.search(150,()->true,line->{if(line.contains(" score mate "))scores.add(AnalysisScore.format(line));});
  Engine.State after=a.state();
  check(!scores.isEmpty(),"material win must produce mate search score");
  check(after.ongoing()&&after.reason.equals("none")&&after.fen.equals(before.fen)&&after.legal.equals(before.legal),"mate search changed root state");
  a.position("9/3k5/9/9/9/9/9/3rrr3/9/4K4 w - - 0 1",Collections.emptyList());
  Engine.State mate=a.state();
  check(mate.result.equals("loss")&&mate.reason.equals("checkmate")&&mate.check&&mate.legal.isEmpty(),"actual checkmate");
  check(!mate.canPlay("e1e1"),"terminal pass allowed");
  a.position(fen,Arrays.asList("b1c3","b10c8","c3b1","c8b10","b1c3","b10c8","c3b1","c8b10"));
  Engine.State repeated=a.state();
  check(repeated.reason.equals("repetition")&&!repeated.ongoing()&&repeated.legal.isEmpty(),"terminal repetition leaked moves");
  check(AnalysisScore.format("info score mate 5 pv a1a2").startsWith("M5"),"positive mate label");
  check(AnalysisScore.format("info score mate -3 pv a1a2").startsWith("-M3"),"negative mate label");
  check(!AnalysisScore.format("info score mate 0 pv a1a2").contains("외통"),"mate zero is not current checkmate");
  check(AnalysisScore.format("info score cp -125 pv a1a2").equals("-1.25"),"cp label");
  check(AnalysisScore.format("info score mate 5 lowerbound pv a1a2").contains("경계값"),"bound label");
  List<String> frame=Arrays.asList("appfen "+fen,"applegal", "appcheck 1", "appresult loss", "appreason checkmate", "appbikjang 0");
  check(Engine.State.parse(frame).legal.isEmpty(),"empty legal list gained phantom move");
  for(int i=0;i<frame.size();i++){
   ArrayList<String> incomplete=new ArrayList<>(frame);incomplete.remove(i);
   try{Engine.State.parse(incomplete);throw new AssertionError("accepted incomplete state "+i);}catch(java.io.IOException expected){}
  }
  ArrayList<String> malformed=new ArrayList<>(frame);malformed.set(3,"appresult ongoing");
  try{Engine.State.parse(malformed);throw new AssertionError("accepted contradictory state");}catch(java.io.IOException expected){}
 }
 public static void main(String[] args)throws Exception {
  Engine a=new Engine(args[0]), b=new Engine(args[0]);
  String fen="rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1";
  ArrayList<String> moves=new ArrayList<>();
  try{
   stateChecks(a,fen);
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
   Path bad=Files.createTempFile("janggi-invalid-", ".nnue");
   Files.write(bad,new byte[]{1,2,3});Engine probe=new Engine(args[0]);
   try {try{probe.useNetwork(bad.toString());throw new AssertionError("invalid NNUE accepted");}catch(java.io.IOException expected){}}
   finally{probe.close();Files.delete(bad);}
   a.position(fen,Collections.emptyList());check(a.state().ongoing(),"failed probe damaged live engine");
   if(args.length>1){Engine net=new Engine(args[0]);try{net.useNetwork(args[1]);stateChecks(net,fen);}finally{net.close();}}
   System.out.println("PASS: state/result/reason parsing, mate score separation, empty/missing/contradictory frames, terminal input guard, NNUE rejection isolation, 20 alternating plies, MultiPV stop, resynchronization, pre-cancelled search.");
  }finally{a.close();b.close();}
 }
}
