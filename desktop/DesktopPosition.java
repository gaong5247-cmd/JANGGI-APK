/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.janggilab;

import java.util.Arrays;

/** UCI board coordinates are canonical; screen orientation never changes a FEN. */
final class DesktopPosition {
    static final String START="rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1";
    final char[][] squares=new char[10][9]; // rank 1 at index 0
    boolean white=true;
    DesktopPosition(){load(START);}
    DesktopPosition(String fen){load(fen);}
    void load(String fen){
        String[] fields=fen.trim().split("\\s+");
        if(fields.length<2||(!fields[1].equals("w")&&!fields[1].equals("b")))
            throw new IllegalArgumentException("잘못된 FEN 둘 차례");
        String[] rows=fields[0].split("/");
        if(rows.length!=10)throw new IllegalArgumentException("FEN은 10행이어야 합니다");
        for(char[] row:squares)Arrays.fill(row,' ');
        for(int r=0;r<10;r++){
            int f=0;
            for(char p:rows[r].toCharArray()){
                if(p>='1'&&p<='9')f+=p-'0';
                else if("kabnrcpKABNRCP".indexOf(p)>=0&&f<9)squares[9-r][f++]=p;
                else throw new IllegalArgumentException("잘못된 FEN 기물: "+p);
            }
            if(f!=9)throw new IllegalArgumentException("FEN 행의 칸 수가 9가 아닙니다: "+(r+1));
        }
        white=fields[1].equals("w");
    }
    char piece(String square){
        int[] at=parse(square);
        return at==null?' ':squares[at[1]][at[0]];
    }
    void set(String square,char piece){
        if(piece!=' '&&"kabnrcpKABNRCP".indexOf(piece)<0)
            throw new IllegalArgumentException("지원하지 않는 기물");
        int[] at=parse(square);
        if(at==null)throw new IllegalArgumentException("범위 밖 좌표: "+square);
        squares[at[1]][at[0]]=piece;
    }
    String toFen(){
        StringBuilder out=new StringBuilder();
        for(int r=9;r>=0;r--){
            int blanks=0;
            for(int f=0;f<9;f++){
                char p=squares[r][f];
                if(p==' ')blanks++;
                else{if(blanks!=0)out.append(blanks);blanks=0;out.append(p);}
            }
            if(blanks!=0)out.append(blanks);
            if(r!=0)out.append('/');
        }
        return out.append(white?" w - - 0 1":" b - - 0 1").toString();
    }
    static String coord(int file,int rank){return ""+(char)('a'+file)+(rank+1);}
    static int[] parse(String s){
        if(s==null||!s.matches("[a-i](10|[1-9])"))return null;
        return new int[]{s.charAt(0)-'a',Integer.parseInt(s.substring(1))-1};
    }
    static String[] splitMove(String move){
        if(move==null||!move.matches("[a-i](10|[1-9])[a-i](10|[1-9])"))return null;
        int cut=move.charAt(2)=='0'?3:2;
        return new String[]{move.substring(0,cut),move.substring(cut)};
    }
    static String viewToSquare(int xIndex,int yIndex,boolean flipped){
        if(xIndex<0||xIndex>8||yIndex<0||yIndex>9)return null;
        int f=flipped?8-xIndex:xIndex;
        int rank=flipped?yIndex:9-yIndex;
        return coord(f,rank);
    }
    static int[] squareToView(String square,boolean flipped){
        int[] p=parse(square);
        if(p==null)return null;
        return new int[]{flipped?8-p[0]:p[0],flipped?p[1]:9-p[1]};
    }
    String validate(){
        int cho=0,han=0;
        for(int r=0;r<10;r++)for(int f=0;f<9;f++){
            char p=squares[r][f];
            char kind=Character.toLowerCase(p);
            if(p==' ')continue;
            if(kind=='k') {if(Character.isUpperCase(p))cho++;else han++;}
            if(kind=='k'||kind=='a'){
                boolean palace=f>=3&&f<=5&&(Character.isUpperCase(p)?r<=2:r>=7);
                if(!palace)return "궁과 사는 소속 진영의 궁성 안에 있어야 합니다";
            }
        }
        return cho==1&&han==1?null:"초와 한의 궁을 하나씩 배치해야 합니다";
    }
}
