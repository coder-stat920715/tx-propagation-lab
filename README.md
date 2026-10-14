# tx-propagation-lab

Spring Boot 3 / JPA / H2 demo of nested `@Transactional` behavior.

Run: `mvn clean test`  (Java 17+)

| Scenario | Service | Test |
|---|---|---|
| 1. REQUIRED shared tx + rollback-only trap | OrderService -> InventoryService | testRequired_WithExceptionHandled_ThrowsUnexpectedRollbackException |
| 2. REQUIRES_NEW survives parent rollback | OrderService -> AuditLogService | testRequiresNew_CommitsIndependently_WhenParentRollsBack |
| 3. Self-invocation bypass + fixes | PaymentService (+ DiscountService) | testSelfInvocation_BypassesProxy |

Evidence technique: `TxProbe` logs `isActualTransactionActive()` and
`getCurrentTransactionName()`. Same name in inner and outer = same physical tx.
