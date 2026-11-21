package org.janggilab;

public final class DesktopPositionTest {
    static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] args){
        DesktopPosition original=new DesktopPosition();
        check(original.toFen().equals(DesktopPosition.START),"start FEN round trip");
        check(original.validate()==null,"initial kings valid");
        for(boolean flip:new boolean[]{false,true}){
            for(int r=0;r<10;r++)for(int f=0;f<9;f++){
                String uci=DesktopPosition.coord(f,r);
                int[] xy=DesktopPosition.squareToView(uci,flip);
                check(uci.equals(DesktopPosition.viewToSquare(xy[0],xy[1],flip)),
                    "view coordinate mismatch "+flip+" "+uci);
            }
        }
        check("a1".equals(DesktopPosition.viewToSquare(0,9,false)),"cho bottom-left");
        check("i10".equals(DesktopPosition.viewToSquare(0,9,true)),"han bottom-left");
        DesktopPosition edited=new DesktopPosition();
        edited.set(DesktopPosition.viewToSquare(2,5,false),'R');
        edited.set(DesktopPosition.viewToSquare(6,4,true),'p');
        edited.white=false;
        check(new DesktopPosition(edited.toFen()).toFen().equals(edited.toFen()),"edit round trip");
        check(edited.validate()==null,"valid edit rejected");
        edited.set("e2",' ');
        check(edited.validate()!=null,"missing general accepted");
        check(DesktopPosition.splitMove("e2e10")[1].equals("e10"),"rank 10 parse");
        check(DesktopPosition.splitMove("e10e2")[0].equals("e10"),"rank 10 start parse");
        for(String v:VariantConfig.IDS)check(VariantConfig.description(v).length()>10,"missing variant description");
        System.out.println("PASS: 180 perspective square transforms, initial/edit FEN, validation, two-digit ranks, four variant descriptions");
    }
}
