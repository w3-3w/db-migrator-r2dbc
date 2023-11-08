package xyz.wzj3335.migrator

import org.apache.commons.logging.LogFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory
import org.springframework.transaction.reactive.TransactionalOperator
import java.util.zip.CRC32

class MigrationOnStartupRunner(
    entityTemplate: R2dbcEntityTemplate,
    private val transactionalOperator: TransactionalOperator?,
    private val properties: MigratorProperties,
) : ApplicationListener<ApplicationStartedEvent> {
    private val log = LogFactory.getLog(javaClass)
    companion object {
        private fun ByteArray.calcSum(): Long =
            CRC32().also {
                it.update(this)
            }.value
    }

    private val databaseClient = entityTemplate.databaseClient
    private val migrationHistoryRepository = R2dbcRepositoryFactory(entityTemplate).getRepository(MigrationHistoryRepository::class.java)

    override fun onApplicationEvent(event: ApplicationStartedEvent) {
        if (!properties.enabled) return
        if (properties.initTable) {
            databaseClient.sql(
                event.applicationContext
                    .getResource("classpath:${this::class.java.packageName.replace('.', '/')}/migration_history_mysql.sql")
                    .getContentAsString(Charsets.UTF_8)
            ).fetch().rowsUpdated().block()
        }
        val existingMigrations = migrationHistoryRepository.findByOrderByMigrationVersionAsc()

        val fileMigrations = event.applicationContext.getResources("${properties.location}/*.sql")
            .filter { it.filename?.matches(Regex("""v\d+.*\.sql""")) ?: false }
            .groupBy { Regex("\\d+").find(it.filename!!)?.value?.toInt()!! }
            .mapValues { (version, resources) ->
                if (resources.size > 1) error("Multiple files with same version number found: version=$version")
                resources.first()
            }
            .toSortedMap(Int::compareTo)

        val existingLastVersion = existingMigrations.zipWithIterable(fileMigrations.entries) { record, (fileVersion, resource) ->
            if (record.migrationVersion != fileVersion) {
                error("Migration version check failure: version in DB=${record.migrationVersion}, version of file=$fileVersion")
            }
            if (record.filename != resource.filename) {
                error("Migration filename check failure: version=${record.migrationVersion}, filename in DB=${record.filename}, filename=${resource.filename}")
            }
            if (!record.succeeded) {
                error("Migration history record is left as not succeeded. Fix DB manually and update the record as succeeded. version=${record.migrationVersion}")
            }
            val fileChecksum = resource.contentAsByteArray.calcSum()
            if (record.checksum == null) {
                log.warn("Migration checksum in DB is null: version=${record.migrationVersion}, file checksum=$fileChecksum")
            } else if (record.checksum != fileChecksum) {
                error("Migration checksum check failure: version=${record.migrationVersion}")
            }
            record.migrationVersion
        }.blockLast() ?: 0

        log.info("Start DB migration process. Current version=$existingLastVersion")
        val migratedVersionCount = fileMigrations.tailMap(existingLastVersion + 1)
            .onEach { (version, resource) ->
                val filename = resource.filename!!
                val fileContent = resource.contentAsByteArray
                log.info("Run migration: version=$version, filename=$filename")
                try {
                    val execution = databaseClient.sql(fileContent.toString(Charsets.UTF_8)).fetch().rowsUpdated()
                    if (properties.executeInTransaction) {
                        transactionalOperator!!.transactional(execution).block()
                    } else {
                        execution.block()
                    }
                } catch (e: Exception) {
                    migrationHistoryRepository.save(
                        MigrationHistoryEntity(
                            migrationVersion = version,
                            filename = filename,
                            checksum = null,
                            succeeded = false,
                        ).switchToInsert()
                    ).block()
                    throw e
                }
                migrationHistoryRepository.save(
                    MigrationHistoryEntity(
                        migrationVersion = version,
                        filename = filename,
                        checksum = fileContent.calcSum(),
                        succeeded = true,
                    ).switchToInsert()
                ).block()
            }
            .size
        log.info("Finished DB migration process. $migratedVersionCount files executed.")
    }
}