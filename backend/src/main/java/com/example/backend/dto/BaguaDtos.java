package com.example.backend.dto;

import com.example.backend.dto.FortuneDtos.Gua;
import java.util.List;

/** 八卦集福阵契约模型:BaguaTrigramProgress / BaguaStatusResponse / BaguaCelebrateResponse */
public final class BaguaDtos {

    private BaguaDtos() {
    }

    public static class BaguaTrigramProgress {
        private Gua gua;
        private boolean lit;
        private int current;
        private int target;
        /** 进度提示文案,过合规词表(禁卦象/卦运/吉位等) */
        private String hint;

        public Gua getGua() { return gua; }
        public void setGua(Gua gua) { this.gua = gua; }
        public boolean isLit() { return lit; }
        public void setLit(boolean lit) { this.lit = lit; }
        public int getCurrent() { return current; }
        public void setCurrent(int current) { this.current = current; }
        public int getTarget() { return target; }
        public void setTarget(int target) { this.target = target; }
        public String getHint() { return hint; }
        public void setHint(String hint) { this.hint = hint; }
    }

    public static class BaguaStatusResponse {
        /** 8 卦进度(固定顺序:乾兑离震巽坎艮坤) */
        private List<BaguaTrigramProgress> trigrams;
        private int litCount;
        private boolean complete;
        /** 圆满大阵图庆祝是否已触发(一次性,服务端标记防重复);未圆满时为 false */
        private boolean celebrated;

        public List<BaguaTrigramProgress> getTrigrams() { return trigrams; }
        public void setTrigrams(List<BaguaTrigramProgress> trigrams) { this.trigrams = trigrams; }
        public int getLitCount() { return litCount; }
        public void setLitCount(int litCount) { this.litCount = litCount; }
        public boolean isComplete() { return complete; }
        public void setComplete(boolean complete) { this.complete = complete; }
        public boolean isCelebrated() { return celebrated; }
        public void setCelebrated(boolean celebrated) { this.celebrated = celebrated; }
    }

    public static class BaguaCelebrateResponse {
        /** true=本次标记成功或此前已标记(幂等) */
        private boolean celebrated;

        public boolean isCelebrated() { return celebrated; }
        public void setCelebrated(boolean celebrated) { this.celebrated = celebrated; }
    }
}
