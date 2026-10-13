package com.example.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.backend.dto.CardDtos.LetterGenerateResponse;
import com.example.backend.dto.CardDtos.LetterSegment;
import com.example.backend.dto.CardDtos.LetterSegmentRole;
import com.example.backend.dto.CardDtos.LetterSource;
import com.example.backend.repository.LetterGenerateQuotaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 感恩信 AI 生成单测(设计 §3 用例 7):把「调网关」抽为包内可覆写方法,喂伪造 JSON 覆盖
 * 解析/过滤/降级分支;任何路径不返回违禁词(三层防线双重断言)。
 */
class AiLetterServiceTest {

    private static final List<String> STORIES = List.of("她总给我留饭", "送我上学那些年", "她爱唠叨但都是好意");

    /** 测试替身:固定网关响应(合法 JSON,每段 2 写法) */
    private AiLetterService serviceWithGateway(String gatewayContent) {
        AiLetterService service = new AiLetterService(new ObjectMapper(),
                mock(LetterGenerateQuotaRepository.class)) {
            @Override
            String callGateway(String requestBody) {
                if (gatewayContent == null) {
                    throw new IllegalStateException("模拟网关超时/IO 异常");
                }
                return gatewayContent;
            }
        };
        service.setConfig("https://ai.test/v1", "test-key", "gpt-test");
        return service;
    }

    private AiLetterService fallbackService() {
        AiLetterService service = new AiLetterService(new ObjectMapper(),
                mock(LetterGenerateQuotaRepository.class));
        service.setConfig("https://ai.test/v1", "", "gpt-test");
        return service;
    }

    private void assertNoForbiddenWord(LetterGenerateResponse response) {
        for (LetterSegment segment : response.getParagraphs()) {
            for (String variant : segment.getVariants()) {
                for (String word : DayTextLib.FORBIDDEN_WORDS) {
                    assertFalse(variant.contains(word),
                            "段落含违禁词「" + word + "」: " + variant);
                }
            }
        }
    }

    // ---- 用例 7:AI 三层防线 ----

    @Test
    void 无Key环境直接走FALLBACK且结构合规() {
        LetterGenerateResponse response = fallbackService().generate("妈妈", STORIES, "TEAR");
        assertEquals(LetterSource.FALLBACK, response.getSource());
        assertEquals(2 + STORIES.size(), response.getParagraphs().size());
        assertStructureValid(response);
        assertNoForbiddenWord(response);
    }

    @Test
    void AI返回合法JSON_来源为AI且结构合规() {
        String aiJson = """
                {"opening":["亲爱的妈妈：","妈，这封信想了很久才动笔。"],
                 "stories":[
                   {"key":"她总给我留饭","variants":["这些年您总给我留饭，热了又热。","每次回家，锅里永远温着我那碗。"]},
                   {"key":"送我上学那些年","variants":["送我上学那些年，您比闹钟还准时。","小时候的早餐，是一碗您五点起来熬的粥。"]},
                   {"key":"她爱唠叨但都是好意","variants":["您爱唠叨，每一句其实都是牵挂。","那些唠叨我现在才听懂。"]}],
                 "closing":["妈，往后换我来惦记您。","妈，接下来的日子，我陪您变老。"]}
                """;
        LetterGenerateResponse response = serviceWithGateway(aiJson).generate("妈妈", STORIES, "TEAR");
        assertEquals(LetterSource.AI, response.getSource());
        assertEquals(5, response.getParagraphs().size());
        assertStructureValid(response);
        assertEquals("亲爱的妈妈：", response.getParagraphs().get(0).getSelected());
        assertNoForbiddenWord(response);
    }

    @Test
    void AI某段含违禁词_该写法被丢弃并以降级写法补位() {
        String aiJson = """
                {"opening":["亲爱的妈妈：","妈，这封信想了很久才动笔。"],
                 "stories":[
                   {"key":"她总给我留饭","variants":["您总给我留饭，这个不行。","每次回家，锅里永远温着我那碗。"]},
                   {"key":"送我上学那些年","variants":["送我上学那些年，您比闹钟还准时。","小时候的早餐，是一碗您五点起来熬的粥。"]},
                   {"key":"她爱唠叨但都是好意","variants":["您爱唠叨，每一句其实都是牵挂。","那些唠叨我现在才听懂。"]}],
                 "closing":["妈，往后换我来惦记您。","妈，接下来的日子，我陪您变老。"]}
                """;
        LetterGenerateResponse response = serviceWithGateway(aiJson).generate("妈妈", STORIES, "TEAR");
        // 解析成功即 source=AI(个别段被过滤补位仍标 AI——source 仅埋点用)
        assertEquals(LetterSource.AI, response.getSource());
        LetterSegment story = response.getParagraphs().get(1);
        assertEquals(2, story.getVariants().size(), "违禁写法被丢弃后须以降级写法补齐到 2 条");
        // 响应任何文本不含违禁词(第二层防线断言)
        assertNoForbiddenWord(response);
    }

    @Test
    void AI返回非JSON_整封降级FALLBACK() {
        LetterGenerateResponse response = serviceWithGateway("这不是 JSON，网关被 markdown 包裹了").generate(
                "妈妈", STORIES, "TEAR");
        assertEquals(LetterSource.FALLBACK, response.getSource());
        assertStructureValid(response);
        assertNoForbiddenWord(response);
    }

    @Test
    void AI故事数与请求不符_整封降级FALLBACK() {
        String aiJson = """
                {"opening":["亲爱的妈妈：","妈，这封信想了很久才动笔。"],
                 "stories":[{"key":"她总给我留饭","variants":["您总给我留饭。","锅里温着我那碗。"]}],
                 "closing":["妈，往后换我来惦记您。","妈，我陪您变老。"]}
                """;
        LetterGenerateResponse response = serviceWithGateway(aiJson).generate("妈妈", STORIES, "TEAR");
        assertEquals(LetterSource.FALLBACK, response.getSource());
        assertStructureValid(response);
    }

    @Test
    void AI故事键不匹配_整封降级FALLBACK() {
        String aiJson = """
                {"opening":["亲爱的妈妈：","妈，这封信想了很久才动笔。"],
                 "stories":[
                   {"key":"她总给我留饭","variants":["您总给我留饭。","锅里温着我那碗。"]},
                   {"key":"另一个故事","variants":["别的。","别的写法。"]},
                   {"key":"她爱唠叨但都是好意","variants":["您爱唠叨。","唠叨是牵挂。"]}],
                 "closing":["妈，往后换我来惦记您。","妈，我陪您变老。"]}
                """;
        LetterGenerateResponse response = serviceWithGateway(aiJson).generate("妈妈", STORIES, "TEAR");
        assertEquals(LetterSource.FALLBACK, response.getSource());
        assertStructureValid(response);
    }

    @Test
    void AI网关异常_静默降级FALLBACK接口不报错() {
        LetterGenerateResponse response = serviceWithGateway(null).generate("妈妈", STORIES, "TEAR");
        assertEquals(LetterSource.FALLBACK, response.getSource());
        assertStructureValid(response);
        assertNoForbiddenWord(response);
    }

    @Test
    void 自定义收信人_开头替换且无违禁词() {
        LetterGenerateResponse response = fallbackService().generate("奶奶", STORIES, "CALM");
        assertEquals(LetterSource.FALLBACK, response.getSource());
        assertTrue(response.getParagraphs().get(0).getSelected().contains("奶奶"),
                "开头「妈」字应替换为自定义收信人");
        assertFalse(response.getParagraphs().get(0).getSelected().contains("妈，"),
                "替换后不应残留「妈，」开头");
        assertNoForbiddenWord(response);
    }

    // ---- 结构断言(段数/段序/variants≥2/selected∈variants) ----

    private void assertStructureValid(LetterGenerateResponse response) {
        List<LetterSegment> paragraphs = response.getParagraphs();
        assertEquals(LetterSegmentRole.OPENING, paragraphs.get(0).getRole());
        assertEquals(LetterSegmentRole.CLOSING, paragraphs.get(paragraphs.size() - 1).getRole());
        for (int i = 0; i < paragraphs.size(); i++) {
            LetterSegment segment = paragraphs.get(i);
            assertEquals(i, segment.getIndex());
            assertTrue(segment.getVariants().size() >= 2, "每段备选写法 ≥2: 段 " + i);
            assertTrue(segment.getVariants().contains(segment.getSelected()),
                    "selected 必须是 variants 之一");
            if (segment.getRole() == LetterSegmentRole.STORY) {
                assertEquals(STORIES.get(i - 1), segment.getStoryKey(), "故事段保序");
            } else {
                assertEquals(null, segment.getStoryKey());
            }
        }
        assertEquals("—— 你的孩子", response.getSignText());
    }
}
