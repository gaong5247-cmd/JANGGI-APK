/* Copyright (C) 2026 Janggi Lab contributors.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.janggilab;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Windows desktop companion to the Android GUI; uses the SAME Engine protocol. */
public final class DesktopMain {
    private static final Color BG=new Color(16,31,32), CARD=new Color(27,48,49),
            TEXT=new Color(241,241,233), MUTED=new Color(170,192,185),
            GOLD=new Color(226,191,125);
    private final Preferences prefs=Preferences.userNodeForPackage(DesktopMain.class);
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"janggi-controller");t.setDaemon(true);return t;
    });
    private final AtomicInteger generation=new AtomicInteger();
    private JFrame window;
    private BoardSurface board;
    private JLabel turn, evaluation, status, engineLabel, ruleHelp;
    private JTextArea details, history;
    private JComboBox<String> rules, modes, sides;
    private JButton start, pass;
    private volatile Engine a,b;
    private Engine.State current;
    private String variant=VariantConfig.IDS[0], variantStart=DesktopPosition.START;
    private String initialFen=DesktopPosition.START, exePath="", nnuePath="";
    private String stagedNetworkPath="";
    private final List<String> moves=new ArrayList<>();
    private final String[] variations={"","",""};
    private int cursor,skillCho=20,skillHan=12,timeCho=1000,timeHan=1000;
    private int threads=1,hash=32;
    private boolean running,loading,closed,humanWhite=true;
    private String mode="analysis";
    private DesktopPosition position=new DesktopPosition();

    public static void main(String[] args){
        System.setProperty("sun.java2d.uiScale.enabled","true");
        SwingUtilities.invokeLater(()->new DesktopMain().open());
    }
    private static JButton button(String label, Runnable action){
        JButton b=new JButton(label);b.setFocusPainted(false);b.addActionListener(e->action.run());return b;
    }
    private static JPanel row(Component... components){
        JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,7,5));p.setOpaque(false);
        for(Component c:components)p.add(c);
        return p;
    }
    private static JLabel text(String s,int size,Color color){
        JLabel l=new JLabel(s);l.setForeground(color);l.setFont(new Font("Malgun Gothic",Font.PLAIN,size));return l;
    }
    private static String html(String s){
        return "<html><body style='width:290px'>"+s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</body></html>";
    }
    private void open(){
        variant=VariantConfig.saved(prefs.get("variant",variant));
        humanWhite=prefs.getBoolean("humanWhite",true);
        String defaultEngine=findDefaultEngine();
        String remembered=prefs.get("exePath","");
        exePath=(!remembered.isEmpty()&&Files.isRegularFile(Paths.get(remembered)))?remembered:defaultEngine;
        nnuePath=prefs.get("nnuePath","");
        skillCho=prefs.getInt("skillCho",20);skillHan=prefs.getInt("skillHan",12);
        timeCho=prefs.getInt("timeCho",1000);timeHan=prefs.getInt("timeHan",1000);
        threads=prefs.getInt("threads",1);hash=prefs.getInt("hash",32);
        initialFen=prefs.get("initialFen",DesktopPosition.START);
        try{position=new DesktopPosition(initialFen);}catch(Exception ex){initialFen=DesktopPosition.START;position=new DesktopPosition();}
        window=new JFrame("Janggi Lab · 장기 연구실 for Windows");
        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){close();}});
        window.getContentPane().setBackground(BG);
        window.setLayout(new BorderLayout(12,12));
        JPanel header=new JPanel(new BorderLayout());header.setBackground(BG);
        JLabel title=text("장기 연구실  ·  WINDOWS",26,TEXT);title.setBorder(BorderFactory.createEmptyBorder(9,16,0,0));
        header.add(title,BorderLayout.NORTH);
        rules=new JComboBox<>(VariantConfig.NAMES);rules.setSelectedIndex(VariantConfig.index(variant));
        modes=new JComboBox<>(new String[]{"분석","직접 대국","엔진 대결"});
        sides=new JComboBox<>(new String[]{"내 진영: 초","내 진영: 한"});sides.setSelectedIndex(humanWhite?0:1);
        JPanel bar=row(text("규칙",13,MUTED),rules,text("모드",13,MUTED),modes,sides,
                button("새 대국",this::newGame),button("포지션 편집",this::editPosition));
        header.add(bar,BorderLayout.CENTER);
        ruleHelp=text(html(VariantConfig.description(variant)),12,MUTED);
        ruleHelp.setBorder(BorderFactory.createEmptyBorder(4,18,8,8));
        header.add(ruleHelp,BorderLayout.SOUTH);
        window.add(header,BorderLayout.NORTH);
        board=new BoardSurface();
        board.model=position;
        board.onTap=this::tap;
        board.onDrag=(from,to)->{if(current!=null&&current.canPlay(from+to))commit(from+to);};
        board.flipped=!humanWhite;
        JPanel left=new JPanel(new BorderLayout(3,3));left.setBackground(BG);
        turn=text("엔진을 준비하는 중...",17,TEXT);turn.setBorder(BorderFactory.createEmptyBorder(0,12,2,0));
        left.add(turn,BorderLayout.NORTH);
        left.add(board,BorderLayout.CENTER);
        start=button("분석 시작",this::toggle);
        pass=button("한 수 쉼",this::passMove);pass.setEnabled(false);
        JPanel below=new JPanel(new BorderLayout());below.setOpaque(false);
        JPanel actions=row(button("◀ 이전",()->navigate(-1)),button("다음 ▶",()->navigate(1)),
            pass,button("뒤집기",()->{board.flipped=!board.flipped;board.repaint();}),start);
        below.add(actions,BorderLayout.CENTER);
        left.add(below,BorderLayout.SOUTH);

        JPanel right=new JPanel(new BorderLayout(8,8));right.setBackground(CARD);
        right.setBorder(BorderFactory.createEmptyBorder(18,18,18,18));
        evaluation=text("평가 대기",26,GOLD);
        details=new JTextArea("분석 시작을 누르면 평가와 후보 수순이 나타납니다.");
        details.setBackground(CARD);details.setForeground(TEXT);details.setEditable(false);
        details.setLineWrap(true);details.setWrapStyleWord(true);
        details.setFont(new Font("Consolas",Font.PLAIN,14));
        history=new JTextArea("기보 없음");
        history.setEditable(false);history.setLineWrap(true);history.setWrapStyleWord(true);
        history.setFont(new Font("Consolas",Font.PLAIN,12));
        history.setBackground(CARD);history.setForeground(MUTED);
        JPanel evalPanel=new JPanel(new BorderLayout());evalPanel.setOpaque(false);
        evalPanel.add(text("ENGINE 평가 · 현재 둘 차례 기준",12,MUTED),BorderLayout.NORTH);
        evalPanel.add(evaluation,BorderLayout.CENTER);
        right.add(evalPanel,BorderLayout.NORTH);
        JPanel results=new JPanel(new BorderLayout(8,8));results.setOpaque(false);
        JScrollPane lines=new JScrollPane(details);lines.setBorder(null);
        results.add(lines,BorderLayout.CENTER);
        JPanel historyPanel=new JPanel(new BorderLayout(6,6));historyPanel.setOpaque(false);
        historyPanel.add(text("기보",12,MUTED),BorderLayout.NORTH);
        JScrollPane moveScroll=new JScrollPane(history);moveScroll.setBorder(null);
        moveScroll.setPreferredSize(new Dimension(300,125));
        historyPanel.add(moveScroll,BorderLayout.CENTER);
        results.add(historyPanel,BorderLayout.SOUTH);
        right.add(results,BorderLayout.CENTER);
        JButton engineFile=button("엔진 EXE 선택",()->chooseEngine());
        JButton nnueFile=button("NNUE 선택",()->chooseNetwork());
        JButton defaultEval=button("기본 평가",()->setNetwork(""));
        JButton photoFolder=button("기물 사진 폴더",this::choosePhotos);
        JPanel settings=row(engineFile,nnueFile,defaultEval,photoFolder,
                button("엔진 설정",this::engineSettings));
        settings.setPreferredSize(new Dimension(360,85));
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);
        footer.add(settings,BorderLayout.NORTH);
        engineLabel=text("EXE 미선택",11,MUTED);footer.add(engineLabel,BorderLayout.CENTER);
        right.add(footer,BorderLayout.SOUTH);

        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,right);
        split.setResizeWeight(.65);split.setDividerSize(6);split.setBorder(null);
        window.add(split,BorderLayout.CENTER);
        status=text("준비 중",12,MUTED);status.setBorder(BorderFactory.createEmptyBorder(4,18,10,8));
        window.add(status,BorderLayout.SOUTH);
        rules.addActionListener(e->selectVariant(VariantConfig.IDS[rules.getSelectedIndex()]));
        modes.addActionListener(e->{
            mode=new String[]{"analysis","play","arena"}[modes.getSelectedIndex()];
            running=false;refresh(false);
        });
        sides.addActionListener(e->{
            humanWhite=sides.getSelectedIndex()==0;prefs.putBoolean("humanWhite",humanWhite);
            if(mode.equals("play")){board.flipped=!humanWhite;board.repaint();}
            refresh(false);
        });
        window.setMinimumSize(new Dimension(990,720));
        window.setSize(1200,840);window.setLocationRelativeTo(null);window.setVisible(true);
        split.setDividerLocation(.65);
        if(!exePath.isEmpty()&&Files.isRegularFile(Paths.get(exePath)))loadEngines(exePath,nnuePath,false);
        else{exePath="";status.setText("Windows 장기 엔진 EXE를 먼저 선택하세요.");engineLabel.setText("설정 → 엔진 EXE 선택");}
    }
    private static String findDefaultEngine(){
        String name="janggilab-engine-x86_64.exe";
        List<Path> paths=new ArrayList<>();
        String packaged=System.getProperty("jpackage.app-path","");
        if(!packaged.isEmpty()){
            Path base=Paths.get(packaged).toAbsolutePath().getParent();
            paths.add(base.resolve("app").resolve(name));paths.add(base.resolve(name));
        }
        try{
            Path jarDir=Paths.get(DesktopMain.class.getProtectionDomain().getCodeSource()
                .getLocation().toURI()).toAbsolutePath().getParent();
            if(jarDir!=null)paths.add(jarDir.resolve(name));
        }catch(Exception ignored){}
        paths.add(Paths.get(name));paths.add(Paths.get("dist",name));
        for(Path p:paths)if(Files.isRegularFile(p))return p.toAbsolutePath().toString();
        return "";
    }
    private int cancel(){
        int id=generation.incrementAndGet();
        if(a!=null)a.stop();if(b!=null)b.stop();return id;
    }
    private void onUi(int id,Runnable action){
        SwingUtilities.invokeLater(()->{if(!closed&&generation.get()==id)action.run();});
    }
    /** Engine NNUE routing requires a "janggi*" basename, regardless of source name. */
    private static String stageNetwork(String source)throws IOException{
        if(source==null||source.isEmpty())return "";
        Path from=Paths.get(source);
        if(!Files.isRegularFile(from)||!from.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nnue"))
            throw new IOException("NNUE 확장자의 정상 파일을 선택하세요");
        long bytes=Files.size(from);
        if(bytes==0||bytes>256L*1024*1024)throw new IOException("NNUE는 1~256 MB 파일이어야 합니다");
        String local=System.getenv("LOCALAPPDATA");
        Path dir=(local!=null&&!local.isEmpty()?Paths.get(local):Paths.get(System.getProperty("user.home")))
                 .resolve("JanggiLab").resolve("networks");
        Files.createDirectories(dir);
        Path staged=dir.resolve("janggi-"+UUID.randomUUID()+".nnue");
        Files.copy(from,staged);
        return staged.toAbsolutePath().toString();
    }
    private void loadEngines(String executable,String network,boolean reset){
        if(loading)return;
        running=false;int id=cancel();loading=true;status.setText("엔진 실행 및 4종 규칙 확인 중...");
        worker.execute(()->{
            Engine first=null,second=null;
            String staged="";
            try{
                staged=stageNetwork(network);
                first=new Engine(executable,variant);first.useNetwork(staged);
                second=new Engine(executable,variant);second.useNetwork(staged);
                Engine.State st=first.state();
                Engine oldA=a,oldB=b;a=first;b=second;first=null;second=null;
                if(oldA!=null)oldA.close();if(oldB!=null)oldB.close();
                String oldStaged=stagedNetworkPath;stagedNetworkPath=staged;
                if(!oldStaged.isEmpty()&&!oldStaged.equals(staged))
                    try{Files.deleteIfExists(Paths.get(oldStaged));}catch(IOException ignored){}
                onUi(id,()->{
                    exePath=executable;nnuePath=network;
                    prefs.put("exePath",exePath);prefs.put("nnuePath",nnuePath);
                    if(reset){variantStart=st.startFen;initialFen=st.startFen;moves.clear();cursor=0;}
                    else variantStart=st.startFen;
                    loading=false;engineLabel.setText(html("EXE: "+Paths.get(exePath).getFileName()+
                        " · NNUE: "+(nnuePath.isEmpty()?"사용 안 함":Paths.get(nnuePath).getFileName())));
                    refresh(false);
                });
            }catch(Exception ex){
                if(first!=null)first.close();if(second!=null)second.close();
                if(!staged.isEmpty())try{Files.deleteIfExists(Paths.get(staged));}catch(IOException ignored){}
                onUi(id,()->{loading=false;status.setText("엔진 로딩 실패: "+ex.getMessage());
                    JOptionPane.showMessageDialog(window,ex.getMessage(),"엔진 설정 오류",JOptionPane.ERROR_MESSAGE);});
            }
        });
    }
    private void refresh(boolean analyze){
        if(a==null||loading||closed)return;
        int id=cancel();current=null;Arrays.fill(variations,"");
        board.hint="";board.selected="";board.targets.clear();board.repaint();
        pass.setEnabled(false);start.setText(running?"■ 중지":mode.equals("analysis")?"분석 시작":"대국 시작");
        evaluation.setText("평가 대기");details.setText("현재 국면 확인 중...");
        String fen=initialFen, selectedMode=mode;
        List<String> snapshot=new ArrayList<>(moves.subList(0,cursor));
        worker.execute(()->{
            if(id!=generation.get())return;
            try{
                Engine primary=a;primary.position(fen,snapshot);
                Engine.State st=primary.state();
                if(id!=generation.get())return;
                onUi(id,()->render(st));
                if(!analyze||!st.ongoing())return;
                if(selectedMode.equals("play")&&st.white==humanWhite)return;
                boolean isAnalysis=selectedMode.equals("analysis");
                Engine e=(isAnalysis||st.white)?a:b;
                e.configure(isAnalysis?20:st.white?skillCho:skillHan,threads,hash,isAnalysis?3:1);
                e.position(fen,snapshot);
                if(id!=generation.get())return;
                onUi(id,()->status.setText(isAnalysis?"분석 중 · 평가값은 현재 차례 기준":
                      (st.white?"초":"한")+" 엔진 탐색 중..."));
                String best=e.search(isAnalysis?600000:(st.white?timeCho:timeHan),
                    ()->id==generation.get(),line->{
                        if(line.startsWith("info ")&&line.contains(" pv ")){
                            onUi(id,()->showInfo(st,line));
                        }
                    });
                if(id!=generation.get())return;
                onUi(id,()->{
                    if(isAnalysis){status.setText("분석 완료");start.setText("분석 시작");running=false;}
                    else if(st.canPlay(best))commit(best);
                    else{running=false;start.setText("대국 시작");status.setText("엔진이 유효하지 않은 수를 반환했습니다: "+best);}
                });
            }catch(Exception ex){onUi(id,()->{
                running=false;start.setText("다시 시작");status.setText("엔진 오류: "+ex.getMessage());
            });}
        });
    }
    private void render(Engine.State st){
        current=st;position=new DesktopPosition(st.fen);
        board.model=position;board.repaint();
        pass.setEnabled(VariantConfig.allowsPass(variant)&&st.ongoing()&&!mode.equals("arena")
              &&(!mode.equals("play")||st.white==humanWhite)
              &&st.legal.stream().anyMatch(m->{String[] q=DesktopPosition.splitMove(m);
                  return q!=null&&q[0].equals(q[1]);}));
        if(st.ongoing()){
            turn.setText((st.white?"초":"한")+" 차례"+(st.check?" · 장군":"")+
                    (st.bikjang&&VariantConfig.hasBikjang(variant)?" · 빅장":"")+" · "+cursor+"수");
            status.setText("오프라인 · "+VariantConfig.NAMES[VariantConfig.index(variant)]);
        }else{
            running=false;start.setText("대국 종료");
            String winner=st.result.equals("draw")?"무승부":
                    (st.result.equals("win")==st.white?"초 승":"한 승");
            turn.setText("대국 종료 · "+winner);
            evaluation.setText(winner);
            details.setText("엔진 판정: "+AnalysisScore.reason(st.reason));
            status.setText("Fairy-Stockfish가 대국 종료를 판정했습니다");
        }
        StringBuilder list=new StringBuilder();
        for(int i=0;i<moves.size();i++){
            list.append(i<cursor?"● ":"○ ").append(i+1).append(". ").append(moves.get(i)).append('\n');
        }
        history.setText(list.length()==0?"기보 없음":list.toString());
    }
    private static String token(String line,String key){
        String[] parts=line.split(" +");
        for(int i=0;i<parts.length-1;i++)if(parts[i].equals(key))return parts[i+1];
        return "—";
    }
    private void showInfo(Engine.State st,String line){
        if(current==null||!current.ongoing()||!current.fen.equals(st.fen))return;
        String score=AnalysisScore.format(line);
        int pv=1;try{pv=Integer.parseInt(token(line,"multipv"));}catch(Exception ignored){}
        String movesPart=line.substring(line.indexOf(" pv ")+4).trim();
        String[] lineMoves=movesPart.split(" +");
        if(lineMoves.length==0)return;
        if(pv>=1&&pv<=3)variations[pv-1]=pv+". "+score+" · "+
                String.join(" ",Arrays.asList(lineMoves).subList(0,Math.min(8,lineMoves.length)));
        if(pv==1){
            evaluation.setText(score);
            board.hint=lineMoves[0];board.repaint();
        }
        StringBuilder msg=new StringBuilder("깊이: "+token(line,"depth")+"\nNPS: "+
                token(line,"nps")+"\n현재 최선수: "+lineMoves[0]+"\n\n후보 수순\n");
        for(String s:variations)if(!s.isEmpty())msg.append(s).append("\n\n");
        details.setText(msg.toString());
    }
    private void toggle(){
        if(a==null||loading){JOptionPane.showMessageDialog(window,"먼저 엔진 EXE를 선택하세요.");return;}
        if(running){running=false;refresh(false);}
        else {running=true;refresh(true);}
    }
    private void tap(String square){
        if(current==null||!current.ongoing()||mode.equals("arena")||
                (mode.equals("play")&&current.white!=humanWhite))return;
        if(!board.selected.isEmpty()){
            String move=board.selected+square;
            if(!board.selected.equals(square)&&current.canPlay(move)){commit(move);return;}
        }
        board.selected=square;board.targets.clear();
        for(String move:current.legal){
            String[] sq=DesktopPosition.splitMove(move);
            if(sq!=null&&sq[0].equals(square)&&!sq[0].equals(sq[1]))board.targets.add(sq[1]);
        }
        board.repaint();
    }
    private void commit(String move){
        if(current==null||!current.canPlay(move))return;
        while(moves.size()>cursor)moves.remove(moves.size()-1);
        moves.add(move);cursor++;
        if(mode.equals("play"))running=true;
        refresh(running);
    }
    private void navigate(int offset){
        int next=cursor+offset;
        if(next<0||next>moves.size()||loading)return;
        cursor=next;running=false;refresh(false);
    }
    private void passMove(){
        if(current==null||!current.ongoing()||!VariantConfig.allowsPass(variant))return;
        for(String move:current.legal){
            String[] p=DesktopPosition.splitMove(move);
            if(p!=null&&p[0].equals(p[1])){commit(move);return;}
        }
    }
    private void selectVariant(String selected){
        ruleHelp.setText(html(VariantConfig.description(selected)));
        if(selected.equals(variant)||loading)return;
        if(a==null){variant=selected;prefs.put("variant",variant);return;}
        int id=cancel();running=false;loading=true;status.setText("규칙 변경 중...");
        String previous=variant;
        worker.execute(()->{
            try{
                Engine.State x=a.setVariant(selected),y=b.setVariant(selected);
                if(!x.startFen.equals(y.startFen))throw new IOException("엔진별 초기 국면 불일치");
                onUi(id,()->{
                    variant=selected;prefs.put("variant",variant);variantStart=x.startFen;
                    initialFen=x.startFen;moves.clear();cursor=0;loading=false;refresh(false);
                });
            }catch(Exception ex){
                try{a.setVariant(previous);b.setVariant(previous);}catch(Exception ignored){}
                onUi(id,()->{
                    loading=false;rules.setSelectedIndex(VariantConfig.index(previous));
                    status.setText("규칙 변경 실패: "+ex.getMessage());refresh(false);
                });
            }
        });
    }
    private void newGame(){
        if(a==null||loading)return;
        String[] names={"마상상마","상마마상","마상마상","상마상마"};
        JComboBox<String> cho=new JComboBox<>(names), han=new JComboBox<>(names);
        JPanel content=new JPanel(new GridLayout(4,1));
        content.add(new JLabel("초 차림 (초 기준 왼쪽부터)"));content.add(cho);
        content.add(new JLabel("한 차림 (한 기준 왼쪽부터)"));content.add(han);
        if(JOptionPane.showConfirmDialog(window,content,"새 대국",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String[] forms={"RNBA1ABNR","RBNA1ANBR","RNBA1ANBR","RBNA1ABNR"};
        String[] parts=variantStart.split(" "), ranks=parts[0].split("/");
        ranks[0]=forms[han.getSelectedIndex()].toLowerCase(Locale.ROOT);
        ranks[9]=forms[cho.getSelectedIndex()];
        parts[0]=String.join("/",ranks);initialFen=String.join(" ",parts);
        moves.clear();cursor=0;running=false;refresh(false);
    }
    private static String pieceLabel(char piece){
        if(piece=='?')return "기존 기물 이동 / 교환";
        if(piece==' ')return "지우기";
        String side=Character.isUpperCase(piece)?"초 ":"한 ";
        String name;
        switch(Character.toLowerCase(piece)){
            case 'k':name="궁";break;case 'a':name="사";break;case 'b':name="상";break;
            case 'n':name="마";break;case 'r':name="차";break;case 'c':name="포";break;
            default:name="졸/병";
        }
        return side+name+" ("+piece+")";
    }
    private void editPosition(){
        if(a==null||loading){JOptionPane.showMessageDialog(window,"엔진을 먼저 선택하세요.");return;}
        running=false;cancel();
        DesktopPosition edited=new DesktopPosition(current==null?initialFen:current.fen);
        JDialog dialog=new JDialog(window,"포지션 편집 · 초/한 관점",true);
        dialog.setLayout(new BorderLayout(4,4));
        BoardSurface preview=new BoardSurface();preview.model=edited;preview.flipped=board.flipped;
        dialog.add(preview,BorderLayout.CENTER);
        char[] tokens={'?',' ','K','A','B','N','R','C','P','k','a','b','n','r','c','p'};
        String[] labels=new String[tokens.length];
        for(int i=0;i<tokens.length;i++)labels[i]=pieceLabel(tokens[i]);
        JComboBox<String> palette=new JComboBox<>(labels);
        JComboBox<String> turnSide=new JComboBox<>(new String[]{"초 차례","한 차례"});
        turnSide.setSelectedIndex(edited.white?0:1);
        final JButton[] orientationButtonLabel=new JButton[1];
        JButton orientation=button(preview.flipped?"한 관점 · 초로 뒤집기":"초 관점 · 한으로 뒤집기",()->{
            preview.flipped=!preview.flipped;preview.selected="";preview.repaint();
            orientationButtonLabel[0].setText(preview.flipped?"한 관점 · 초로 뒤집기":"초 관점 · 한으로 뒤집기");
        });
        orientationButtonLabel[0]=orientation;
        JPanel tools=new JPanel(new GridLayout(2,2,8,7));
        tools.add(palette);tools.add(turnSide);tools.add(orientation);
        tools.add(text("탭: 배치 / 드래그: 이동",12,new Color(40,65,62)));
        dialog.add(tools,BorderLayout.NORTH);
        preview.onTap=square->{
            char choice=tokens[palette.getSelectedIndex()];
            if(choice!='?'){edited.set(square,choice);preview.selected="";}
            else if(preview.selected.isEmpty()){
                if(edited.piece(square)!=' ')preview.selected=square;
            } else if(preview.selected.equals(square))preview.selected="";
            else {
                char p=edited.piece(preview.selected),q=edited.piece(square);
                edited.set(preview.selected,q);edited.set(square,p);preview.selected="";
            }
            preview.repaint();
        };
        preview.onDrag=(from,to)->{
            char choice=tokens[palette.getSelectedIndex()];
            if(choice!='?'){edited.set(to,choice);}
            else if(edited.piece(from)!=' '){
                char p=edited.piece(from),q=edited.piece(to);
                edited.set(from,q);edited.set(to,p);
            }
            preview.selected="";preview.repaint();
        };
        JButton apply=button("적용",()->{
            edited.white=turnSide.getSelectedIndex()==0;
            String error=edited.validate();
            if(error!=null){JOptionPane.showMessageDialog(dialog,error,"편집 오류",JOptionPane.WARNING_MESSAGE);return;}
            initialFen=edited.toFen();moves.clear();cursor=0;
            board.flipped=preview.flipped;running=false;
            dialog.dispose();refresh(false);
        });
        JPanel bottom=row(button("취소",dialog::dispose),apply);
        dialog.add(bottom,BorderLayout.SOUTH);
        dialog.setSize(630,760);dialog.setLocationRelativeTo(window);
        dialog.setVisible(true);
    }
    private void chooseEngine(){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Fairy-Stockfish / JanggiLab Windows EXE");
        chooser.setFileFilter(new FileNameExtensionFilter("Windows EXE (*.exe)","exe"));
        if(!exePath.isEmpty())chooser.setSelectedFile(new File(exePath));
        if(chooser.showOpenDialog(window)==JFileChooser.APPROVE_OPTION)
            loadEngines(chooser.getSelectedFile().getAbsolutePath(),nnuePath,true);
    }
    private void chooseNetwork(){
        JFileChooser chooser=new JFileChooser();chooser.setDialogTitle("Fairy-Stockfish 장기 NNUE");
        chooser.setFileFilter(new FileNameExtensionFilter("NNUE (*.nnue)","nnue"));
        if(chooser.showOpenDialog(window)==JFileChooser.APPROVE_OPTION)
            setNetwork(chooser.getSelectedFile().getAbsolutePath());
    }
    private void setNetwork(String path){
        if(a==null){nnuePath=path;return;}
        loadEngines(exePath,path,false); // swap only after validation succeeds
    }
    private void choosePhotos(){
        JFileChooser chooser=new JFileChooser();
        chooser.setDialogTitle("기물 PNG 폴더 선택 (K,A,B,N,R,C,P / 소문자 = 한)");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(chooser.showOpenDialog(window)==JFileChooser.APPROVE_OPTION){
            prefs.put("pieceDir",chooser.getSelectedFile().getAbsolutePath());
            board.reloadImages();board.repaint();
        }
    }
    private void engineSettings(){
        JSpinner choSkill=new JSpinner(new SpinnerNumberModel(skillCho,0,20,1));
        JSpinner hanSkill=new JSpinner(new SpinnerNumberModel(skillHan,0,20,1));
        JSpinner choMs=new JSpinner(new SpinnerNumberModel(timeCho,250,10000,250));
        JSpinner hanMs=new JSpinner(new SpinnerNumberModel(timeHan,250,10000,250));
        JSpinner count=new JSpinner(new SpinnerNumberModel(threads,1,32,1));
        JSpinner mb=new JSpinner(new SpinnerNumberModel(hash,16,1024,16));
        JPanel settings=new JPanel(new GridLayout(6,2,8,5));
        for(Object[] row:new Object[][]{
            {"초 엔진 레벨",choSkill},{"한 엔진 레벨",hanSkill},
            {"초 생각 시간(ms)",choMs},{"한 생각 시간(ms)",hanMs},
            {"스레드",count},{"해시(MB)",mb}
        }){settings.add(new JLabel((String)row[0]));settings.add((Component)row[1]);}
        if(JOptionPane.showConfirmDialog(window,settings,"엔진 옵션",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
            skillCho=(int)choSkill.getValue();skillHan=(int)hanSkill.getValue();
            timeCho=(int)choMs.getValue();timeHan=(int)hanMs.getValue();
            threads=(int)count.getValue();hash=(int)mb.getValue();
            prefs.putInt("skillCho",skillCho);prefs.putInt("skillHan",skillHan);
            prefs.putInt("timeCho",timeCho);prefs.putInt("timeHan",timeHan);
            prefs.putInt("threads",threads);prefs.putInt("hash",hash);
            running=false;refresh(false);
        }
    }
    private void close(){
        if(closed)return;closed=true;
        cancel();
        prefs.put("variant",variant);prefs.put("initialFen",initialFen);
        worker.shutdownNow();
        if(a!=null)a.close();if(b!=null)b.close();
        if(!stagedNetworkPath.isEmpty())
            try{Files.deleteIfExists(Paths.get(stagedNetworkPath));}catch(IOException ignored){}
        window.dispose();
    }

    /** One board renderer for game, analysis and perspective-correct position editing. */
    private final class BoardSurface extends JPanel {
        DesktopPosition model;
        String selected="",last="",hint="";
        boolean flipped;
        final Set<String> targets=new HashSet<>();
        Consumer<String> onTap;
        BiConsumer<String,String> onDrag;
        final Map<Character,BufferedImage> images=new HashMap<>();
        double cell,ox,oy;
        String mouseDown;
        BoardSurface(){
            setOpaque(true);setBackground(new Color(238,219,182));
            setPreferredSize(new Dimension(670,730));
            reloadImages();
            MouseAdapter handler=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){mouseDown=hit(e.getX(),e.getY());}
                @Override public void mouseReleased(MouseEvent e){
                    String up=hit(e.getX(),e.getY());
                    if(mouseDown!=null&&up!=null&&!mouseDown.equals(up)&&onDrag!=null)onDrag.accept(mouseDown,up);
                    else if(up!=null&&onTap!=null)onTap.accept(up);
                    mouseDown=null;
                }
            };
            addMouseListener(handler);
        }
        void reloadImages(){
            images.clear();
            String override=prefs.get("pieceDir","");
            for(char piece:"KABNRCPkabnrcp".toCharArray()){
                try{
                    BufferedImage img=null;
                    if(!override.isEmpty()){
                        Path path=Paths.get(override,String.valueOf(piece)+".png");
                        if(Files.isRegularFile(path))img=ImageIO.read(path.toFile());
                    }
                    if(img==null){
                        try(InputStream in=getClass().getResourceAsStream("/pieces/"+piece+".png")){
                            if(in!=null)img=ImageIO.read(in);
                        }
                    }
                    if(img!=null)images.put(piece,img);
                }catch(Exception ignored){}
            }
        }
        String hit(int px,int py){
            if(cell<=0)return null;
            int f=(int)Math.round((px-ox)/cell),r=(int)Math.round((py-oy)/cell);
            return DesktopPosition.viewToSquare(f,r,flipped);
        }
        int sx(int f){return (int)Math.round(ox+(flipped?8-f:f)*cell);}
        int sy(int r){return (int)Math.round(oy+(flipped?r:9-r)*cell);}
        @Override protected void paintComponent(Graphics raw){
            super.paintComponent(raw);
            Graphics2D g=(Graphics2D)raw.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                int width=getWidth(),height=getHeight();
                g.setPaint(new GradientPaint(0,0,new Color(244,228,188),width,height,new Color(214,180,124)));
                g.fillRoundRect(5,5,width-10,height-10,16,16);
                cell=Math.min((width-46.0)/8.5,(height-46.0)/9.5);
                ox=(width-8*cell)/2;oy=(height-9*cell)/2;
                g.setColor(new Color(122,91,57));g.setStroke(new BasicStroke(1.5f));
                for(int f=0;f<9;f++)g.drawLine(sx(f),sy(0),sx(f),sy(9));
                for(int r=0;r<10;r++)g.drawLine(sx(0),sy(r),sx(8),sy(r));
                g.drawLine(sx(3),sy(0),sx(5),sy(2));g.drawLine(sx(5),sy(0),sx(3),sy(2));
                g.drawLine(sx(3),sy(7),sx(5),sy(9));g.drawLine(sx(5),sy(7),sx(3),sy(9));
                if(model==null)return;
                String[] previous=DesktopPosition.splitMove(last);
                if(previous!=null)for(String square:previous){
                    int[] at=DesktopPosition.parse(square);
                    g.setColor(new Color(76,155,135,80));
                    int rad=(int)(cell*.45);g.fillOval(sx(at[0])-rad,sy(at[1])-rad,rad*2,rad*2);
                }
                for(int r=0;r<10;r++)for(int f=0;f<9;f++){
                    char piece=model.squares[r][f];if(piece==' ')continue;
                    int x=sx(f),y=sy(r);
                    int rad=(int)(cell*(Character.toLowerCase(piece)=='k'?.43:.38));
                    BufferedImage bitmap=images.get(piece);
                    if(bitmap!=null){
                        int ww=(int)(cell*1.08),hh=(int)(cell*.98);
                        g.drawImage(bitmap,x-ww/2,y-hh/2,ww,hh,null);
                    }else{
                        Polygon oct=new Polygon();
                        for(int i=0;i<8;i++){
                            double a=Math.PI/8+i*Math.PI/4;
                            oct.addPoint(x+(int)(rad*Math.cos(a)),y+(int)(rad*Math.sin(a)));
                        }
                        g.setColor(new Color(255,246,222));g.fillPolygon(oct);
                        g.setStroke(new BasicStroke(2));
                        g.setColor(Character.isUpperCase(piece)?new Color(9,94,129):new Color(175,49,53));
                        g.drawPolygon(oct);
                        String label=hanja(piece);
                        Font font=new Font("Malgun Gothic",Font.BOLD,(int)(cell*.47));
                        g.setFont(font);FontMetrics met=g.getFontMetrics();
                        g.drawString(label,x-met.stringWidth(label)/2,y+(met.getAscent()-met.getDescent())/2);
                    }
                    if(DesktopPosition.coord(f,r).equals(selected)){
                        g.setColor(new Color(233,152,39));g.setStroke(new BasicStroke(3));
                        g.drawOval(x-rad-3,y-rad-3,2*rad+6,2*rad+6);
                    }
                }
                for(String sq:targets){
                    int[] at=DesktopPosition.parse(sq);if(at==null)continue;
                    int radius=(int)Math.max(3,cell*.11);
                    g.setColor(new Color(10,115,93,180));
                    g.fillOval(sx(at[0])-radius,sy(at[1])-radius,radius*2,radius*2);
                }
                String[] suggested=DesktopPosition.splitMove(hint);
                if(suggested!=null&&!suggested[0].equals(suggested[1])){
                    int[] src=DesktopPosition.parse(suggested[0]),dst=DesktopPosition.parse(suggested[1]);
                    int x1=sx(src[0]),y1=sy(src[1]),x2=sx(dst[0]),y2=sy(dst[1]);
                    g.setColor(new Color(221,143,24,180));g.setStroke(new BasicStroke(4));
                    g.drawLine(x1,y1,x2,y2);
                    double angle=Math.atan2(y2-y1,x2-x1);
                    Polygon arrow=new Polygon();arrow.addPoint(x2,y2);
                    arrow.addPoint(x2-(int)(cell*.38*Math.cos(angle-.55)),y2-(int)(cell*.38*Math.sin(angle-.55)));
                    arrow.addPoint(x2-(int)(cell*.38*Math.cos(angle+.55)),y2-(int)(cell*.38*Math.sin(angle+.55)));
                    g.fillPolygon(arrow);
                }
            }finally{g.dispose();}
        }
        private String hanja(char piece){
            switch(Character.toLowerCase(piece)){
                case 'k':return Character.isUpperCase(piece)?"楚":"漢";
                case 'a':return "士";case 'b':return "象";case 'n':return "馬";
                case 'r':return "車";case 'c':return "包";
                default:return Character.isUpperCase(piece)?"卒":"兵";
            }
        }
    }
}
