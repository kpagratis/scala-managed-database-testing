package io.github.kpagratis.database.managed.deps

import io.github.kpagratis.database.managed.config.{SupportedInstanceType, User}

case object Postgres_15_1 extends SupportedInstanceType {
  override val dockerImage: String = "postgres:15.1"
  override val defaultPort: Int = 5432

  override def createUserDDL(users: Seq[User]): Seq[String] =
    users.map { user =>
      s"CREATE USER ${user.name} WITH PASSWORD '${user.password}';"
    }

  override def grantUserPermissionDDL(users: Seq[User], databaseName: String): Seq[String] =
    users.map { user =>
      s"""GRANT ${user.privilege} ON ALL TABLES IN SCHEMA public TO ${user.name}"""
    }

  override def getAllTablesQuery(databaseName: String): String =
    s"""
      |SELECT table_name FROM information_schema.tables
      |WHERE table_type = 'BASE TABLE' and table_catalog = '$databaseName' AND table_schema = 'public'
      |""".stripMargin

  override def truncateTableQuery(tableName: String): String = s"""TRUNCATE TABLE $tableName"""

  override def createDatabaseQuery(databaseName: String): String = s"CREATE DATABASE $databaseName"

  override def prepareTruncationSql: Seq[String] =
    Seq(
      "SET session_replication_role = 'replica';"
    )

  override def cleanupTruncationSql: Seq[String] =
    Seq(
      "SET session_replication_role = 'origin';"
    )
}