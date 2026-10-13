package com.example.backend.service;

import com.example.backend.dto.JieyouDtos.JieyouQuoteType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 解忧册宽心话文案库:促成(推一把)/缓行(等一等)/放下(松一松)×40,共 120 条。
 * 全部指向具体行动、口语化、面向中老年,每条 ≤16 字;纯随机下发,不接收不存储任何问题内容。
 * ⚠️ 合规红线:全量文案启动时过违禁词表断言(JieyouService 启动调用 validate(),测试期另有
 * JieyouQuoteLibTest 扫描),词表见 .agents/docs/conventions.md 含 v1.5 追加词。
 */
public final class JieyouQuoteLib {

    private JieyouQuoteLib() {
    }

    public static final int QUOTE_COUNT = 120;
    public static final int MAX_TEXT_LENGTH = 16;
    private static final int QUOTES_PER_TYPE = 40;

    /**
     * 违禁词表(与 conventions.md + v1.5 追加词同步):文案中出现即 Bug。
     * v1.5 追加:能成/不行/会有/注定/天意/预示/破财/有灾/卦象/卦运/宜忌/生门/吉位/凶位。
     */
    public static final List<String> FORBIDDEN_WORDS = List.of(
            "能成", "不行", "会有", "注定", "天意", "预示", "破财", "有灾",
            "运势", "吉凶", "求签", "算命", "占卜", "八字", "塔罗", "改运", "挡灾",
            "化解", "开光", "大师", "卦象", "卦运", "宜忌", "生门", "吉位", "凶位",
            "命中注定", "犯太岁", "本命", "凶", "忌", "招财进宝",
            "降血压", "降血糖", "治疗", "理疗", "排毒", "药用", "偏方");

    /** 一条宽心话:id 即册页码(1-120),三型各 40 条 */
    public record Quote(int id, JieyouQuoteType type, String text) {
    }

    private static final List<Quote> QUOTES = buildQuotes();

    private static List<Quote> buildQuotes() {
        List<Quote> quotes = new ArrayList<>();
        int id = 0;
        for (String text : List.of(
                "想问的话，今天就问出口",
                "明天先打个电话给对方",
                "想见面，就约个时间见见",
                "把想说的话，先说一句试试",
                "先迈一小步，后面就顺了",
                "这事今天起个头就好",
                "找老朋友喝杯茶，聊聊近况",
                "想做的事，别等下个月",
                "先把窗打开，让阳光进来",
                "想念谁，现在就告诉谁",
                "主动打个招呼，话就开了",
                "今天先去做，做完再评价",
                "犹豫的事，先干十分钟",
                "把主意写下来，明天去办",
                "先应下来，再慢慢准备",
                "话到嘴边，就说出来吧",
                "约上老伴，出门走一走",
                "想学的东西，今天报个名",
                "先伸出手，握一握再说",
                "今天把那件小事办了它",
                "问问孩子，最近过得咋样",
                "想吃的馆子，这周就去",
                "别憋着，找人说说话",
                "先开口的人，不吃亏",
                "翻出旧照片，给家人讲讲",
                "想帮就搭把手，别多想",
                "今天先问一句，再决定",
                "见面三分情，多走动走动",
                "心里的话，挑一句说说",
                "先试试看，试了才知道",
                "该问的就问，别自己猜",
                "想回老家看看，就定日子",
                "给老邻居倒杯茶，聊两句",
                "想说的谢谢，今天说出口",
                "先约顿饭，事情桌上聊",
                "别等来日方长，今天就联系",
                "想到就做，做一点是一点",
                "把电话拨出去，响一声也算",
                "主动一回，心里就松快了",
                "今天把想说的话说完它")) {
            quotes.add(new Quote(++id, JieyouQuoteType.CU_CHENG, text));
        }
        for (String text : List.of(
                "这事先放三天再说",
                "睡一觉，明早再想它",
                "不急，先把饭吃好",
                "明天再答复，也不迟",
                "先缓一缓，事看得更清",
                "走慢点，路还长着呢",
                "这事先记下，周末再办",
                "气头上别说话，先喝口水",
                "等心平了，再拿主意",
                "先放一放，回头再看",
                "忙就先歇歇，事跑不掉",
                "大事缓三天，小事缓半天",
                "先睡个好觉，事明天说",
                "等孩子有空，再细聊",
                "别急着回话，想好再说",
                "先散步一圈，回来再想",
                "火气下去一半，再开口",
                "这周先不动，下周再看",
                "缓两天，兴许有转机",
                "先把觉睡足，再操心",
                "不差这一天，慢慢来",
                "等消息的，先好好吃饭",
                "心里乱时，先不拿主意",
                "事再复杂，先捋一捋",
                "先问清楚，再动手不迟",
                "越着急的事，越要慢半拍",
                "先放下半天，出去走走",
                "缓一缓，给彼此留余地",
                "明早脑子清爽了再说",
                "别连夜想事，费神",
                "先听家人说完，再表态",
                "话到嘴边，先咽半句",
                "等风头过去，再走动",
                "先顾好自己，再顾事",
                "事缓则圆，先静一静",
                "把决定留到饭后",
                "先量力而行，别硬撑",
                "缓几天，问问过来人",
                "再急的事，也先喝口茶",
                "今天想不通，明天再想")) {
            quotes.add(new Quote(++id, JieyouQuoteType.HUAN_XING, text));
        }
        for (String text : List.of(
                "过去的事，翻篇就轻了",
                "管不了的事，交给时间",
                "儿孙自有儿孙福",
                "少操一份心，多睡一小时",
                "别人的话，听过就放下",
                "已经过去，就别再想了",
                "旧事重提，累的是自己",
                "放不下的，先放一边",
                "心里的石头，搬开一块",
                "计较少了，日子就宽了",
                "这事不归您管，歇着吧",
                "天塌不下来，先睡觉",
                "少惦记点，饭才吃得香",
                "睡前把烦心事放门外",
                "想不通的，先不想了",
                "不是您的错，别自责",
                "让孩子自己闯一闯",
                "松开手，各自都自在",
                "该忘的忘，该留的留",
                "心宽一寸，路宽一丈",
                "别跟自己较劲了",
                "放下一桩，轻松一桩",
                "过不去的坎，绕着走",
                "眼不见，心就少烦一分",
                "少想多做，心就静了",
                "昨天的雨，淋不湿今天",
                "别人的日子，别拿来比",
                "管好自己，就是帮大家",
                "心事说出口，就轻一半",
                "不必事事都操心",
                "老了就歇歇，不丢人",
                "留三分糊涂，多七分自在",
                "想开些，日子还长",
                "把心放宽，觉就沉了",
                "松开手，新日子才进得来",
                "忘性大一点，烦恼少一点",
                "睡个踏实觉，比啥都强",
                "先把自己哄高兴了",
                "放一放，事就慢慢淡了",
                "儿女的事，让儿女拿主意")) {
            quotes.add(new Quote(++id, JieyouQuoteType.FANG_XIA, text));
        }
        return List.copyOf(quotes);
    }

    /** 纯随机一条(120 条均匀抽取) */
    public static Quote randomQuote() {
        return QUOTES.get(ThreadLocalRandom.current().nextInt(QUOTES.size()));
    }

    /** 按页码取一条(1-120;当日幂等回读用) */
    public static Quote byId(int quoteId) {
        if (quoteId < 1 || quoteId > QUOTES.size()) {
            throw new IllegalArgumentException("宽心话页码越界: " + quoteId);
        }
        return QUOTES.get(quoteId - 1);
    }

    /** 全量合规自检:违禁词命中、超长、数量/分型不足即抛异常(启动期与测试期共用) */
    public static void validate() {
        if (QUOTES.size() != QUOTE_COUNT) {
            throw new IllegalStateException("宽心话总数应为 " + QUOTE_COUNT + ",实际 " + QUOTES.size());
        }
        Map<JieyouQuoteType, Integer> counts = new EnumMap<>(JieyouQuoteType.class);
        for (int i = 0; i < QUOTES.size(); i++) {
            Quote quote = QUOTES.get(i);
            if (quote.id() != i + 1) {
                throw new IllegalStateException("宽心话页码应连续: " + quote.id());
            }
            counts.merge(quote.type(), 1, Integer::sum);
            if (quote.text() == null || quote.text().isBlank()) {
                throw new IllegalStateException("宽心话为空: 第" + quote.id() + "页");
            }
            if (quote.text().length() > MAX_TEXT_LENGTH) {
                throw new IllegalStateException("宽心话超过 " + MAX_TEXT_LENGTH + " 字: 第" + quote.id() + "页 " + quote.text());
            }
            for (String word : FORBIDDEN_WORDS) {
                if (quote.text().contains(word)) {
                    throw new IllegalStateException("宽心话包含违禁词「" + word + "」: 第" + quote.id() + "页 " + quote.text());
                }
            }
        }
        for (JieyouQuoteType type : JieyouQuoteType.values()) {
            if (counts.getOrDefault(type, 0) != QUOTES_PER_TYPE) {
                throw new IllegalStateException(type + " 应为 " + QUOTES_PER_TYPE + " 条,实际 "
                        + counts.getOrDefault(type, 0));
            }
        }
    }
}
