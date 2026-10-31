package com.example.backend.repository;

import com.example.backend.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByOpenid(String openid);

    /** 值班分享免鉴权只读月历(契约 v1.9.1):按 scene 反查分享者 */
    Optional<UserEntity> findByDutyShareScene(String dutyShareScene);

    /** scene 签发撞码兜底(同 MadeCardRepository.existsByShareScene 先例) */
    boolean existsByDutyShareScene(String dutyShareScene);
}
