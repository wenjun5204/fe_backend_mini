package com.example.backend.dto;

/** 解忧册契约模型:JieyouQuoteType / JieyouQuoteResponse */
public final class JieyouDtos {

    private JieyouDtos() {
    }

    /** 宽心话三型:促成(推一把)/缓行(等一等)/放下(松一松) */
    public enum JieyouQuoteType { CU_CHENG, HUAN_XING, FANG_XIA }

    public static class JieyouQuoteResponse {
        private int quoteId;
        private JieyouQuoteType type;
        /** 宽心话正文,≤16 字,指向行动,禁吉凶与承诺结果词汇 */
        private String text;
        /** 册页码(1-120,前端展示用) */
        private int pageNo;

        public int getQuoteId() { return quoteId; }
        public void setQuoteId(int quoteId) { this.quoteId = quoteId; }
        public JieyouQuoteType getType() { return type; }
        public void setType(JieyouQuoteType type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public int getPageNo() { return pageNo; }
        public void setPageNo(int pageNo) { this.pageNo = pageNo; }
    }
}
