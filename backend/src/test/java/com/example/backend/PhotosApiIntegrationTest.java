package com.example.backend;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.backend.entity.PhotoAssetEntity;
import com.example.backend.entity.PhotoAssetEntity.PhotoStatus;
import com.example.backend.repository.PhotoAssetRepository;
import com.example.backend.service.PhotoService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 照片管道集成测试(H2 内存库):上传校验序、免鉴权回图、日限、GC、40400 不泄露存在性。
 * 用例矩阵对齐 docs/design/v2.2-family-design.md §3 photos 组;照片字节用 ImageIO 真实 PNG 编码。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhotosApiIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private PhotoAssetRepository photoAssetRepository;

    @Autowired
    private PhotoService photoService;

    private RestClient rest;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final AtomicInteger userCounter = new AtomicInteger();

    @BeforeEach
    void setUp() {
        rest = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    // ---- 工具 ----

    private String login() {
        return post("/api/auth/login", null, Map.of("code", "dev-photos-" + userCounter.incrementAndGet()))
                .get("token").asText();
    }

    private JsonNode get(String path, String token) {
        return exchange(HttpMethod.GET, path, token, null);
    }

    private JsonNode post(String path, String token, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, token, body);
    }

    private JsonNode exchange(HttpMethod method, String path, String token, Map<String, Object> body) {
        RestClient.RequestBodySpec spec = rest.method(method).uri(path);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        String response = body == null
                ? spec.retrieve().body(String.class)
                : spec.body(body).retrieve().body(String.class);
        return parse(response);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("响应不是合法 JSON: " + body, e);
        }
    }

    private void assertErrorCode(JsonNode response, String code) {
        assertEquals(code, response.get("code").asText(), "响应: " + response);
    }

    /** 4×4 红 PNG 真实编码(设计 §3:照片字节用 ImageIO) */
    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                image.setRGB(x, y, 0xFF0000);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** 8 字节 JPEG 头载荷(嗅探 FF D8 FF 即 jpeg,小载荷打日限最快) */
    private byte[] jpegHeaderBytes(int size) {
        byte[] data = new byte[size];
        data[0] = (byte) 0xFF;
        data[1] = (byte) 0xD8;
        data[2] = (byte) 0xFF;
        for (int i = 3; i < size; i++) {
            data[i] = 0x00;
        }
        return data;
    }

    private JsonNode upload(String token, byte[] data, String scene) {
        return post("/api/photos", token, Map.of(
                "imageBase64", Base64.getEncoder().encodeToString(data),
                "scene", scene));
    }

    private ResponseEntity<byte[]> getPhotoBytes(String photoId, String token) {
        RestClient.RequestHeadersSpec<?> spec = rest.get().uri("/api/photos/" + photoId);
        if (token != null) {
            spec.header("Authorization", "Bearer " + token);
        }
        return spec.retrieve().toEntity(byte[].class);
    }

    private String getPhotoBody(String photoId) {
        return rest.get().uri("/api/photos/" + photoId).retrieve().body(String.class);
    }

    // ---- 上传 ----

    @Test
    void 上传主路径_photoId高熵与回图字节一致() throws Exception {
        String token = login();
        byte[] data = pngBytes();
        JsonNode uploaded = upload(token, data, "MOMENT");

        String photoId = uploaded.get("photoId").asText();
        assertTrue(photoId.matches("[0-9a-zA-Z]{16,20}"), "photoId 须为 16-20 位 base62: " + photoId);
        assertEquals("/api/photos/" + photoId, uploaded.get("photoUrl").asText());
        assertEquals(data.length, uploaded.get("sizeBytes").asInt());

        // 免鉴权回图:不带 token,字节全等、Content-Type 按 mime、长缓存头
        ResponseEntity<byte[]> entity = getPhotoBytes(photoId, null);
        assertEquals(200, entity.getStatusCode().value());
        assertArrayEquals(data, entity.getBody());
        assertEquals("image/png", entity.getHeaders().getContentType().toString());
        String cacheControl = entity.getHeaders().getCacheControl();
        assertTrue(cacheControl.contains("public"), cacheControl);
        assertTrue(cacheControl.contains("max-age=31536000"), cacheControl);
        assertTrue(cacheControl.contains("immutable"), cacheControl);
        assertEquals("nosniff", entity.getHeaders().getFirst("X-Content-Type-Options"));
        // 带 token 也 200(免鉴权不排斥)
        assertEquals(200, getPhotoBytes(photoId, token).getStatusCode().value());
    }

    @Test
    void 字节头嗅探与mimeType交叉校验() {
        String token = login();
        // mimeType 缺省:按嗅探 jpeg 通过
        JsonNode jpeg = upload(token, jpegHeaderBytes(16), "CAPSULE");
        assertEquals("image/jpeg", getPhotoBytes(jpeg.get("photoId").asText(), null)
                .getHeaders().getContentType().toString());
        assertEquals(16, jpeg.get("sizeBytes").asInt());
        // mimeType 与字节头不符 → 40000(PNG 数据谎报 jpeg)
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", Base64.getEncoder().encodeToString(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0}),
                "scene", "MOMENT", "mimeType", "image/jpeg")), "40000");
        // mimeType 白名单外 → 40000
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", Base64.getEncoder().encodeToString(jpegHeaderBytes(16)),
                "scene", "MOMENT", "mimeType", "image/gif")), "40000");
        // 字节头嗅不出 → 40000
        assertErrorCode(upload(token, new byte[] {1, 2, 3, 4, 5}, "MOMENT"), "40000");
    }

    @Test
    void 超长base64不解码即拒绝_解码超限与非法base64被拒() {
        String token = login();
        // >700k 字符:先验长度,不解码超大载荷
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", "A".repeat(700_001), "scene", "MOMENT")), "40000");
        // 解码后 512001 字节 → 40000
        assertErrorCode(upload(token, jpegHeaderBytes(512_001), "MOMENT"), "40000");
        // 非法 base64 → 40000
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", "!!!not-base64!!!", "scene", "MOMENT")), "40000");
        // 空白载荷 → 40000
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", "  ", "scene", "MOMENT")), "40000");
    }

    @Test
    void scene非法与缺失被拒() {
        String token = login();
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", Base64.getEncoder().encodeToString(jpegHeaderBytes(8)),
                "scene", "FOO")), "40000");
        assertErrorCode(post("/api/photos", token, Map.of(
                "imageBase64", Base64.getEncoder().encodeToString(jpegHeaderBytes(8)))), "40000");
    }

    @Test
    void 未登录上传被拒_回图免鉴权() throws Exception {
        assertErrorCode(post("/api/photos", null, Map.of(
                "imageBase64", Base64.getEncoder().encodeToString(pngBytes()),
                "scene", "MOMENT")), "40100");
    }

    @Test
    void 日限第21张被拒() {
        String token = login();
        for (int i = 0; i < 20; i++) {
            JsonNode uploaded = upload(token, jpegHeaderBytes(8), "MOMENT");
            assertFalse(uploaded.has("code"), "前 20 张必须全部成功,第 " + (i + 1) + " 张失败: " + uploaded);
        }
        assertErrorCode(upload(token, jpegHeaderBytes(8), "MOMENT"), "40300");
        // 其他用户不受影响(按 uploaderId+日期 计数)
        assertEquals(8, upload(login(), jpegHeaderBytes(8), "MOMENT").get("sizeBytes").asInt());
    }

    @Test
    void 伪造photoId与已删照片40400_响应逐字节一致() throws Exception {
        String token = login();
        JsonNode uploaded = upload(token, pngBytes(), "MOMENT");
        String photoId = uploaded.get("photoId").asText();

        // 伪造 photoId(格式合法但不存在)与非法格式,统一 40400
        assertErrorCode(parse(getPhotoBody("1234567890abcdefgh")), "40400");
        assertErrorCode(parse(getPhotoBody("abc")), "40400");

        // 置 DELETED 后回图 40400,与不存在 photoId 的响应逐字节一致(不泄露存在性)
        String beforeDelete = getPhotoBody("1234567890abcdefgh");
        PhotoAssetEntity entity = photoAssetRepository.findByPhotoId(photoId).orElseThrow();
        entity.setStatus(PhotoStatus.DELETED);
        photoAssetRepository.save(entity);
        assertEquals(beforeDelete, getPhotoBody(photoId), "DELETED 与不存在的响应须逐字节一致");
        assertErrorCode(parse(getPhotoBody(photoId)), "40400");
    }

    @Test
    void ORPHAN超48小时被GC后回图40400() {
        String token = login();
        JsonNode uploaded = upload(token, jpegHeaderBytes(8), "MOMENT");
        String photoId = uploaded.get("photoId").asText();
        assertEquals(200, getPhotoBytes(photoId, null).getStatusCode().value());

        PhotoAssetEntity entity = photoAssetRepository.findByPhotoId(photoId).orElseThrow();
        entity.setCreatedAt(LocalDateTime.now().minusHours(49));
        photoAssetRepository.save(entity);
        long removed = photoService.gcOrphanPhotos();
        assertTrue(removed >= 1, "49h ORPHAN 应被 GC 扫除");
        assertErrorCode(parse(getPhotoBody(photoId)), "40400");
    }

    @Test
    void photoId每次新签发不复用() {
        String token = login();
        JsonNode first = upload(token, jpegHeaderBytes(8), "MOMENT");
        JsonNode second = upload(token, jpegHeaderBytes(8), "MOMENT");
        assertNotEquals(first.get("photoId").asText(), second.get("photoId").asText());
    }

    @Test
    void 二进制端点不破坏apiDocs() {
        String docs = rest.get().uri("/v3/api-docs").retrieve().body(String.class);
        assertTrue(docs.contains("uploadPhoto"), "api-docs 须含照片上传 operationId");
        assertTrue(docs.contains("getPhoto"), "api-docs 须含照片回显 operationId");
        JsonNode parsed = parse(docs);
        assertNull(parsed.get("code"), "api-docs 不应走错误通道");
    }
}
