package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.backend.dto.FortuneDtos.PlateLevel;
import com.example.backend.service.BlessService;
import org.junit.jupiter.api.Test;

/** 门牌等级规则:500/2000/5000/20000,只升不降 */
class BlessServicePlateTest {

    @Test
    void 零福值为无门牌() {
        assertEquals(PlateLevel.NONE, BlessService.upgradePlate(0));
    }

    @Test
    void 达到500解锁勤俭之家() {
        assertEquals(PlateLevel.BRONZE, BlessService.upgradePlate(500));
        assertEquals(PlateLevel.BRONZE, BlessService.upgradePlate(1999));
    }

    @Test
    void 达到2000解锁和睦之家() {
        assertEquals(PlateLevel.SILVER, BlessService.upgradePlate(2000));
    }

    @Test
    void 达到5000解锁满门福气() {
        assertEquals(PlateLevel.GOLD, BlessService.upgradePlate(5000));
    }

    @Test
    void 达到20000解锁福泽满堂() {
        assertEquals(PlateLevel.JADE, BlessService.upgradePlate(20000));
        assertEquals(PlateLevel.JADE, BlessService.upgradePlate(999999));
    }

    @Test
    void 距下一级门牌差值正确() {
        assertEquals(500, BlessService.nextPlateGap(0));
        assertEquals(1500, BlessService.nextPlateGap(500));
        assertEquals(1, BlessService.nextPlateGap(4999));
        assertEquals(0, BlessService.nextPlateGap(20000), "最高门牌已达,差值为 0");
    }
}
