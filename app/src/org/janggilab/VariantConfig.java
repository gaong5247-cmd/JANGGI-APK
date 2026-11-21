package org.janggilab;

/** Only 9x10 Janggi family rules are accepted at the process boundary. */
final class VariantConfig {
    static final String[] IDS={"janggitraditional","janggimodern","jangginopass","janggiblitz"};
    static final String[] NAMES={"전통 장기","현대 장기","패스 없는 장기","블리츠 장기"};
    /** Describes the options in variants.ini (these are application rule presets). */
    static final String[] DESCRIPTIONS={
        "빅장 허용 · 한 수 쉼 가능 · 같은 국면 3회 반복 시 무승부 · 연속 장군 제한 · 기물 점수 판정",
        "빅장 없음 · 한 수 쉼 가능 · 왕복 반복 금지 및 같은 국면 4회 반복 · 연속 장군 제한 · 100수 무진행 제한 · 기물 점수 판정",
        "전통 장기와 같은 빅장·점수·3회 반복 규칙이지만 한 수 쉼이 불가능합니다",
        "빅장 없음 · 한 수 쉼 가능 · 같은 국면 2회 반복 시 무승부 · 연속 장군 금지 규칙 해제 · 기물 점수 판정 없음"
    };
    static String description(String id) { return DESCRIPTIONS[index(id)]; }
    static int index(String id) { for(int i=0;i<IDS.length;i++)if(IDS[i].equals(id))return i;return 0; }
    static String saved(String id) { for(String v:IDS)if(v.equals(id))return v;return IDS[0]; }
    static void require(String id) {
        if ("janggi".equals(id)) return;
        for(String v:IDS)if(v.equals(id))return;
        throw new IllegalArgumentException("지원하지 않는 장기 규칙: "+id);
    }
    static boolean allowsPass(String id) { return !"jangginopass".equals(id); }
    static boolean hasBikjang(String id) { return !"janggimodern".equals(id)&&!"janggiblitz".equals(id); }
    static boolean countsMaterial(String id) { return !"janggiblitz".equals(id); }
}
