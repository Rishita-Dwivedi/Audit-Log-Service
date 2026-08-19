package com.auditlog;

import com.auditlog.dto.VerifyResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the concurrency-control mechanism in com.auditlog.service.AuditEventService
 * (pessimistic lock on chain_head): fires many writes from separate threads at roughly the
 * same time and asserts the resulting chain is still contiguous and internally consistent.
 * Scope note (docs/TESTING.md): this is concurrent-thread contention within a single
 * application instance, not literal multi-process/multi-instance contention -- see
 * docs/EVALUATION_CLOSURE_MATRIX.md item 12 for why that distinction is called out explicitly.
 */
class ConcurrentAppendTest extends AbstractApiIntegrationTest {

    @Test
    void concurrentAppendsDoNotBreakTheChain() throws Exception {
        int writerCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(writerCount);
        CountDownLatch ready = new CountDownLatch(writerCount);
        CountDownLatch start = new CountDownLatch(1);

        List<Callable<org.springframework.http.ResponseEntity<com.auditlog.dto.AuditEventResponse>>> tasks = IntStream.range(0, writerCount)
                .<Callable<org.springframework.http.ResponseEntity<com.auditlog.dto.AuditEventResponse>>>mapToObj(i -> () -> {
                    ready.countDown();
                    start.await();
                    Map<String, Object> request = Map.of(
                            "eventType", "USER_LOGIN",
                            "actorId", "user-" + i,
                            "resourceType", "ACCOUNT",
                            "resourceId", "acct-" + i,
                            "payload", Map.of(),
                            "timestamp", OffsetDateTime.now().toString());
                    return restTemplate.postForEntity(baseUrl("/audit/events"), request, com.auditlog.dto.AuditEventResponse.class);
                })
                .collect(Collectors.toList());

        List<Future<org.springframework.http.ResponseEntity<com.auditlog.dto.AuditEventResponse>>> futures =
                tasks.stream().map(pool::submit).collect(Collectors.toList());
        ready.await();
        start.countDown();

        int successCount = 0;
        for (Future<org.springframework.http.ResponseEntity<com.auditlog.dto.AuditEventResponse>> future : futures) {
            org.springframework.http.ResponseEntity<com.auditlog.dto.AuditEventResponse> response = future.get(30, TimeUnit.SECONDS);
            if (response.getStatusCode().is2xxSuccessful()) {
                successCount++;
            } else {
                System.out.println("Concurrent write failed with status " + response.getStatusCode());
            }
        }
        pool.shutdown();

        VerifyResponse verify = restTemplate.getForObject(baseUrl("/audit/verify"), VerifyResponse.class);
        System.out.println("successCount=" + successCount + " verify=" + verify);

        assertThat(successCount).isEqualTo(writerCount);
        assertThat(verify.chainIntact())
                .as("verify=%s", verify)
                .isTrue();
        assertThat(verify.recordsChecked()).isEqualTo((long) writerCount);
    }
}
