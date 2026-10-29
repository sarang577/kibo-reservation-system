package com.kibo.reservation.repository;

import com.kibo.reservation.entity.Hold;
import com.kibo.reservation.entity.HoldStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface HoldRepository extends JpaRepository<Hold, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hold h join fetch h.drop where h.id = :id")
    Optional<Hold> findByIdForUpdate(@Param("id") String id);

    List<Hold> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            HoldStatus status, Instant now);
}
