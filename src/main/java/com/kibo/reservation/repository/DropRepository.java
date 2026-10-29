package com.kibo.reservation.repository;

import com.kibo.reservation.entity.Drop;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DropRepository extends JpaRepository<Drop, Long> {
    boolean existsByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Drop d where d.id = :id")
    Optional<Drop> findByIdForUpdate(@Param("id") Long id);
}
