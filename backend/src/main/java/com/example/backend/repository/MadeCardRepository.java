package com.example.backend.repository;

import com.example.backend.dto.CardDtos.CardStatus;
import com.example.backend.entity.MadeCardEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MadeCardRepository extends JpaRepository<MadeCardEntity, Long> {

    /** 我的卡片墙:仅本人 ACTIVE,按创建时间降序 */
    java.util.List<MadeCardEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, CardStatus status);

    /** 访客端按 scene 查卡:软删(DELETED)在此过滤,统一 40400 不泄露存在性 */
    Optional<MadeCardEntity> findByShareSceneAndStatus(String shareScene, CardStatus status);

    /** scene 签发预检(全库唯一索引为真防线,预检只为减少撞码重试) */
    boolean existsByShareScene(String shareScene);
}
