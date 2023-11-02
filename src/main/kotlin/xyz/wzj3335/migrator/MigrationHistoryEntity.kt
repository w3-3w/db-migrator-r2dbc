package xyz.wzj3335.migrator

import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.Immutable
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("migration_history")
@Immutable
data class MigrationHistoryEntity(
    @Id
    val migrationVersion: Int,
    val filename: String,
    val checksum: Long?,
    val succeeded: Boolean,
    @CreatedDate
    val createdAt: Instant = Instant.EPOCH,
) : Persistable<Int> {
    @Transient
    private var shouldInsertOnSave: Boolean = false
    override fun getId(): Int = migrationVersion
    override fun isNew(): Boolean = shouldInsertOnSave

    fun switchToInsert(): MigrationHistoryEntity = apply {
        shouldInsertOnSave = true
    }
}
