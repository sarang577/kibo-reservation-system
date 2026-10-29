package com.kibo.reservation;

import com.kibo.reservation.entity.Drop;
import com.kibo.reservation.repository.DropRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@SpringBootApplication
@EnableScheduling
public class KiboReservationApplication {

    public static void main(String[] args) {
        SpringApplication.run(KiboReservationApplication.class, args);
    }

    @Bean
    ApplicationRunner seedDrops(DropRepository dropRepository) {
        return args -> {
            Instant now = Instant.now();
            List<DropSeed> seeds = List.of(
                    new DropSeed("Limited Sneaker Drop", 10, now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS)),
                    new DropSeed("Concert Ticket Drop", 100, now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS)),
                    new DropSeed("Exclusive Collectible Drop", 25, now.minus(1, ChronoUnit.HOURS), now.plus(7, ChronoUnit.DAYS))
            );

            for (DropSeed seed : seeds) {
                if (!dropRepository.existsByName(seed.name())) {
                    Drop drop = new Drop();
                    drop.setName(seed.name());
                    drop.setTotalQuantity(seed.quantity());
                    drop.setAvailableQuantity(seed.quantity());
                    drop.setStartsAt(seed.startsAt());
                    drop.setEndsAt(seed.endsAt());
                    dropRepository.save(drop);
                }
            }
        };
    }

    private record DropSeed(String name, int quantity, Instant startsAt, Instant endsAt) {}
}
