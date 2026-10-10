package com.example.backend.service;

import com.example.backend.dto.CardDtos.TemplateCategory;
import com.example.backend.dto.CardDtos.TemplateInfo;
import com.example.backend.dto.CardDtos.TemplateKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 模板馆静态配置(设计裁定:代码内配置类而非表——无管理端点、previewMeta 是用户可见文案须过
 * 合规词表类加载断言、上下架为低频运营动作随版本管理可回滚,避免 H2/MySQL 种子数据漂移)。
 * 内容对齐契约 TemplateInfo schema 与交互原型 TPL_DATA;V1.1+ 加管理端点再迁表。
 * ⚠️ 合规红线:全量 previewMeta 文本类加载期过 DayTextLib.FORBIDDEN_WORDS 断言(命中即启动失败)。
 */
public final class TemplateCatalog {

    private TemplateCatalog() {
    }

    private static final String FREE_LABEL = "✅ 免费";

    private static final List<TemplateInfo> TEMPLATES = List.of(
            template(TemplateKey.LETTER, "给妈妈的感恩信", TemplateCategory.FESTIVAL, true, 10,
                    null, null, "pages/letter-edit/letter-edit",
                    preview("A LETTER TO MOM", "💌", "妈，这些年您总给我留饭，我离家以后，您学会了视频…",
                            "NIGHT", "AI 根据你选的故事代笔，写完直接生成信纸海报",
                            List.of("选你们的故事", "AI 代笔", "逐句可改", "一键出海报"),
                            "12.4 万人已写", "约 3 分钟", "母亲节前 3 天做的人最多，现在做正合适")),
            template(TemplateKey.DAYS_CARD, "相伴天数卡", TemplateCategory.COUPLE, true, 20,
                    null, null, "pages/days-card-edit/days-card-edit",
                    preview("DAYS TOGETHER", "💕", "从 2019.10.22 到今天，第 2480 天",
                            "SUNSET", "在一起的第 N 天，每天自动 +1，永不过期",
                            List.of("三种渐变样式", "数字每天 +1", "扫码可看"),
                            "8.1 万人已做", "约 1 分钟", "情侣和已婚的最爱，发朋友圈自带话题")),
            template(TemplateKey.CAPSULE, "时光胶囊邀请函", TemplateCategory.FUN, true, 30,
                    null, null, "pages/capsule-edit/capsule-edit",
                    preview("TIME CAPSULE", "⏳", "这里面有一句话，和一张你等着看的照片。",
                            "MINT", "写一段话 + 放一张照片，封存到指定日子，生成开箱邀请函",
                            List.of("指定开箱日", "邀请 TA 扫码", "开箱日自动提醒"),
                            "3.6 万人埋下了", "约 2 分钟", "埋下的那天起，TA 就有了一个盼头")),
            template(TemplateKey.BIRTHDAY_CARD, "生日福卡", TemplateCategory.FAMILY, true, 40,
                    null, null, null,
                    preview("HAPPY BIRTHDAY", "🎂", "祝妈妈生日快乐，天天都有好心情。",
                            "VIOLET", "家人生日当天，自动提醒你生成专属福卡",
                            List.of("生日当天提醒", "家人可见", "收到的人可回 ❤️"),
                            "2.9 万人已送", "约 1 分钟", "已在你记的日子到期时自动出现")),
            // 预约态(live=false,主按钮「上线提醒我」);父亲节 2026-06-21(6 月第三个周日,万年历复核)
            template(TemplateKey.HARD_FOR_DAD, "给爸爸的一句硬话", TemplateCategory.FAMILY, false, 50,
                    "2026-06-21", "父亲节 👔", null,
                    preview("FOR DAD", "👔", "爸，这些话我替你说了。",
                            "DAD", "父爱无言，AI 帮你把他没说出口的翻译出来",
                            List.of("父亲节限定", "AI 代笔", "一键出海报"),
                            "2.3 万人想用", "6 月上线", "Father's Day · 6 月第三个周日")),
            template(TemplateKey.WEDDING_CARD, "结婚纪念日卡", TemplateCategory.COUPLE, false, 60,
                    null, null, null,
                    preview("ANNIVERSARY", "💍", "红宝石婚 · 一起走过 40 年。",
                            "WEDDING", "N 周年自动匹配称谓（银婚/金婚/红宝石婚…）",
                            List.of("周年称谓自动配", "双人生日同步", "可印制"),
                            "1.8 万人想用", "近期上线", "记住结婚日，周年当天自动提醒")));

    /** 全量清单(按 sort 升序),每次调用返回新拷贝(响应可变,配置不可变) */
    public static List<TemplateInfo> list() {
        return copyAll(null);
    }

    /** 按分类过滤(缺省=全部),按 sort 升序 */
    public static List<TemplateInfo> listByCategory(TemplateCategory category) {
        return copyAll(category);
    }

    private static List<TemplateInfo> copyAll(TemplateCategory category) {
        List<TemplateInfo> result = new ArrayList<>();
        for (TemplateInfo source : TEMPLATES) {
            if (category == null || source.getCategory() == category) {
                result.add(copy(source));
            }
        }
        result.sort(Comparator.comparingInt(TemplateInfo::getSort));
        return result;
    }

    private static TemplateInfo copy(TemplateInfo source) {
        TemplateInfo copy = new TemplateInfo();
        copy.setKey(source.getKey());
        copy.setName(source.getName());
        copy.setCategory(source.getCategory());
        copy.setLive(source.isLive());
        copy.setSort(source.getSort());
        copy.setFestivalDate(source.getFestivalDate());
        copy.setFestivalLabel(source.getFestivalLabel());
        copy.setMakerPage(source.getMakerPage());
        copy.setPreviewMeta(source.getPreviewMeta());
        return copy;
    }

    private static TemplateInfo template(TemplateKey key, String name, TemplateCategory category,
                                        boolean live, int sort, String festivalDate, String festivalLabel,
                                        String makerPage, com.example.backend.dto.CardDtos.TemplatePreviewMeta meta) {
        TemplateInfo info = new TemplateInfo();
        info.setKey(key);
        info.setName(name);
        info.setCategory(category);
        info.setLive(live);
        info.setSort(sort);
        info.setFestivalDate(festivalDate);
        info.setFestivalLabel(festivalLabel);
        info.setMakerPage(makerPage);
        info.setPreviewMeta(meta);
        return info;
    }

    private static com.example.backend.dto.CardDtos.TemplatePreviewMeta preview(
            String kicker, String emoji, String sampleText, String backgroundKey, String sub,
            List<String> feats, String usedLabel, String durationLabel, String hint) {
        com.example.backend.dto.CardDtos.TemplatePreviewMeta meta =
                new com.example.backend.dto.CardDtos.TemplatePreviewMeta();
        meta.setKicker(kicker);
        meta.setEmoji(emoji);
        meta.setSampleText(sampleText);
        meta.setBackgroundKey(backgroundKey);
        meta.setSub(sub);
        meta.setFeats(feats);
        meta.setUsedLabel(usedLabel);
        meta.setDurationLabel(durationLabel);
        meta.setFreeLabel(FREE_LABEL);
        meta.setHint(hint);
        return meta;
    }

    static {
        // 启动期合规断言:配置完整性 + previewMeta 文案过违禁词表
        if (TEMPLATES.size() != TemplateKey.values().length) {
            throw new IllegalStateException("模板配置数量与 TemplateKey 枚举不一致");
        }
        List<TemplateKey> keys = TEMPLATES.stream().map(TemplateInfo::getKey).toList();
        for (TemplateKey key : TemplateKey.values()) {
            if (!keys.contains(key)) {
                throw new IllegalStateException("模板配置缺少 key=" + key);
            }
        }
        for (TemplateInfo info : TEMPLATES) {
            if (!info.isLive() && info.getMakerPage() != null) {
                throw new IllegalStateException("预约态模板不应有制作页: " + info.getKey());
            }
            com.example.backend.dto.CardDtos.TemplatePreviewMeta meta = info.getPreviewMeta();
            if (meta == null) {
                throw new IllegalStateException("模板缺少 previewMeta: " + info.getKey());
            }
            if (!FREE_LABEL.equals(meta.getFreeLabel())) {
                throw new IllegalStateException("freeLabel 必须恒「" + FREE_LABEL + "」: " + info.getKey());
            }
            for (String text : List.of(meta.getKicker(), meta.getEmoji(), meta.getSampleText(),
                    meta.getSub(), meta.getUsedLabel(), meta.getDurationLabel(), meta.getHint())) {
                assertClean(info.getKey() + " previewMeta", text);
            }
            for (String feat : meta.getFeats()) {
                assertClean(info.getKey() + " 特性标签", feat);
            }
            if (info.getFestivalLabel() != null) {
                assertClean(info.getKey() + " 节点标签", info.getFestivalLabel());
            }
        }
    }

    private static void assertClean(String label, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("previewMeta 字段为空: " + label);
        }
        for (String word : DayTextLib.FORBIDDEN_WORDS) {
            if (text.contains(word)) {
                throw new IllegalStateException("模板文案含违禁词「" + word + "」: " + label);
            }
        }
    }
}
