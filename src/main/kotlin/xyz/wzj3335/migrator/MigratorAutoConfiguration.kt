package xyz.wzj3335.migrator

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate
import org.springframework.boot.autoconfigure.data.r2dbc.R2dbcDataAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate
import org.springframework.transaction.reactive.TransactionalOperator

@AutoConfiguration(after = [R2dbcDataAutoConfiguration::class])
@ConditionalOnClass(R2dbcEntityTemplate::class)
@ConditionalOnProperty("migrator.enabled", havingValue = "true")
@EnableConfigurationProperties(MigratorProperties::class)
class MigratorAutoConfiguration {
    @Bean
    @ConditionalOnSingleCandidate(R2dbcEntityTemplate::class)
    fun migrationOnStartupRunner(
        entityTemplate: R2dbcEntityTemplate,
        transactionalOperator: TransactionalOperator?,
        properties: MigratorProperties
    ) =
        MigrationOnStartupRunner(entityTemplate, transactionalOperator, properties)
}