package com.example.backend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 照片管道契约模型(v1.8.0 tag=photos):base64 JSON 上传与免鉴权回图。
 * scene/mimeType 用 String 承接 + service 层枚举校验,非法值统一回 40000(不静默降级)。
 */
public final class PhotoDtos {

    private PhotoDtos() {
    }

    /** 照片用途声明:绑定期校验一致性(MOMENT=家人瞬间封面,CAPSULE=时光胶囊附图) */
    public enum PhotoScene {
        MOMENT, CAPSULE;

        @JsonCreator
        public static PhotoScene fromValue(String value) {
            for (PhotoScene scene : values()) {
                if (scene.name().equals(value)) {
                    return scene;
                }
            }
            return null;
        }
    }

    /** 照片 mime 白名单(回图 Content-Type 同源) */
    public enum PhotoMimeType {
        JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp");

        private final String mime;

        PhotoMimeType(String mime) {
            this.mime = mime;
        }

        public String getMime() {
            return mime;
        }

        public static PhotoMimeType fromMime(String value) {
            for (PhotoMimeType type : values()) {
                if (type.mime.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    public static class PhotoUploadRequest {
        private String imageBase64;
        @Schema(implementation = PhotoScene.class, description = "上传场景;非法值由服务端校验回 40000")
        private String scene;
        @Schema(implementation = PhotoMimeType.class, description = "图片 mime;非法值由服务端校验回 40000")
        private String mimeType;

        public String getImageBase64() { return imageBase64; }
        public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
        public String getScene() { return scene; }
        public void setScene(String scene) { this.scene = scene; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    }

    public static class PhotoUploadResponse {
        private String photoId;
        private String photoUrl;
        private int sizeBytes;

        public String getPhotoId() { return photoId; }
        public void setPhotoId(String photoId) { this.photoId = photoId; }
        public String getPhotoUrl() { return photoUrl; }
        public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
        public int getSizeBytes() { return sizeBytes; }
        public void setSizeBytes(int sizeBytes) { this.sizeBytes = sizeBytes; }
    }
}
