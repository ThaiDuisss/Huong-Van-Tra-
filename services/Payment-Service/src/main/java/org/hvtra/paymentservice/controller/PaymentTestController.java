package org.hvtra.paymentservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.hvtra.commonservice.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequestMapping("/internal/payments/test")
public class PaymentTestController {

    private final AtomicInteger unstableCounter = new AtomicInteger(0);

    @GetMapping("/success")
    public ResponseEntity<?> success() {
        log.info("[Payment] success called at {}", LocalTime.now());

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Payment Service OK"
        ));
    }

    @GetMapping("/error")
    public ResponseEntity<?> error() {
        log.info("[Payment] error called at {}", LocalTime.now());

        throw new BaseException("loi", "loi", HttpStatus.BAD_REQUEST){
        };
    }

    @GetMapping("/slow")
    public ResponseEntity<?> slow() throws InterruptedException {
        log.info("[Payment] slow called at {}", LocalTime.now());

        Thread.sleep(3000);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Payment Service slow response after 3s"
        ));
    }

    @GetMapping("/unstable")
    public ResponseEntity<?> unstable() {
        int count = unstableCounter.incrementAndGet();

        log.info("[Payment] unstable called count={} at {}", count, LocalTime.now());

        if (count % 3 != 0) {
            throw new RuntimeException("Payment Service unstable error, count=" + count);
        }

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Payment Service success on attempt " + count
        ));
    }

    @GetMapping("/reset")
    public ResponseEntity<?> reset() {
        unstableCounter.set(0);

        return ResponseEntity.ok(Map.of(
                "message", "Counter reset"
        ));
    }
}