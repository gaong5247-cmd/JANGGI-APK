/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Distributed WITHOUT ANY WARRANTY; see LICENSE.
 */
package org.janggilab;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Offline, engine-backed move review.
 *
 * Chess.com's current move review is based on expected-points loss rather than
 * raw centipawn loss. Janggi Lab uses the same idea, but with a transparent
 * Janggi-specific approximation because no rating-conditioned Janggi win
 * probability model exists in the app.
 */
final class GameReview {
    enum Kind {
        BRILLIANT("!!","탁월한 수",0xff29b6a8,true),
        GREAT("!","훌륭한 수",0xff42a5f5,true),
        BEST("★","최선의 수",0xff66bb6a,false),
        EXCELLENT("✓","매우 좋은 수",0xff81c784,false),
        GOOD("✓","좋은 수",0xff9ccc65,false),
        INACCURACY("?!","부정확",0xffffca28,true),
        MISTAKE("?","실수",0xffff8f3d,true),
        MISS("×","놓친 기회",0xffec6aa8,true),
        BLUNDER("??","큰 실수",0xffef5350,true);

        final String symbol,label;
        final int color;
        final boolean key;
        Kind(String symbol,String label,int color,boolean key){
            this.symbol=symbol;this.label=label;this.color=color;this.key=key;
        }
    }

    static final class Score {
        final boolean mate;
        final int value;
        Score(boolean mate,int value){this.mate=mate;this.value=value;}
        Score negate(){return new Score(mate,-value);}
        double expected(){
            if(mate){
                if(value>0)return .999;
                if(value<0)return .001;
                return .5;
            }
            int cp=Math.max(-3000,Math.min(3000,value));
            return 1.0/(1.0+Math.exp(-cp/420.0));
        }
        String label(){
            if(mate)return value<0?"-M"+Math.abs(value):"M"+Math.abs(value);
            return String.format(Locale.US,"%+.2f",value/100.0);
        }
    }

    static final class Line {
        final int multipv,depth;
        final Score score;
        final String[] moves;
        Line(int multipv,int depth,Score score,String[] moves){
            this.multipv=multipv;this.depth=depth;this.score=score;this.moves=moves;
        }
        String first(){return moves.length==0?"":moves[0];}
        String text(){
            return moves.length==0?"":String.join(" ",Arrays.copyOf(moves,Math.min(8,moves.length)));
        }
    }

    static final class Item {
        int ply;
        boolean moverWhite;
        String fenBefore;
        final ArrayList<String> legal=new ArrayList<>();
        String playedMove,bestMove,bestLine;
        String bestScore,playedScore;
        double bestExpected,playedExpected,loss,secondExpected;
        Kind kind;
        String coach;
        double whiteExpectedAfter(){
            return moverWhite?playedExpected:1.0-playedExpected;
        }
    }

    static final class Result {
        final ArrayList<Item> items=new ArrayList<>();
        final EnumMap<Kind,Integer> counts=new EnumMap<>(Kind.class);
        double whiteAccuracy,blackAccuracy;
        Item biggestSwing;
        int whiteMoves,blackMoves;

        Result(){for(Kind k:Kind.values())counts.put(k,0);}

        String summary(){
            StringBuilder s=new StringBuilder();
            s.append(String.format(Locale.US,"초 정확도 %.1f · 한 정확도 %.1f",whiteAccuracy,blackAccuracy));
            if(biggestSwing!=null){
                s.append("\n가장 큰 흔들림: ").append(biggestSwing.ply).append("수 ")
                    .append(biggestSwing.moverWhite?"초 ":"한 ")
                    .append(biggestSwing.kind.symbol).append(" ").append(biggestSwing.kind.label);
            }
            s.append("\n핵심: !! ").append(counts.get(Kind.BRILLIANT))
                .append(" · ! ").append(counts.get(Kind.GREAT))
                .append(" · ?! ").append(counts.get(Kind.INACCURACY))
                .append(" · ? ").append(counts.get(Kind.MISTAKE))
                .append(" · × ").append(counts.get(Kind.MISS))
                .append(" · ?? ").append(counts.get(Kind.BLUNDER));
            return s.toString();
        }
    }

    interface Progress { void update(int done,int total); }

    private static final class Collector implements Engine.Info {
        final HashMap<Integer,Line> lines=new HashMap<>();
        @Override public void line(String line){
            if(!line.startsWith("info ")||!line.contains(" score ")||!line.contains(" pv "))return;
            int pv=intToken(line,"multipv",1),depth=intToken(line,"depth",0);
            Score score=parseScore(line);
            if(score==null)return;
            int at=line.indexOf(" pv ");
            String tail=line.substring(at+4).trim();
            if(tail.isEmpty())return;
            String[] moves=tail.split(" +");
            Line old=lines.get(pv);
            if(old==null||depth>=old.depth)lines.put(pv,new Line(pv,depth,score,moves));
        }
        Line get(int pv){return lines.get(pv);}
        Line byFirst(String move){
            for(Line l:lines.values())if(move.equals(l.first()))return l;
            return null;
        }
    }

    static Result analyze(
        Engine engine,String initialFen,List<String> gameMoves,int movetimeMs,
        int threads,int hash,BooleanSupplier valid,Progress progress
    ) throws Exception {
        if(gameMoves.isEmpty())throw new IOException("리뷰할 수가 없습니다");
        Result result=new Result();
        ArrayList<String> prefix=new ArrayList<>();
        double whiteAccuracyTotal=0,blackAccuracyTotal=0;

        for(int i=0;i<gameMoves.size();i++){
            if(!valid.getAsBoolean())throw new CancellationException();
            String played=gameMoves.get(i);
            engine.position(initialFen,prefix);
            Engine.State before=engine.state();
            if(!before.ongoing())break;
            if(!before.canPlay(played))
                throw new IOException((i+1)+"수 "+played+"는 현재 규칙에서 합법수가 아닙니다");

            engine.configure(20,threads,hash,3);
            engine.position(initialFen,prefix);
            Collector pre=new Collector();
            engine.search(movetimeMs,valid,pre);
            if(!valid.getAsBoolean())throw new CancellationException();
            Line best=pre.get(1);
            if(best==null||best.first().isEmpty())
                throw new IOException((i+1)+"수의 최선수를 분석하지 못했습니다");

            double bestEp=best.score.expected();
            double secondEp=pre.get(2)==null?bestEp:pre.get(2).score.expected();
            Line playedLine=pre.byFirst(played);
            Score playedScore;
            double playedEp;
            if(playedLine!=null){
                playedScore=playedLine.score;
                playedEp=playedScore.expected();
            }else{
                ArrayList<String> afterMoves=new ArrayList<>(prefix);
                afterMoves.add(played);
                engine.position(initialFen,afterMoves);
                Engine.State after=engine.state();
                if(!after.ongoing()){
                    if("draw".equals(after.result))playedEp=.5;
                    else if("loss".equals(after.result))playedEp=.999;
                    else playedEp=.001;
                    playedScore=new Score(true,playedEp>.5?1:-1);
                }else{
                    engine.configure(20,threads,hash,1);
                    engine.position(initialFen,afterMoves);
                    Collector post=new Collector();
                    engine.search(movetimeMs,valid,post);
                    if(!valid.getAsBoolean())throw new CancellationException();
                    Line reply=post.get(1);
                    if(reply==null)throw new IOException((i+1)+"수 이후 평가를 읽지 못했습니다");
                    playedScore=reply.score.negate();
                    playedEp=1.0-reply.score.expected();
                }
            }

            double loss=Math.max(0,bestEp-playedEp);
            boolean exactBest=played.equals(best.first());
            boolean sacrifice=exactBest&&isGoodSacrifice(before.fen,best.moves,bestEp);
            Kind kind=classify(exactBest,sacrifice,bestEp,playedEp,secondEp);

            Item item=new Item();
            item.ply=i+1;item.moverWhite=before.white;item.fenBefore=before.fen;
            item.legal.addAll(before.legal);item.playedMove=played;item.bestMove=best.first();
            item.bestLine=best.text();item.bestScore=best.score.label();item.playedScore=playedScore.label();
            item.bestExpected=bestEp;item.playedExpected=playedEp;item.loss=loss;item.secondExpected=secondEp;
            item.kind=kind;item.coach=coach(item,sacrifice);
            result.items.add(item);result.counts.put(kind,result.counts.get(kind)+1);
            if(result.biggestSwing==null||loss>result.biggestSwing.loss)result.biggestSwing=item;

            double acc=moveAccuracy(loss);
            if(before.white){whiteAccuracyTotal+=acc;result.whiteMoves++;}
            else {blackAccuracyTotal+=acc;result.blackMoves++;}
            prefix.add(played);
            if(progress!=null)progress.update(i+1,gameMoves.size());
        }

        result.whiteAccuracy=result.whiteMoves==0?0:whiteAccuracyTotal/result.whiteMoves;
        result.blackAccuracy=result.blackMoves==0?0:blackAccuracyTotal/result.blackMoves;
        return result;
    }

    static Kind classify(boolean exactBest,boolean sacrifice,double best,double actual,double second){
        double loss=Math.max(0,best-actual);
        if(exactBest&&sacrifice&&best>=.48&&best<=.95)return Kind.BRILLIANT;
        if(exactBest&&best-second>=.10)return Kind.GREAT;
        if(exactBest)return Kind.BEST;
        if(loss>=.10&&best>=.70&&actual<.60)return Kind.MISS;
        if(loss<=.02)return Kind.EXCELLENT;
        if(loss<=.05)return Kind.GOOD;
        if(loss<=.10)return Kind.INACCURACY;
        if(loss<=.20)return Kind.MISTAKE;
        return Kind.BLUNDER;
    }

    static double moveAccuracy(double loss){
        return 100.0*Math.exp(-2.5*Math.max(0,Math.min(1,loss)));
    }

    private static String coach(Item x,boolean sacrifice){
        int before=(int)Math.round(x.bestExpected*100),after=(int)Math.round(x.playedExpected*100);
        String delta=(int)Math.round(x.loss*100)+"%p";
        switch(x.kind){
            case BRILLIANT:
                return "쉽게 보이지 않는 희생을 감수하면서도 최선 평가를 유지했습니다. "
                    +x.bestMove+"가 핵심입니다.";
            case GREAT:
                return "사실상 유일한 좋은 수에 가깝습니다. 2순위 후보보다 결과 기대값이 크게 좋습니다.";
            case BEST:
                return "엔진의 1순위와 정확히 일치합니다. 이 국면에서 가장 강한 선택입니다.";
            case EXCELLENT:
                return "최선수와 거의 같은 결과를 유지했습니다. "+x.bestMove+"도 함께 비교해보세요.";
            case GOOD:
                return "충분히 괜찮은 수지만 "+x.bestMove+"가 조금 더 정확했습니다.";
            case INACCURACY:
                return "조금 아쉬운 수입니다. 기대값이 "+before+"%에서 "+after+"%로 약 "+delta+" 내려갔습니다.";
            case MISTAKE:
                return "이 수로 형세가 눈에 띄게 나빠졌습니다. "+x.bestMove+"였다면 훨씬 버틸 수 있었습니다.";
            case MISS:
                return "상대의 실수를 강하게 벌할 기회를 놓쳤습니다. "+x.bestMove+"가 승부를 크게 기울일 수였습니다.";
            case BLUNDER:
            default:
                return "승부에 큰 영향을 준 실수입니다. "+x.bestMove+"와 실제 수를 퍼즐처럼 다시 비교해보세요.";
        }
    }

    private static boolean isGoodSacrifice(String fen,String[] pv,double bestEp){
        if(pv.length<2||bestEp<.48)return false;
        String[] first=BoardView.splitMove(pv[0]),reply=BoardView.splitMove(pv[1]);
        if(first==null||reply==null||first[0].equals(first[1]))return false;
        if(!reply[1].equals(first[1]))return false;
        char moving=pieceAt(fen,first[0]),captured=pieceAt(fen,first[1]);
        int own=pieceValue(moving),gain=pieceValue(captured);
        return own>=5&&own-gain>=2;
    }

    static char pieceAt(String fen,String square){
        int[] q=BoardView.parse(square);
        if(q[0]<0)return ' ';
        String[] rows=fen.split(" ")[0].split("/");
        int targetRow=9-q[1];
        if(targetRow<0||targetRow>=rows.length)return ' ';
        int file=0;
        for(char ch:rows[targetRow].toCharArray()){
            if(Character.isDigit(ch)){file+=ch-'0';continue;}
            if(file==q[0])return ch;
            file++;
        }
        return ' ';
    }

    static int pieceValue(char piece){
        switch(Character.toLowerCase(piece)){
            case 'r':return 13;
            case 'c':return 7;
            case 'n':return 5;
            case 'b':case 'e':case 'a':return 3;
            case 'p':return 2;
            case 'k':return 100;
            default:return 0;
        }
    }

    private static int intToken(String line,String key,int fallback){
        String[] t=line.split(" +");
        for(int i=0;i<t.length-1;i++)if(key.equals(t[i])){
            try{return Integer.parseInt(t[i+1]);}catch(Exception ignored){return fallback;}
        }
        return fallback;
    }

    private static Score parseScore(String line){
        String[] t=line.split(" +");
        for(int i=0;i<t.length-2;i++)if("score".equals(t[i])){
            try{
                if("cp".equals(t[i+1]))return new Score(false,Integer.parseInt(t[i+2]));
                if("mate".equals(t[i+1]))return new Score(true,Integer.parseInt(t[i+2]));
            }catch(Exception ignored){return null;}
        }
        return null;
    }
}
