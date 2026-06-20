package com.odin.catalog.inventory.application.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Retries an operation that may fail with {@link OptimisticLockingFailureException} when two
 * concurrent writers (e.g. multiple replicas) race on a {@code @Version}-locked entity.
 *
 * <p>Invoke this at a proxy boundary so each attempt runs in its own transaction — i.e. the
 * supplied action should call a {@code @Transactional} bean method (as the controller does for the
 * dataset ownership-transfer flows). Calling a {@code @Transactional} method on {@code this} from
 * within the same bean would reuse the now rollback-only transaction and defeat the retry.
 */
@Component
public class OptimisticRetry {

    private static final Logger log = LoggerFactory.getLogger(OptimisticRetry.class);
    private static final int MAX_ATTEMPTS = 3;

    public <T> T execute(Supplier<T> action) {
        OptimisticLockingFailureException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (OptimisticLockingFailureException e) {
                last = e;
                log.warn("action=OPTIMISTIC_RETRY attempt={} max={} reason={}",
                    attempt, MAX_ATTEMPTS, e.getMessage());
            }
        }
        throw last;
    }
}
