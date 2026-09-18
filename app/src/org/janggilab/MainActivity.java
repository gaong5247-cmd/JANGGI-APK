/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Distributed WITHOUT ANY WARRANTY; see LICENSE.
 */
package org.janggilab;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

public final class MainActivity extends Activity {
    static final int BG=0xff101F20, CARD=0xff1B3031, TEXT=0xffF1F1E9, MUTED=0xffAAC0B9, ACCENT=0xffE2BF7D;
    static final String START="rnba1abnr/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/RNBA1ABNR w - - 0 1";
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final AtomicInteger generation=new AtomicInteger();
    final Handler main=new Handler(Looper.getMainLooper());
    volatile Engine a,b;
    volatile boolean initialized, destroyed;
    Engine.State state;
    BoardView board;
    TextView status, turn, eval, details, history, badge;
    Button run;
    Button[] modes=new Button[3];
    String initialFen=START, mode="analysis", displayedFen="";
    ArrayList<String> moves=new ArrayList<>();
    int cursor=0, skillA=20,skillB=12,msA=1000,msB=1000,threads=1,hash=32;
    boolean active=false,humanWhite=true;
    final String[] pvs={"","",""};
    SharedPreferences prefs;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("janggi",0);
        initialFen=prefs.getString("initial",START);skillA=prefs.getInt("skillA",20);skillB=prefs.getInt("skillB",12);
        humanWhite=prefs.getBoolean("humanWhite",true);
        msA=prefs.getInt("msA",1000);msB=prefs.getInt("msB",1000);threads=prefs.getInt("threads",1);hash=prefs.getInt("hash",32);
        String savedMoves=prefs.getString("moves","");
        if(savedMoves.matches("([a-i](10|[1-9])[a-i](10|[1-9]) ?)*")&&!savedMoves.isEmpty())moves.addAll(Arrays.asList(savedMoves.split(" +")));
        cursor=Math.min(prefs.getInt("cursor",moves.size()),moves.size());
        ui();
        worker.execute(()->{
            try{
                String exe=getApplicationInfo().nativeLibraryDir+"/libfairy.so";
                a=new Engine(exe); if(destroyed){a.close();return;}
                b=new Engine(exe); if(destroyed){a.close();b.close();return;}
                initialized=true; main.post(()->{status.setText("오프라인 엔진 준비 완료");refresh(false);});
            }catch(Exception e){error(e);}
        });
    }
    int dp(float n){return (int)(getResources().getDisplayMetrics().density*n+.5f);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    Button button(String s,Runnable task){Button x=new Button(this);x.setText(s);x.setAllCaps(false);x.setTextSize(12);x.setTextColor(TEXT);x.setMinWidth(0);x.setMinimumWidth(0);x.setPadding(dp(3),0,dp(3),0);x.setBackground(bg(CARD,10));x.setOnClickListener(v->task.run());return x;}
    void addButton(LinearLayout r,Button b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(3),dp(3),dp(3),dp(3));r.addView(b,lp);}
    void ui(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(14),dp(12),dp(14),dp(18));scroll.addView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(dp(14)+i.left,dp(12)+i.top,dp(14)+i.right,dp(18)+i.bottom);return insets;});
        // API 26-29 use legacy insets.
        if(Build.VERSION.SDK_INT<30)root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(dp(14)+i.getSystemWindowInsetLeft(),dp(12)+i.getSystemWindowInsetTop(),dp(14)+i.getSystemWindowInsetRight(),dp(18)+i.getSystemWindowInsetBottom());return i;});
        LinearLayout heading=row();TextView title=text("장기 연구실",27,TEXT);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(45),1));
        Button settings=button("설정",()->settings());heading.addView(settings,new LinearLayout.LayoutParams(dp(64),dp(40)));root.addView(heading);
        badge=text("FAIRY-STOCKFISH  ·  OFFLINE",11,ACCENT);badge.setLetterSpacing(.12f);root.addView(badge);
        LinearLayout tabs=row();String[] names={"분석","직접 대국","엔진 대결"};String[] ids={"analysis","play","arena"};
        for(int i=0;i<3;i++){final String id=ids[i];modes[i]=button(names[i],()->{mode=id;active=false;updateModes();refresh(false);});addButton(tabs,modes[i]);}root.addView(tabs);updateModes();
        turn=text("엔진을 준비하고 있습니다",16,TEXT);turn.setPadding(dp(3),dp(10),0,dp(8));root.addView(turn);
        board=new BoardView(this);board.fen(initialFen);board.tap=this::tap;
        int width=getResources().getDisplayMetrics().widthPixels-dp(28);
        root.addView(board,new LinearLayout.LayoutParams(-1,Math.min((int)(width*1.1),dp(470))));
        LinearLayout bar=row();addButton(bar,button("◀ 이전",()->navigate(-1)));addButton(bar,button("다음 ▶",()->navigate(1)));addButton(bar,button("한 수 쉼",this::pass));addButton(bar,button("뒤집기",()->{board.flipped=!board.flipped;board.invalidate();}));root.addView(bar);
        LinearLayout controls=row();run=button("분석 시작",()->{if(!initialized)return;active=!active;refresh(active);});run.setTextColor(ACCENT);addButton(controls,run);addButton(controls,button("새 대국",this::newGame));addButton(controls,button("기보 공유",this::share));root.addView(controls);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(14),dp(12),dp(14),dp(12));panel.setBackground(bg(CARD,14));
        eval=text("국면을 깊이 읽어보세요",20,ACCENT);panel.addView(eval);details=text("추천수 · 평가 · 탐색 깊이 · 후보 수순\n분석 시작을 누르면 현재 국면을 탐색합니다.",12,MUTED);details.setLineSpacing(dp(3),1);details.setTextIsSelectable(true);panel.addView(details);root.addView(panel);
        status=text("Fairy-Stockfish를 시작하는 중…",12,MUTED);status.setPadding(dp(3),dp(10),0,dp(8));root.addView(status);
        history=text("아직 둔 수가 없습니다",12,MUTED);history.setTextIsSelectable(true);history.setLineSpacing(dp(3),1);root.addView(history);
        TextView foot=text("초 = 파랑 · 한 = 빨강  /  평가값은 현재 둘 차례 기준",10,MUTED);foot.setPadding(0,dp(14),0,0);root.addView(foot);
        setContentView(scroll);
    }
    void updateModes(){for(int i=0;i<3;i++)modes[i].setTextColor(mode.equals(new String[]{"analysis","play","arena"}[i])?ACCENT:MUTED);}
    void persist(){prefs.edit().putString("initial",initialFen).putString("moves",String.join(" ",moves)).putInt("cursor",cursor).putInt("skillA",skillA).putInt("skillB",skillB).putInt("msA",msA).putInt("msB",msB).putInt("threads",threads).putInt("hash",hash).putBoolean("humanWhite",humanWhite).apply();}
    int cancel(){int id=generation.incrementAndGet();if(a!=null)a.stop();if(b!=null)b.stop();return id;}
    void refresh(boolean search){
        if(!initialized)return;
        final int id=cancel();state=null;board.hint="";board.selected="";board.targets.clear();board.invalidate();Arrays.fill(pvs,"");
        final String fen=initialFen, currentMode=mode;
        final ArrayList<String> snapshot=new ArrayList<>(moves.subList(0,cursor));
        final int sa=skillA,sb=skillB,ta=msA,tb=msB,th=threads,ha=hash;
        persist();run.setText(active?"■ 중지":mode.equals("analysis")?"분석 시작":"대국 시작");status.setText("국면 확인 중…");
        worker.execute(()->{
            if(id!=generation.get())return;
            try{
                a.position(fen,snapshot);Engine.State st=a.state();
                if(id!=generation.get())return;
                main.post(()->{if(id==generation.get())render(st);});
                if(!search||!st.result.equals("ongoing"))return;
                boolean analysis=currentMode.equals("analysis");
                if(currentMode.equals("play")&&st.white==humanWhite)return;
                Engine e=analysis||st.white?a:b;
                e.configure(analysis?20:st.white?sa:sb,th,ha,analysis?3:1);e.position(fen,snapshot);
                if(id!=generation.get())return;
                main.post(()->{if(id==generation.get())status.setText(analysis?"분석 중 · 평가값은 둘 차례 기준":(st.white?"초 A":"한 B")+" 엔진 생각 중…");});
                long[] tick={0};
                // Long, cancellable analysis; arena uses the selected per-move time.
                String best=e.search(analysis?600000:st.white?ta:tb,()->id==generation.get(),line->{
                    if(id!=generation.get()||!line.startsWith("info ")||!line.contains(" pv "))return;
                    long now=System.currentTimeMillis();if(now-tick[0]<80&&!line.contains("multipv 3"))return;tick[0]=now;
                    main.post(()->{if(id==generation.get())info(line);});
                });
                if(id!=generation.get())return;
                main.post(()->{
                    if(id!=generation.get())return;
                    if(analysis){active=false;run.setText("분석 시작");status.setText("분석 완료");}
                    else if(st.legal.contains(best)){commit(best);}
                    else {active=false;status.setText("엔진이 유효한 수를 반환하지 않았습니다: "+best);run.setText("대국 시작");}
                });
            }catch(Exception ex){if(id==generation.get())error(ex);}
        });
    }
    void render(Engine.State st){
        if(!displayedFen.equals(st.fen)){eval.setText("현재 국면 · 분석 대기");details.setText("분석 시작을 누르면 추천수와 후보 수순을 표시합니다.");displayedFen=st.fen;}
        state=st;board.fen(st.fen);board.last=cursor>0?moves.get(cursor-1):"";board.invalidate();
        String side=st.white?"초":"한";
        turn.setText(side+" 차례"+(st.check?" · 장군":"")+"     "+cursor+"수");
        status.setText("인터넷 없이 사용 중 · "+(mode.equals("analysis")?"기물을 눌러 분석 수순을 진행할 수 있습니다":mode.equals("play")?"내 진영: "+(humanWhite?"초":"한"):"초 A / 한 B · 독립 엔진"));
        if(!st.result.equals("ongoing")){
            active=false;run.setText("종료");String result=st.result.equals("draw")?"무승부":(st.result.equals("win")==st.white?"초 승":"한 승");turn.setText("대국 종료 · "+result);status.setText("Fairy-Stockfish 장기 규칙 판정");
        }
        StringBuilder h=new StringBuilder();for(int i=0;i<moves.size();i++){if(i==cursor)h.append("│ ");h.append(i+1).append(". ").append(moves.get(i)).append("   ");}
        if(cursor==moves.size()&&!moves.isEmpty())h.append("│");history.setText(h.length()==0?"아직 둔 수가 없습니다":h.toString());
    }
    static String token(String line,String key){String[] t=line.split(" +");for(int i=0;i<t.length-1;i++)if(t[i].equals(key))return t[i+1];return "—";}
    void info(String line){
        int pv=1;try{pv=Integer.parseInt(token(line,"multipv"));}catch(Exception ignored){}
        String variation=line.substring(line.indexOf(" pv ")+4);String[] v=variation.split(" +");
        String value="";Matcher match=Pattern.compile("score (cp|mate) (-?\\d+)").matcher(line);
        if(match.find())value=match.group(1).equals("mate")?"외통 "+match.group(2):String.format(Locale.US,"%+.2f",Integer.parseInt(match.group(2))/100.0);
        if(pv>=1&&pv<=3)pvs[pv-1]=pv+". "+value+"  "+String.join(" ",Arrays.copyOf(v,Math.min(v.length,8)));
        if(pv==1){eval.setText(value+"   ·   "+v[0]);board.hint=v[0];board.invalidate();}
        StringBuilder d=new StringBuilder("깊이 "+token(line,"depth")+"  ·  "+token(line,"nps")+" nodes/s\n");for(String s:pvs)if(!s.isEmpty())d.append(s).append('\n');details.setText(d.toString().trim());
    }
    void tap(String sq){
        if(state==null||!state.result.equals("ongoing")||mode.equals("arena")||(mode.equals("play")&&state.white!=humanWhite))return;
        if(!board.selected.isEmpty()){
            String move=board.selected+sq;
            if(!board.selected.equals(sq)&&state.legal.contains(move)){commit(move);return;}
        }
        board.selected=sq;board.targets.clear();
        for(String m:state.legal){String[] s=BoardView.splitMove(m);if(s!=null&&s[0].equals(sq)&&!s[0].equals(s[1]))board.targets.add(s[1]);}board.invalidate();
    }
    void commit(String move){
        while(moves.size()>cursor)moves.remove(moves.size()-1);
        moves.add(move);cursor++;
        if(mode.equals("play"))active=true;
        refresh(active);
    }
    void navigate(int delta){if(cursor+delta<0||cursor+delta>moves.size())return;cursor+=delta;active=false;eval.setText("선택한 국면");details.setText("분석 시작으로 현재 국면을 평가할 수 있습니다.");refresh(false);}
    void pass(){if(state==null||mode.equals("arena")||(mode.equals("play")&&state.white!=humanWhite))return;for(String m:state.legal){String[] s=BoardView.splitMove(m);if(s!=null&&s[0].equals(s[1])){commit(m);return;}}Toast.makeText(this,"현재 국면에서는 한 수 쉴 수 없습니다",0).show();}
    void error(Exception e){main.post(()->{if(destroyed)return;active=false;if(run!=null)run.setText("다시 시작");if(status!=null)status.setText("오류: "+e.getMessage()+" · 계속되면 앱을 다시 열어주세요");});}
    Spinner spinner(String[] options,int selected){Spinner s=new Spinner(this);ArrayAdapter<String> ad=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);s.setAdapter(ad);s.setSelection(selected);return s;}
    void label(LinearLayout l,String title,View input){TextView t=text(title,14,0xff233F3A);t.setPadding(dp(3),dp(10),0,0);l.addView(t);l.addView(input,new LinearLayout.LayoutParams(-1,dp(44)));}
    void newGame(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(1);l.setPadding(dp(18),0,dp(18),0);
        String[] forms={"마상상마 (기본)","상마마상","마상마상","상마상마"};Spinner cho=spinner(forms,0),han=spinner(forms,0);label(l,"초 차림 · 화면 아래에서 본 순서",cho);label(l,"한 차림 · 화면 아래에서 본 순서",han);
        new AlertDialog.Builder(this).setTitle("새 장기 대국").setView(l).setMessage("현재 기보를 초기화합니다. 필요한 기보는 먼저 공유하세요.").setNegativeButton("취소",null).setPositiveButton("시작",(d,w)->{
            String[] ranks={"RNBA1ABNR","RBNA1ANBR","RNBA1ANBR","RBNA1ABNR"};
            initialFen=ranks[han.getSelectedItemPosition()].toLowerCase(Locale.ROOT)+"/4k4/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/4K4/"+ranks[cho.getSelectedItemPosition()]+" w - - 0 1";
            moves.clear();cursor=0;active=false;eval.setText("새 대국 준비 완료");details.setText("차림을 적용했습니다. 시작 버튼을 눌러주세요.");refresh(false);
        }).show();
    }
    int index(int[] values,int n){for(int i=0;i<values.length;i++)if(values[i]==n)return i;return 0;}
    void settings(){
        active=false;refresh(false);
        ScrollView scroll=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setPadding(dp(20),0,dp(20),dp(10));l.setOrientation(1);scroll.addView(l);
        String[] skills=new String[21];for(int i=0;i<21;i++)skills[i]="레벨 "+i+(i==20?" · 최대":"");
        Spinner sa=spinner(skills,skillA),sb=spinner(skills,skillB);
        int[] times={250,500,1000,2000,5000,10000};String[] timeLabels={"0.25초","0.5초","1초","2초","5초","10초"};Spinner ta=spinner(timeLabels,index(times,msA)),tb=spinner(timeLabels,index(times,msB));
        Spinner th=spinner(new String[]{"1 스레드 · 절전","2 스레드","4 스레드"},index(new int[]{1,2,4},threads));Spinner ha=spinner(new String[]{"16 MB","32 MB","64 MB","128 MB"},index(new int[]{16,32,64,128},hash));Spinner side=spinner(new String[]{"초 · 먼저 둠","한 · 나중에 둠"},humanWhite?0:1);
        label(l,"초 엔진 A 강도",sa);label(l,"초 A · 한 수 생각 시간",ta);label(l,"한 엔진 B 강도",sb);label(l,"한 B · 한 수 생각 시간",tb);label(l,"엔진별 CPU",th);label(l,"엔진별 해시",ha);label(l,"직접 대국 · 내 진영",side);
        TextView note=text("분석은 최대 강도 · 후보수 3개로 실행됩니다.\n화면을 떠나면 탐색은 멈추고 기보는 자동 저장됩니다.\n\nFairy-Stockfish / GPL-3.0-or-later\n내장 평가함수 사용 · NNUE 미포함\n엔진 대결은 같은 엔진의 서로 다른 설정 간 대결입니다.",12,0xff425B54);l.addView(note);
        new AlertDialog.Builder(this).setTitle("연구실 설정").setView(scroll).setNegativeButton("취소",null).setNeutralButton("라이선스",(d,w)->licenses()).setPositiveButton("적용",(d,w)->{skillA=sa.getSelectedItemPosition();skillB=sb.getSelectedItemPosition();msA=times[ta.getSelectedItemPosition()];msB=times[tb.getSelectedItemPosition()];threads=new int[]{1,2,4}[th.getSelectedItemPosition()];hash=new int[]{16,32,64,128}[ha.getSelectedItemPosition()];humanWhite=side.getSelectedItemPosition()==0;persist();refresh(false);}).show();
    }
    void licenses(){try{java.io.InputStream in=getAssets().open("COPYING");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))>0)out.write(buf,0,n);in.close();TextView t=text(out.toString("UTF-8"),12,0xff233F3A);t.setPadding(dp(16),dp(12),dp(16),dp(12));ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle("GNU GPL v3").setView(s).setPositiveButton("닫기",null).show();}catch(Exception e){error(e);}}
    void share(){if(state==null)return;String content="[장기 연구실 / Fairy-Stockfish]\nVariant: janggi\nInitial FEN: "+initialFen+"\nMoves: "+String.join(" ",moves)+"\nCursor: "+cursor+"\nCurrent FEN: "+state.fen+"\n";Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,content);startActivity(Intent.createChooser(i,"기보 공유"));}
    @Override protected void onResume(){super.onResume();if(initialized)refresh(false);}
    @Override protected void onPause(){super.onPause();active=false;cancel();persist();if(run!=null)run.setText(mode.equals("analysis")?"분석 시작":"대국 시작");}
    @Override protected void onDestroy(){destroyed=true;cancel();if(a!=null)a.close();if(b!=null)b.close();worker.shutdownNow();super.onDestroy();}
}
