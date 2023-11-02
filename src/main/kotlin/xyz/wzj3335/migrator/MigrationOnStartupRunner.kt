package xyz.wzj3335.migrator

import org.apache.commons.logging.LogFactory
import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.ApplicationListener
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory
import java.util.zip.CRC32

class MigrationOnStartupRunner(
    entityTemplate: R2dbcEntityTemplate,
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
        databaseClient.sql(
            event.applicationContext
                .getResource("classpath:${this::class.java.packageName.replace('.', '/')}/migration_history.sql")
                .getContentAsString(Charsets.UTF_8)
        ).fetch().rowsUpdated().block()
        val existingMigrations = migrationHistoryRepository.findByOrderByMigrationVersionAsc()

        val fileMigrations = event.applicationContext.getResources("${properties.location}/*.sql")
            .filter { it.filename?.matches(Regex("""v\d+.*\.sql""")) ?: false }
            .groupBy { Regex("\\d+").find(it.filename!!)?.value?.toInt()!! }
            .mapValues { (version, resources) ->
                if (resources.size > 1) error("マイグレーションバージョンが重複しています。 version=$version")
                resources.first()
            }
            .toSortedMap { o1, o2 -> o1.compareTo(o2) }

        val existingLastVersion = existingMigrations.zipWithIterable(fileMigrations.entries) { record, (fileVersion, resource) ->
            if (record.migrationVersion != fileVersion) {
                error("マイグレーションバージョンチェック失敗。失敗箇所：DBバージョン=${record.migrationVersion}, ファイルバージョン=$fileVersion")
            }
            if (record.filename != resource.filename) {
                error("マイグレーションファイル名チェック失敗。失敗箇所：バージョン=${record.migrationVersion}, DBファイル名=${record.filename}, ファイルファイル名=${resource.filename}")
            }
            if (!record.succeeded) {
                error("マイグレーションレコードが失敗のままになっています。手動でDB状態を修正し、レコードの状態を成功に変えてください。失敗箇所：バージョン=${record.migrationVersion}")
            }
            val fileChecksum = resource.contentAsByteArray.calcSum()
            if (record.checksum == null) {
                log.info("マイグレーションchecksum: version=${record.migrationVersion}, checksum=$fileChecksum")
            } else if (record.checksum != fileChecksum) {
                error("マイグレーションファイル中身チェック失敗。失敗箇所：バージョン=${record.migrationVersion}")
            }
            record.migrationVersion
        }.blockLast() ?: 0

        log.info("DBマイグレーションを開始します。実行前のマイグレーションバージョン: $existingLastVersion")
        val migratedVersionCount = fileMigrations.tailMap(existingLastVersion + 1)
            .onEach { (version, resource) ->
                val filename = resource.filename!!
                val fileContent = resource.contentAsByteArray
                log.info("マイグレーション実行: version=$version, filename=$filename")
                try {
                    databaseClient.sql(fileContent.toString(Charsets.UTF_8)).fetch().rowsUpdated().block()
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
        log.info("DBマイグレーションが完了しました。マイグレーション実行数: $migratedVersionCount")
    }
}