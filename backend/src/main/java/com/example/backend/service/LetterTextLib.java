package com.example.backend.service;

import com.example.backend.dto.CardDtos.LetterGenerateResponse;
import com.example.backend.dto.CardDtos.LetterSegment;
import com.example.backend.dto.CardDtos.LetterSegmentRole;
import com.example.backend.dto.CardDtos.LetterSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 感恩信降级文案库(AI 不可用/异常时兜底,用户无感):纯静态无随机,同参可复现。
 * 结构满足 D6「每段 ≥2 写法」:开头 ≥3 写法(取 2 为 variants)、每故事 2 写法、每语气 2 收尾。
 * ⚠️ 合规红线:全量文案类加载期过 DayTextLib.FORBIDDEN_WORDS 断言(命中即启动失败)。
 */
public final class LetterTextLib {

    private LetterTextLib() {
    }

    public static final int MAX_PARAGRAPH_LENGTH = 60;

    /** 固定落款,不可换(定稿时服务端校验 signText 必须等于此常量) */
    public static final String SIGN_TEXT = "—— 你的孩子";

    /** 开头 ≥3 写法(OPENING 段 variants 取前 2:默认写法 + 1 备选) */
    private static final List<String> OPENINGS = List.of(
            "妈，见字如面。",
            "亲爱的妈：",
            "妈，提笔的时候想了很久，");

    /** 每故事 ≥2 写法;键=契约 StoryKey 枚举 5 个固定素材键,与交互原型 STORY_PARAS 一致 */
    private static final Map<String, List<String>> STORY_PARAS = Map.of(
            "她总给我留饭", List.of(
                    "这些年您总给我留饭，热了又热，怕我回来吃凉的。",
                    "每次回家，锅里永远温着我那碗。"),
            "送我上学那些年", List.of(
                    "送我上学那些年，您比闹钟还准时，风雨没缺过一天。",
                    "小时候的早餐，是一碗您五点起来熬的粥。"),
            "她爱唠叨但都是好意", List.of(
                    "您爱唠叨，降温了、吃早饭了，每一句其实都是牵挂。",
                    "那些唠叨我现在才听懂，都是「我想你」。"),
            "她一个人把我带大", List.of(
                    "您一个人把我带大，从没喊过累，也没让我缺过什么。",
                    "那些年您一个人扛，我现在才知道有多重。"),
            "我离家后她学会视频", List.of(
                    "我离家以后，您学会了视频通话，每次接通都先问我「吃了没」。",
                    "您打字慢，就总发语音，六十秒，全是要紧事。"));

    /** 每语气 ≥2 收尾;键=TEAR/FUN/CALM(契约 LetterTone) */
    private static final Map<String, List<String>> TONE_ENDS = Map.of(
            "TEAR", List.of(
                    "妈，以前是您惦记我，往后换我来惦记您。",
                    "妈，您把我养大，接下来的日子，我陪您变老。"),
            "FUN", List.of(
                    "放心，您儿子/闺女在外面混得还行，就是饭没您做的好吃。",
                    "妈，别的都不敢保证，但你儿子/闺女想家这件事，保质期是永久。"),
            "CALM", List.of(
                    "不多说了，您照顾好自己，我常回来。",
                    "就写到这。天冷了，您添衣。"));

    static {
        // 启动期合规断言:开头 ≥3/每故事 ≥2/每语气 ≥2、单段 ≤60 字、全量过违禁词表;不足即阻断启动
        if (OPENINGS.size() < 3) {
            throw new IllegalStateException("感恩信开头写法不足 3 条");
        }
        for (String opening : OPENINGS) {
            assertClean("开头「" + opening + "」", opening);
        }
        for (String key : StoryKeys.ALL) {
            List<String> paras = STORY_PARAS.get(key);
            if (paras == null || paras.size() < 2) {
                throw new IllegalStateException("感恩信故事「" + key + "」写法不足 2 条");
            }
            for (String para : paras) {
                assertClean("故事「" + key + "」", para);
            }
        }
        for (String tone : List.of("TEAR", "FUN", "CALM")) {
            List<String> ends = TONE_ENDS.get(tone);
            if (ends == null || ends.size() < 2) {
                throw new IllegalStateException("感恩信语气「" + tone + "」收尾写法不足 2 条");
            }
            for (String end : ends) {
                assertClean("收尾(" + tone + ")", end);
            }
        }
        assertClean("落款", SIGN_TEXT);
    }

    /** 素材键值域(与 dto StoryKey 枚举一致;以 String 流转,避免入参绑定歧义) */
    public static final class StoryKeys {
        public static final List<String> ALL = List.of(
                "她总给我留饭", "送我上学那些年", "她爱唠叨但都是好意",
                "她一个人把我带大", "我离家后她学会视频");

        private StoryKeys() {
        }
    }

    /** OPENING 段 2 写法(默认+备选);custom recipient 非「妈妈」时替换「妈」字(收信人已在入参层过词表) */
    public static List<String> openingVariants(String recipient) {
        List<String> result = new ArrayList<>();
        for (String opening : OPENINGS.subList(0, 2)) {
            result.add(applyRecipient(opening, recipient));
        }
        return result;
    }

    /** STORY 段 2 写法 */
    public static List<String> storyVariants(String storyKey) {
        return STORY_PARAS.get(storyKey);
    }

    /** CLOSING 段 2 写法 */
    public static List<String> closingVariants(String tone) {
        return TONE_ENDS.get(tone);
    }

    /** FALLBACK 整封组装:开头+每请求故事一段(保序)+收尾,共 2+len(stories) 段;source=FALLBACK */
    public static LetterGenerateResponse assemble(String recipient, List<String> stories, String tone) {
        List<LetterSegment> segments = new ArrayList<>();
        int index = 0;
        segments.add(segment(index++, LetterSegmentRole.OPENING, null, openingVariants(recipient)));
        for (String story : stories) {
            segments.add(segment(index++, LetterSegmentRole.STORY, story, storyVariants(story)));
        }
        segments.add(segment(index, LetterSegmentRole.CLOSING, null, closingVariants(tone)));
        LetterGenerateResponse response = new LetterGenerateResponse();
        response.setGenerationId(UUID.randomUUID().toString());
        response.setSource(LetterSource.FALLBACK);
        response.setParagraphs(segments);
        response.setSignText(SIGN_TEXT);
        return response;
    }

    private static LetterSegment segment(int index, LetterSegmentRole role, String storyKey, List<String> variants) {
        LetterSegment segment = new LetterSegment();
        segment.setIndex(index);
        segment.setRole(role);
        segment.setStoryKey(storyKey);
        segment.setVariants(variants);
        segment.setSelected(variants.get(0));
        return segment;
    }

    private static String applyRecipient(String text, String recipient) {
        if (recipient == null || recipient.isBlank() || "妈妈".equals(recipient)) {
            return text;
        }
        return text.replace("妈", recipient);
    }

    private static void assertClean(String label, String text) {
        if (text.length() > MAX_PARAGRAPH_LENGTH) {
            throw new IllegalStateException("感恩信段落超过 " + MAX_PARAGRAPH_LENGTH + " 字: " + label);
        }
        for (String word : DayTextLib.FORBIDDEN_WORDS) {
            if (text.contains(word)) {
                throw new IllegalStateException("感恩信文案含违禁词「" + word + "」: " + label);
            }
        }
    }
}
