# db-migrator-r2dbc
A lightweight DB Migrator for Spring Boot which runs migration SQLs on application startup.

Migrations run before `ApplicationRunner`s run. For web applications, also before server ready to accept requests.

Although R2DBC is known for its non blocking nature, migrations are run in blocking way by calling `Publisher.block()`. This project aims to make it possible for reactive applications to run blocking migrations without having to introduce JDBC related components, which should be avoided in business logic.

This should work with most reactive Spring Boot applications, which use Spring Data R2DBC as the database access layer, and MySQL as database.

## build
Requires JDK 17, Spring Boot 3.x or above.
Tested with Spring Boot 3.1.5 and MySQL 8.0.
```
./gradlew clean build
```

## usage
Build, and add the following dependency to your Spring Boot project:
```
dependencies {
    runtimeOnly(files("jar file path which is built by the build command above"))
}
```
If it fails to autoconfigure, you may switch to `implementation`, and register
`xyz.wzj3335.migrator.MigrationOnStartupRunner` as a bean manually.

The constructor of `MigrationOnStartupRunner` takes 2 parameters: `R2dbcEntityTemplate` and
`MigratorProperties`.

### properties
All property names have a prefix of `migrator`.

| name       | type    | description                        | default value          |
|------------|---------|------------------------------------|------------------------|
| `enabled`  | Boolean | whether to enable the migrator     | `false`                |
| `location` | String  | the location of the migration SQLs | `classpath:migrations` |

### migration SQLs
Migration SQLs should be named as `v{version}{optionalName}.sql`, e.g. `v1_init.sql`.
`version` is a positive integer, and `optionalName` is optional string.

### migration process
The migrator acts like simplified Flyway.

1. create migrator managed `migration_history` table if it doesn't exist.
2. pick up all migration SQLs from the configured `location` and sort with their `version`.
3. validate the migration SQLs which already exist in `migration_history` table.
4. run the migration SQLs which are not in `migration_history` table in order.
   - If migration fails, the corresponding migration history record will be saved as succeeded = 0, and the
   whole application will halt immediately. Please fix the DB manually, set succeeded = 1 for failed version,
   and restart the application.

## todos

1. log in English.
2. run migrations in transactions.
