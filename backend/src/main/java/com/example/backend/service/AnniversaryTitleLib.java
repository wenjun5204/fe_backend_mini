package com.example.backend.service;

import java.util.Map;

/**
 * 结婚周年称谓静态库(1-15 逐年 15 条 + 20/25/30/35/40/45/50/55/60/70 十档 10 条,共 25 条)。
 * 模式对齐 DayTextLib:固定文化词条无运营变更,随代码管理;
 * ⚠️ 合规红线:全量词条类加载期过 DayTextLib.FORBIDDEN_WORDS 断言(命中即启动失败),key 升序唯一。
 */
public final class AnniversaryTitleLib {

    private AnniversaryTitleLib() {
    }

    private static final Map<Integer, String> TITLES = Map.ofEntries(
            Map.entry(1, "纸婚"),
            Map.entry(2, "棉婚"),
            Map.entry(3, "皮婚"),
            Map.entry(4, "花果婚"),
            Map.entry(5, "木婚"),
            Map.entry(6, "铁婚"),
            Map.entry(7, "铜婚"),
            Map.entry(8, "陶婚"),
            Map.entry(9, "柳婚"),
            Map.entry(10, "锡婚"),
            Map.entry(11, "钢婚"),
            Map.entry(12, "丝婚"),
            Map.entry(13, "花边婚"),
            Map.entry(14, "象牙婚"),
            Map.entry(15, "水晶婚"),
            Map.entry(20, "瓷婚"),
            Map.entry(25, "银婚"),
            Map.entry(30, "珍珠婚"),
            Map.entry(35, "珊瑚婚"),
            Map.entry(40, "红宝石婚"),
            Map.entry(45, "蓝宝石婚"),
            Map.entry(50, "金婚"),
            Map.entry(55, "翡翠婚"),
            Map.entry(60, "钻石婚"),
            Map.entry(70, "白金婚"));

    /** 周年称谓:未收录(如 16/65 周年)返回 null,前端仅显示「N 周年」 */
    public static String title(int years) {
        return TITLES.get(years);
    }

    static {
        // 启动期断言:25 条(spec「24 条」为枚举笔误,以枚举清单为准)、词条过违禁词表;
        // key 唯一由 Map.ofEntries 天然保证(重复 key 构造期即抛)
        if (TITLES.size() != 25) {
            throw new IllegalStateException("周年称谓库必须为 25 条,实际 " + TITLES.size());
        }
        for (Map.Entry<Integer, String> entry : TITLES.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalStateException("周年称谓词条为空: " + entry.getKey());
            }
            for (String word : DayTextLib.FORBIDDEN_WORDS) {
                if (entry.getValue().contains(word)) {
                    throw new IllegalStateException("周年称谓含违禁词「" + word + "」: "
                            + entry.getKey() + " " + entry.getValue());
                }
            }
        }
    }
}
