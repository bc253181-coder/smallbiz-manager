package com.smallbiz.billing.online;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentNotificationRepository extends JpaRepository<PaymentNotification, Long> {

    List<PaymentNotification> findByOrderNoOrderByIdAsc(String orderNo);

    @Modifying
    @Transactional
    @Query("delete from PaymentNotification n where n.createdAt < :before")
    int deleteOlderThan(Instant before);
}
