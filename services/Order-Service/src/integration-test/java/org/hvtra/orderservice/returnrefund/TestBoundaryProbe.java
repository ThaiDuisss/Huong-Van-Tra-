package org.hvtra.orderservice.returnrefund;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Test-only observation/fault injection. Repository calls and writes always execute for real. */
@Aspect
class TestBoundaryProbe {
    @PersistenceContext private EntityManager entityManager;
    private final JdbcTemplate jdbc;
    volatile boolean holdOrderLock;
    volatile String failAfterNotification;
    volatile CountDownLatch locked = new CountDownLatch(1);
    volatile CountDownLatch release = new CountDownLatch(1);
    private final AtomicBoolean owner = new AtomicBoolean();
    final List<Map<String, Object>> events = new CopyOnWriteArrayList<>();

    TestBoundaryProbe(DataSource dataSource) { jdbc = new JdbcTemplate(dataSource); }

    void reset() {
        holdOrderLock = false;
        failAfterNotification = null;
        locked = new CountDownLatch(1);
        release = new CountDownLatch(1);
        owner.set(false);
        events.clear();
    }

    @Around("execution(* org.hvtra.orderservice.repository.OrderRepository.findByIdAndIsDeletedFalseWithItemsForUpdate(..))")
    Object observeLock(ProceedingJoinPoint call) throws Throwable {
        if (!holdOrderLock) return call.proceed();
        events.add(Map.of("point", "before-order-lock", "thread", Thread.currentThread().getName(),
                "connectionId", jdbc.queryForObject("SELECT CONNECTION_ID()", Long.class),
                "entityManagerIdentity", System.identityHashCode(entityManager.getDelegate()),
                "nanoTime", System.nanoTime()));
        Object result = call.proceed();
        events.add(Map.of("point", "after-order-lock", "thread", Thread.currentThread().getName(),
                "nanoTime", System.nanoTime()));
        if (owner.compareAndSet(false, true)) {
            locked.countDown();
            if (!release.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("Lock probe release timed out");
        }
        return result;
    }

    @Around("execution(* org.hvtra.orderservice.outbox.service.OrderOutboxPublisher.publishReturn*Notification(..))")
    Object afterRealWrite(ProceedingJoinPoint call) throws Throwable {
        Object result = call.proceed();
        if (call.getSignature().getName().equals(failAfterNotification)) {
            entityManager.flush();
            events.add(Map.of("point", "flushed-before-injected-failure",
                    "returns", jdbc.queryForObject("SELECT COUNT(*) FROM order_returns", Long.class),
                    "items", jdbc.queryForObject("SELECT COUNT(*) FROM order_return_items", Long.class),
                    "outbox", jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events", Long.class)));
            throw new InjectedFailure();
        }
        return result;
    }

    static class InjectedFailure extends RuntimeException {
        InjectedFailure() { super("Controlled failure after real persistence flush"); }
    }
}
