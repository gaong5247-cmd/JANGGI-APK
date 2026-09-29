/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.janggilab;

import android.app.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

final class GameReviewDialog {
    private final Activity activity;
    private final GameReview.Result result;
    private BoardView board;
    private ReviewGraphView graph;
    private TextView heading,coach,detail,puzzle;
    private Button retry,solution,showLine,passMove;\n    private int lineStep;
    private int index;
    private boolean retryMode,showSolution;

    private GameReviewDialog(Activity activity,GameReview.Result result){
        this.activity=activity;this.result=result;
    }

    static void show(Activity activity,GameReview.Result result){
        if(result==null||result.items.isEmpty()){
            Toast.makeText(activity,"리뷰할 수가 없습니다",Toast.LENGTH_LONG).show();return;
        }
        new GameReviewDialog(activity,result).open();
    }

    private int dp(float n){return (int)(activity.getResources().getDisplayMetrics().density*n+.5f);}
    private TextView text(String value,int sp,int color){
        TextView t=new TextView(activity);t.setText(value);t.setTextSize(sp);t.setTextColor(color);return t;
    }
    private GradientDrawable bg(int color,int radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private Button button(String label,Runnable action){
        Button b=new Button(activity);b.setText(label);b.setAllCaps(false);b.setTextSize(11);b.setTextColor(MainActivity.TEXT);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(3),0,dp(3),0);b.setBackground(bg(MainActivity.CARD,10));
        b.setOnClickListener(v->action.run());return b;
    }
    private void add(LinearLayout row,Button button){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));row.addView(button,lp);
    }

    private void open(){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setBackgroundColor(MainActivity.BG);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12),dp(10),dp(12),dp(12));scroll.addView(root);

        TextView summary=text(result.summary(),13,MainActivity.TEXT);summary.setLineSpacing(dp(3),1);summary.setTypeface(null,Typeface.BOLD);
        summary.setPadding(dp(10),dp(8),dp(10),dp(8));summary.setBackground(bg(MainActivity.CARD,12));root.addView(summary);

        TextView legend=text("그래프: 위쪽=초 유리 · 아래쪽=한 유리 · 색 점=핵심 수",10,MainActivity.MUTED);
        legend.setPadding(dp(4),dp(8),0,dp(4));root.addView(legend);
        graph=new ReviewGraphView(activity);graph.data(result);graph.onSelect(i->{retryMode=false;showSolution=false;show(i);});
        root.addView(graph,new LinearLayout.LayoutParams(-1,dp(126)));

        board=new BoardView(activity);
        int width=activity.getResources().getDisplayMetrics().widthPixels-dp(72);
        root.addView(board,new LinearLayout.LayoutParams(-1,Math.min((int)(width*1.06f),dp(430))));

        heading=text("",18,MainActivity.ACCENT);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(dp(4),dp(8),0,dp(4));root.addView(heading);
        coach=text("",13,MainActivity.TEXT);coach.setLineSpacing(dp(3),1);coach.setPadding(dp(10),dp(8),dp(10),dp(8));coach.setBackground(bg(MainActivity.CARD,12));root.addView(coach);
        detail=text("",11,MainActivity.MUTED);detail.setLineSpacing(dp(2),1);detail.setTextIsSelectable(true);detail.setPadding(dp(4),dp(8),dp(4),dp(4));root.addView(detail);
        puzzle=text("",12,MainActivity.ACCENT);puzzle.setPadding(dp(4),dp(4),dp(4),dp(4));root.addView(puzzle);

        LinearLayout nav=row();
        add(nav,button("◀ 이전",()->show(Math.max(0,index-1))));
        add(nav,button("다음 핵심",this::nextKey));
        add(nav,button("다음 ▶",()->show(Math.min(result.items.size()-1,index+1))));
        root.addView(nav);

        LinearLayout actions=row();
        retry=button("Retry",this::retry);
        passMove=button("한 수 쉼",this::puzzlePass);
        solution=button("Best · 해답",()->{retryMode=false;showSolution=true;show(index);});
        showLine=button("Show · 수순",this::playLineStep);
        add(actions,retry);add(actions,passMove);add(actions,solution);add(actions,showLine);root.addView(actions);

        TextView note=text("정확도와 분류는 Fairy-Stockfish 평가를 승리 기대값으로 변환한 장기 연구실의 로컬 추정치입니다. Chess.com의 비공개 CAPS2/레이팅 모델과 동일한 값은 아닙니다.",9,MainActivity.MUTED);
        note.setPadding(dp(4),dp(9),dp(4),0);root.addView(note);

        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("게임 리뷰").setView(scroll).setNegativeButton("닫기",null).create();
        dialog.setOnShowListener(d->{Window w=dialog.getWindow();if(w!=null)w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});
        show(0);dialog.show();
    }

    private void show(int next){
        index=Math.max(0,Math.min(result.items.size()-1,next));
        lineStep=0;if(showLine!=null)showLine.setText("Show · 수순");
        GameReview.Item item=result.items.get(index);
        graph.selected(index);
        board.fen(item.fenBefore);
        board.last=index>0?result.items.get(index-1).playedMove:"";
        board.hint=showSolution?item.bestMove:"";
        board.selected="";board.targets.clear();
        board.tap=retryMode?this::puzzleTap:null;board.drag=null;board.invalidate();

        heading.setText(item.ply+"수 · "+(item.moverWhite?"초":"한")+" · "+item.kind.symbol+" "+item.kind.label);
        heading.setTextColor(item.kind.color);
        coach.setText(item.coach);
        detail.setText(String.format(Locale.US,
            "실전 수  %s   (%s)\n추천 수  %s   (%s)\n승리 기대값  %.0f%% → %.0f%%   손실 %.0f%%p\n추천 수순  %s",
            item.playedMove,item.playedScore,item.bestMove,item.bestScore,
            item.bestExpected*100,item.playedExpected*100,item.loss*100,item.bestLine));
        if(!retryMode)puzzle.setText(showSolution?"금색 화살표가 엔진 추천수입니다.":"Retry를 누르면 이 장면을 퍼즐처럼 다시 풀 수 있습니다.");
        retry.setText(retryMode?"퍼즐 취소":"Retry");
        String pass=passFor(item);
        passMove.setVisibility(retryMode&&!pass.isEmpty()?View.VISIBLE:View.GONE);
        passMove.setEnabled(retryMode&&!pass.isEmpty());
        solution.setEnabled(!showSolution);
    }

    private void retry(){
        retryMode=!retryMode;showSolution=false;
        puzzle.setText(retryMode?"최선수를 직접 찾아보세요. 기물을 누르면 합법수가 표시됩니다.":"퍼즐 모드를 종료했습니다.");
        show(index);
    }

    private String passFor(GameReview.Item item){
        for(String move:item.legal){
            String[] s=BoardView.splitMove(move);
            if(s!=null&&s[0].equals(s[1]))return move;
        }
        return "";
    }

    private void puzzlePass(){
        if(!retryMode)return;
        GameReview.Item item=result.items.get(index);
        String pass=passFor(item);
        if(pass.isEmpty())return;
        if(pass.equals(item.bestMove)){
            retryMode=false;showSolution=true;show(index);
            puzzle.setText("정답! 이 국면의 최선은 한 수 쉬는 것입니다.");
        }else{
            puzzle.setText("한 수 쉴 수는 있지만 최선수는 아닙니다. 다른 수를 찾아보세요.");
        }
    }

    private void puzzleTap(String square){
        if(!retryMode)return;
        GameReview.Item item=result.items.get(index);
        if(!board.selected.isEmpty()){
            String move=board.selected+square;
            if(!board.selected.equals(square)&&item.legal.contains(move)){
                board.selected="";board.targets.clear();
                if(move.equals(item.bestMove)){
                    retryMode=false;showSolution=true;
                    show(index);
                    puzzle.setText("정답! "+move+"가 이 국면의 최선수입니다.");
                }else{
                    if(move.equals(item.playedMove))
                        puzzle.setText("이 수는 실전에서 둔 "+item.kind.symbol+" "+item.kind.label+"입니다. 다른 수를 찾아보세요.");
                    else puzzle.setText(move+"도 합법이지만 엔진 최선수는 아닙니다. 다시 찾아보세요.");
                    board.invalidate();
                }
                return;
            }
        }
        board.selected=square;board.targets.clear();
        for(String move:item.legal){
            String[] s=BoardView.splitMove(move);
            if(s!=null&&s[0].equals(square)&&!s[0].equals(s[1]))board.targets.add(s[1]);
        }
        board.invalidate();
    }

    private void playLineStep(){
        retryMode=false;showSolution=false;board.selected="";board.targets.clear();board.hint="";
        GameReview.Item item=result.items.get(index);
        String[] line=item.bestLine.trim().isEmpty()?new String[0]:item.bestLine.trim().split(" +");
        if(line.length==0){puzzle.setText("표시할 추천 수순이 없습니다.");return;}
        if(lineStep<=0||lineStep>=line.length){
            board.fen(item.fenBefore);board.last=index>0?result.items.get(index-1).playedMove:"";lineStep=0;
        }
        String move=line[lineStep];
        String[] squares=BoardView.splitMove(move);
        if(squares==null){puzzle.setText("수순 표기 오류: "+move);return;}
        if(!squares[0].equals(squares[1])){
            char moving=board.piece(squares[0]);
            board.setPiece(squares[0],' ');board.setPiece(squares[1],moving);
        }
        board.last=move;lineStep++;board.invalidate();
        puzzle.setText("추천 수순 "+lineStep+"/"+line.length+" · "+move);
        showLine.setText(lineStep>=line.length?"수순 처음":"Show ▶");
    }

    private void nextKey(){
        for(int i=index+1;i<result.items.size();i++){
            if(result.items.get(i).kind.key){retryMode=false;showSolution=false;show(i);return;}
        }
        Toast.makeText(activity,"뒤에 남은 핵심 수가 없습니다",Toast.LENGTH_SHORT).show();
    }
}
