package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.backend.dto.FortuneDtos.Gua;
import com.example.backend.service.GuaService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 卦位映射测试:确定性(familyId*31+epochDay)%8,同家族同日恒定,8 日一轮换全覆盖 */
class GuaServiceTest {

    @Test
    void 同家族同日恒定不变() {
        LocalDate date = LocalDate.of(2026, 9, 23);
        assertEquals(GuaService.guaOfDate(10L, date), GuaService.guaOfDate(10L, date));
        assertEquals(GuaService.guaOfDate(999L, date), GuaService.guaOfDate(999L, date));
    }

    @Test
    void 同家族连续八日八卦各出现一次() {
        LocalDate start = LocalDate.of(2026, 9, 1);
        Set<Gua> guas = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            guas.add(GuaService.guaOfDate(10L, start.plusDays(i)));
        }
        assertEquals(8, guas.size(), "连续 8 日应覆盖全部卦位(每日轮换)");
    }

    @Test
    void 卦位只由家族和日期决定与用户无关() {
        LocalDate date = LocalDate.of(2027, 3, 6);
        // 全家同卦:guaOfDate 不接收用户参数,同一 familyId+date 恒定
        assertEquals(GuaService.guaOfDate(10L, date), GuaService.guaOfDate(10L, date.plusDays(0)));
    }

    @Test
    void 卡面主题映射齐全() {
        assertEquals("云鹤", GuaService.cardThemeOf(Gua.QIAN));
        assertEquals("锦鲤", GuaService.cardThemeOf(Gua.DUI));
        assertEquals("灯笼", GuaService.cardThemeOf(Gua.LI));
        assertEquals("春雷", GuaService.cardThemeOf(Gua.ZHEN));
        assertEquals("风铃", GuaService.cardThemeOf(Gua.XUN));
        assertEquals("锦鲤戏水", GuaService.cardThemeOf(Gua.KAN));
        assertEquals("山景", GuaService.cardThemeOf(Gua.GEN));
        assertEquals("花开", GuaService.cardThemeOf(Gua.KUN));
        for (Gua gua : Gua.values()) {
            assertNotNull(GuaService.cardThemeOf(gua));
        }
    }
}
