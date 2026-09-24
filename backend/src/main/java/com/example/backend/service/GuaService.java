package com.example.backend.service;

import com.example.backend.dto.FortuneDtos.Gua;
import java.time.LocalDate;
import java.util.Map;

/**
 * 卦位映射:确定性、无状态,同家族同日恒定,跨日轮换;不参与福签等级概率。
 * ⚠️ 合规红线:卦位只影响卡面主题(cardTheme),文案禁卦象/卦运/吉位等词。
 */
public final class GuaService {

    private GuaService() {
    }

    /** 卦位顺序:乾兑离震巽坎艮坤(与契约 trigrams 固定顺序一致) */
    private static final Gua[] VALUES = Gua.values();

    /** 卡面主题映射:乾=云鹤/兑=锦鲤/离=灯笼/震=春雷/巽=风铃/坎=锦鲤戏水/艮=山景/坤=花开 */
    private static final Map<Gua, String> CARD_THEMES = Map.of(
            Gua.QIAN, "云鹤",
            Gua.DUI, "锦鲤",
            Gua.LI, "灯笼",
            Gua.ZHEN, "春雷",
            Gua.XUN, "风铃",
            Gua.KAN, "锦鲤戏水",
            Gua.GEN, "山景",
            Gua.KUN, "花开");

    /** 确定性卦位:(familyId*31 + epochDay) % 8 → 乾兑离震巽坎艮坤 */
    public static Gua guaOfDate(Long familyId, LocalDate date) {
        long index = Math.floorMod(familyId * 31 + date.toEpochDay(), VALUES.length);
        return VALUES[(int) index];
    }

    /** 卦位对应卡面主题(内置常量表) */
    public static String cardThemeOf(Gua gua) {
        return CARD_THEMES.get(gua);
    }
}
