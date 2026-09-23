/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Distributed WITHOUT ANY WARRANTY; see LICENSE.
 */
package org.janggilab;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

final class BoardView extends View {
    interface Tap { void square(String square); }
    interface Drag { void move(String from,String to); }
    final Paint p=new Paint(3);
    final char[][] pieces=new char[10][9];
    final Set<String> targets=new HashSet<>();
    String selected="", last="", hint="";
    boolean flipped;
    float cell, ox, oy;
    Tap tap;
    Drag drag;
    String downSquare="";
    BoardView(Context c) { super(c); setContentDescription("장기판. 기물을 선택하고 표시된 합법수 위치를 누르세요."); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
    void fen(String fen) {
        for(char[] row:pieces) Arrays.fill(row,' ');
        String[] rows=fen.split(" ")[0].split("/");
        for(int r=0;r<10;r++) {
            int f=0;
            for(char ch:rows[r].toCharArray()) {
                if(Character.isDigit(ch)) f+=ch-'0'; else if(f<9) pieces[9-r][f++]=ch;
            }
        }
        invalidate();
    }
    void setPiece(String square,char piece) {
        int[] a=parse(square);
        if(a[0]>=0&&a[0]<9&&a[1]>=0&&a[1]<10) { pieces[a[1]][a[0]]=piece; invalidate(); }
    }
    char piece(String square) {
        int[] a=parse(square);
        return a[0]>=0&&a[0]<9&&a[1]>=0&&a[1]<10?pieces[a[1]][a[0]]:' ';
    }
    String toFen(boolean white) {
        StringBuilder out=new StringBuilder();
        for(int rank=9;rank>=0;rank--) {
            int empty=0;
            for(int file=0;file<9;file++) {
                char ch=pieces[rank][file];
                if(ch==' ') { empty++; continue; }
                if(empty>0) { out.append(empty); empty=0; }
                out.append(ch);
            }
            if(empty>0)out.append(empty);
            if(rank>0)out.append('/');
        }
        return out.append(white?" w - - 0 1":" b - - 0 1").toString();
    }
    static int[] parse(String s) {
        try { return new int[]{s.charAt(0)-'a',Integer.parseInt(s.substring(1))-1}; }
        catch(Exception e) { return new int[]{-1,-1}; }
    }
    static String[] splitMove(String move) {
        if(move==null || !move.matches("[a-i](10|[1-9])[a-i](10|[1-9])")) return null;
        int cut=move.charAt(2)=='0'?3:2;
        return new String[]{move.substring(0,cut),move.substring(cut)};
    }
    float x(int f) { return ox+(flipped?8-f:f)*cell; }
    float y(int r) { return oy+(flipped?r:9-r)*cell; }
    void color(int c) { p.setColor(c); p.setStyle(Paint.Style.FILL); p.setStrokeWidth(1); p.clearShadowLayer(); }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        cell=Math.min((getWidth()-28f)/9,(getHeight()-20f)/10);
        ox=(getWidth()-8*cell)/2; oy=(getHeight()-9*cell)/2;
        color(Color.rgb(237,219,181)); c.drawRoundRect(4,4,getWidth()-4,getHeight()-4,18,18,p);
        color(0xff998566); p.setStrokeWidth(1.5f);
        for(int f=0;f<9;f++) c.drawLine(x(f),y(0),x(f),y(9),p);
        for(int r=0;r<10;r++) c.drawLine(x(0),y(r),x(8),y(r),p);
        c.drawLine(x(3),y(0),x(5),y(2),p); c.drawLine(x(5),y(0),x(3),y(2),p);
        c.drawLine(x(3),y(7),x(5),y(9),p); c.drawLine(x(5),y(7),x(3),y(9),p);
        String[] lm=splitMove(last);
        if(lm!=null) for(String sq:lm) { int[] a=parse(sq); color(0x55499786); c.drawCircle(x(a[0]),y(a[1]),cell*.46f,p); }
        for(int r=0;r<10;r++) for(int f=0;f<9;f++) {
            char ch=pieces[r][f]; if(ch==' ')continue;
            float xx=x(f), yy=y(r), rad=cell*(Character.toLowerCase(ch)=='k'?.43f:.38f);
            color(0xffFFF5DC); p.setShadowLayer(2,0,2,0x554B3620);
            Path path=new Path();
            for(int k=0;k<8;k++) { double a=Math.PI/8+k*Math.PI/4; float px=xx+(float)Math.cos(a)*rad,py=yy+(float)Math.sin(a)*rad; if(k==0)path.moveTo(px,py);else path.lineTo(px,py); }
            path.close(); c.drawPath(path,p);
            color(Character.isUpperCase(ch)?0xff126C87:0xffBB403D); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.7f); c.drawPath(path,p); p.setStyle(Paint.Style.FILL);
            String label="";
            switch(Character.toLowerCase(ch)) {case 'k':label=Character.isUpperCase(ch)?"楚":"漢";break;case 'a':label="士";break;case 'b':case 'e':label="象";break;case 'n':label="馬";break;case 'r':label="車";break;case 'c':label="包";break;case 'p':label=Character.isUpperCase(ch)?"卒":"兵";break;}
            p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(cell*.48f);p.setTextAlign(Paint.Align.CENTER);
            c.drawText(label,xx,yy-(p.ascent()+p.descent())/2,p);
            if(selected.equals(""+(char)('a'+f)+(r+1))) { color(0xffDF9D32); p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawCircle(xx,yy,rad+3,p); }
        }
        for(String sq:targets) { int[] a=parse(sq); if(a[0]<0)continue;color(0xaa156F5E);c.drawCircle(x(a[0]),y(a[1]),cell*.105f,p); }
        String[] hm=splitMove(hint);
        if(hm!=null && !hm[0].equals(hm[1])) {
            int[] a=parse(hm[0]),b=parse(hm[1]);float sx=x(a[0]),sy=y(a[1]),ex=x(b[0]),ey=y(b[1]);
            color(0xbbed9d22);p.setStrokeWidth(cell*.12f);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(sx,sy,ex,ey,p);
            double ang=Math.atan2(ey-sy,ex-sx);Path arrow=new Path();arrow.moveTo(ex,ey);
            arrow.lineTo(ex-(float)Math.cos(ang-.55)*cell*.5f,ey-(float)Math.sin(ang-.55)*cell*.5f);
            arrow.lineTo(ex-(float)Math.cos(ang+.55)*cell*.5f,ey-(float)Math.sin(ang+.55)*cell*.5f);arrow.close();c.drawPath(arrow,p);
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if(cell<=0)return true;
        int f=Math.round((e.getX()-ox)/cell),r=Math.round((e.getY()-oy)/cell);
        String square=(f>=0&&f<9&&r>=0&&r<10)?""+(char)('a'+(flipped?8-f:f))+(flipped?r+1:10-r):"";
        if(e.getAction()==MotionEvent.ACTION_DOWN){downSquare=square;return true;}
        if(e.getAction()==MotionEvent.ACTION_UP){
            if(!downSquare.isEmpty()&&!square.isEmpty()&&!downSquare.equals(square)&&drag!=null)drag.move(downSquare,square);
            else if(!square.isEmpty()&&tap!=null)tap.square(square);
            downSquare="";performClick();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
