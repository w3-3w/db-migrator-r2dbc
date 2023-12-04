package xyz.wzj3335.migrator

import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux

internal interface MigrationHistoryRepository : ReactiveCrudRepository<MigrationHistoryEntity, Int> {
    fun findByOrderByMigrationVersionAsc(): Flux<MigrationHistoryEntity>
}