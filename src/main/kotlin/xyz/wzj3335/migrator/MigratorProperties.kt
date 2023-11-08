package xyz.wzj3335.migrator

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties("migrator")
data class MigratorProperties
@ConstructorBinding
constructor(
    val enabled: Boolean,
    val initTable: Boolean = true,
    val location: String = "classpath:migrations",
    val executeInTransaction: Boolean = true,
)
