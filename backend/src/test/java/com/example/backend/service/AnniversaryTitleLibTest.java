package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 周年称谓库纯单测:词条覆盖、未收录为 null、25 条全过违禁词表(对齐设计 §3 用例 23) */
class AnniversaryTitleLibTest {

    @Test
    void 主要档位称谓正确() {
        assertEquals("纸婚", AnniversaryTitleLib.title(1));
        assertEquals("木婚", AnniversaryTitleLib.title(5));
        assertEquals("锡婚", AnniversaryTitleLib.title(10));
        assertEquals("水晶婚", AnniversaryTitleLib.title(15));
        assertEquals("瓷婚", AnniversaryTitleLib.title(20));
        assertEquals("银婚", AnniversaryTitleLib.title(25));
        assertEquals("珍珠婚", AnniversaryTitleLib.title(30));
        assertEquals("红宝石婚", AnniversaryTitleLib.title(40));
        assertEquals("金婚", AnniversaryTitleLib.title(50));
        assertEquals("钻石婚", AnniversaryTitleLib.title(60));
        assertEquals("白金婚", AnniversaryTitleLib.title(70));
    }

    @Test
    void 未收录周年返回null() {
        assertNull(AnniversaryTitleLib.title(16));
        assertNull(AnniversaryTitleLib.title(65));
        assertNull(AnniversaryTitleLib.title(0));
        assertNull(AnniversaryTitleLib.title(-1));
    }

    @Test
    void 逐年1到15全覆盖() {
        String[] expected = {"纸婚", "棉婚", "皮婚", "花果婚", "木婚", "铁婚", "铜婚", "陶婚",
                "柳婚", "锡婚", "钢婚", "丝婚", "花边婚", "象牙婚", "水晶婚"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], AnniversaryTitleLib.title(i + 1), (i + 1) + " 周年");
        }
    }

    @Test
    void 词条恰25条且全过违禁词表() {
        // 1-15 逐年 15 条 + 20/25/30/35/40/45/50/55/60/70 十档 10 条 = 25 条(枚举清单为准)
        int count = 0;
        for (int n = 1; n <= 200; n++) {
            String title = AnniversaryTitleLib.title(n);
            if (title == null) {
                continue;
            }
            count++;
            assertTrue(!title.isBlank(), "空词条: " + n);
            for (String word : DayTextLib.FORBIDDEN_WORDS) {
                assertTrue(!title.contains(word), "违禁词「" + word + "」: " + n + " " + title);
            }
        }
        assertEquals(25, count, "周年称谓库必须为 25 条");
    }
}
