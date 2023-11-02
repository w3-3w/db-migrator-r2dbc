CREATE TABLE IF NOT EXISTS `migration_history` (
  `migration_version` int NOT NULL,
  `filename` varchar(100) NOT NULL,
  `checksum` bigint DEFAULT NULL,
  `succeeded` bit(1) NOT NULL DEFAULT b'0',
  `created_at` timestamp NOT NULL,
  PRIMARY KEY (`migration_version`)
);