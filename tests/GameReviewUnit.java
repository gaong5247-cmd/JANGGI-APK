package org.janggilab;

public final class GameReviewUnit {
    private static void eq(GameReview.Kind got,GameReview.Kind want,String name){
        if(got!=want)throw new AssertionError(name+": "+got+" != "+want);
    }
    private static void ok(boolean value,String name){
        if(!value)throw new AssertionError(name);
    }
    public static void main(String[] args){
        eq(GameReview.classify(true,true,.70,.70,.60),GameReview.Kind.BRILLIANT,"brilliant sacrifice");
        eq(GameReview.classify(true,false,.70,.70,.55),GameReview.Kind.GREAT,"only move");
        eq(GameReview.classify(true,false,.70,.70,.66),GameReview.Kind.BEST,"best");
        eq(GameReview.classify(false,false,.70,.685,.68),GameReview.Kind.EXCELLENT,"excellent");
        eq(GameReview.classify(false,false,.70,.66,.65),GameReview.Kind.GOOD,"good");
        eq(GameReview.classify(false,false,.65,.57,.56),GameReview.Kind.INACCURACY,"inaccuracy");
        eq(GameReview.classify(false,false,.65,.50,.49),GameReview.Kind.MISTAKE,"mistake");
        eq(GameReview.classify(false,false,.82,.55,.54),GameReview.Kind.MISS,"miss");
        eq(GameReview.classify(false,false,.65,.30,.29),GameReview.Kind.BLUNDER,"blunder");
        ok(GameReview.moveAccuracy(0)>GameReview.moveAccuracy(.10),"accuracy monotonic 1");
        ok(GameReview.moveAccuracy(.10)>GameReview.moveAccuracy(.30),"accuracy monotonic 2");
        String[] move=GameReview.splitMove("a10i9");
        ok(move!=null&&"a10".equals(move[0])&&"i9".equals(move[1]),"10th rank move split");
        char rook=GameReview.pieceAt("8r/9/9/9/9/9/9/9/9/R8 w - - 0 1","a1");
        ok(rook=='R',"fen square parser");
        System.out.println("GameReviewUnit OK");
    }
}
