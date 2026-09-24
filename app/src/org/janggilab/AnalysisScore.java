package org.janggilab;

import java.util.Locale;
import java.util.regex.*;

/** Search output only: this type deliberately cannot change Engine.State. */
final class AnalysisScore {
    private static final Pattern SCORE=Pattern.compile("\\bscore (cp|mate) (-?\\d+)\\b");
    static String format(String line) {
        Matcher m=SCORE.matcher(line);
        if (!m.find()) return "평가 대기";
        long n=Long.parseLong(m.group(2));
        String value;
        if (m.group(1).equals("cp")) value=String.format(Locale.US,"%+.2f",n/100.0);
        // Fairy-Stockfish also encodes decisive variant endings as mate scores.
        // Never claim these are current checkmate or necessarily a mating line.
        else value=(n<0?"-M":"M")+Math.abs(n)+" · "+(n==0?"탐색 평가":n<0?"패배 예측":"강제 승리 예측");
        if (line.contains(" lowerbound") || line.contains(" upperbound")) value+=" (경계값)";
        return value;
    }
    static String reason(String reason) {
        switch (reason) {
            case "checkmate": return "외통";
            case "bikjang": return "빅장 수락";
            case "double_pass": return "양측 한 수 쉼";
            case "repetition": return "반복수";
            case "perpetual_check": return "연속 장군 규칙";
            case "move_repetition": return "반복 금지 규칙";
            case "move_limit": return "무진행 수 제한";
            case "stalemate": return "합법수 없음";
            default: return "기타 규칙 판정";
        }
    }
}
