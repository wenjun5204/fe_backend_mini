package com.example.backend.repository;

import com.example.backend.entity.NotifyGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotifyGrantRepository extends JpaRepository<NotifyGrantEntity, Long> {

    /** 用户累计接受的授权条数(每次接受 = 一条可发送额度) */
    long countByUserIdAndAcceptedTrue(Long userId);
}
