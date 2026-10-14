package com.example.tx.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records and logs the transactional context observed at each step.
 *
 * getCurrentTransactionName() returns the name of the transaction that is physically
 * running, e.g. "com...PaymentService.processPayment". If an inner method reports the
 * OUTER method's name, it did NOT get its own transaction (joined, or proxy bypassed).
 */
@Component
public class TxProbe {

    private static final Logger log = LoggerFactory.getLogger(TxProbe.class);

    public record Observation(String step, boolean transactionActive, String transactionName) { }

    private final List<Observation> observations = new CopyOnWriteArrayList<>();

    public void record(String step) {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        String name = TransactionSynchronizationManager.getCurrentTransactionName();
        observations.add(new Observation(step, active, name));
        log.info("[TX] step={} | actualTxActive={} | txName={}", step, active, name);
    }

    public Optional<Observation> find(String step) {
        return observations.stream().filter(o -> o.step().equals(step)).findFirst();
    }

    public List<Observation> all() {
        return List.copyOf(observations);
    }

    public void clear() {
        observations.clear();
    }
}
