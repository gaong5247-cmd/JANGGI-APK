package org.janggilab;

import java.util.*;

public final class GameReviewIntegration {
    private static void check(boolean ok,String message){
        if(!ok)throw new AssertionError(message);
    }

    private static String firstPlayable(Engine.State state){
        for(String move:state.legal){
            String[] q=GameReview.splitMove(move);
            if(q!=null&&!q[0].equals(q[1]))return move;
        }
        return state.legal.isEmpty()?null:state.legal.get(0);
    }

    public static void main(String[] args) throws Exception {
        if(args.length<1)throw new IllegalArgumentException("engine path required");
        Engine engine=new Engine(args[0],VariantConfig.IDS[0]);
        try{
            Engine.State start=engine.state();
            check(start.startFen!=null&&!start.startFen.isEmpty(),"missing start FEN");
            ArrayList<String> moves=new ArrayList<>();
            for(int i=0;i<4;i++){
                engine.position(start.startFen,moves);
                Engine.State s=engine.state();
                check(s.ongoing(),"generated test game ended too early");
                String move=firstPlayable(s);
                check(move!=null,"no legal move for generated test game");
                moves.add(move);
            }

            GameReview.Result review=GameReview.analyze(
                engine,start.startFen,moves,80,1,16,()->true,null
            );
            check(review.items.size()==moves.size(),"review did not cover every generated move");
            for(GameReview.Item item:review.items){
                check(item.bestMove!=null&&!item.bestMove.isEmpty(),"missing best move");
                check(item.kind!=null,"missing move classification");
                check(item.bestLine!=null&&!item.bestLine.isEmpty(),"missing PV");
            }
            System.out.println("GameReviewIntegration OK moves="+String.join(" ",moves));
        } finally {
            engine.close();
        }
    }
}
