/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.janggilab;

import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

final class ReviewGraphView extends View {
    interface Select { void index(int index); }
    private final Paint paint=new Paint(3);
    private GameReview.Result result;
    private int selected=-1;
    private Select select;

    ReviewGraphView(Context context){super(context);setMinimumHeight(dp(120));}

    void data(GameReview.Result value){result=value;invalidate();}
    void selected(int index){selected=index;invalidate();}
    void onSelect(Select listener){select=listener;}

    private int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight(),left=dp(28),right=w-dp(10),top=dp(12),bottom=h-dp(18);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff142728);c.drawRoundRect(0,0,w,h,dp(12),dp(12),paint);
        paint.setStrokeWidth(dp(1));paint.setColor(0xff526a65);
        float mid=(top+bottom)/2f;c.drawLine(left,mid,right,mid,paint);

        paint.setTextSize(dp(9));paint.setColor(0xffaac0b9);paint.setTextAlign(Paint.Align.LEFT);
        c.drawText("초",dp(7),top+dp(5),paint);c.drawText("한",dp(7),bottom,paint);
        if(result==null||result.items.isEmpty())return;

        int n=result.items.size();
        Path path=new Path();
        for(int i=0;i<n;i++){
            GameReview.Item item=result.items.get(i);
            float x=n==1?(left+right)/2f:left+(right-left)*i/(float)(n-1);
            float p=(float)item.whiteExpectedAfter();
            float y=bottom-(bottom-top)*p;
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xffe2bf7d);c.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);

        for(int i=0;i<n;i++){
            GameReview.Item item=result.items.get(i);
            if(!item.kind.key&&i!=selected)continue;
            float x=n==1?(left+right)/2f:left+(right-left)*i/(float)(n-1);
            float y=bottom-(bottom-top)*(float)item.whiteExpectedAfter();
            paint.setColor(item.kind.color);
            c.drawCircle(x,y,dp(i==selected?5:3.3f),paint);
            if(i==selected){
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setColor(0xffffffff);
                c.drawCircle(x,y,dp(7),paint);paint.setStyle(Paint.Style.FILL);
            }
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getAction()!=MotionEvent.ACTION_UP||result==null||result.items.isEmpty())return true;
        float left=dp(28),right=getWidth()-dp(10);
        float ratio=(event.getX()-left)/Math.max(1f,right-left);
        int idx=Math.round(Math.max(0f,Math.min(1f,ratio))*(result.items.size()-1));
        selected=idx;invalidate();if(select!=null)select.index(idx);performClick();return true;
    }

    @Override public boolean performClick(){super.performClick();return true;}
}
