package org.janggilab;

/** Only 9x10 Janggi family rules are accepted at the process boundary. */
final class VariantConfig {
    static final String[] IDS={"janggitraditional","janggimodern","jangginopass","janggiblitz"};
    static final String[] NAMES={"전통 장기","현대 장기","패스 없는 장기","블리츠 장기"};
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
