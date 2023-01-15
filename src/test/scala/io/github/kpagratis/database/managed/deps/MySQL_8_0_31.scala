package io.github.kpagratis.database.managed.deps

import io.github.kpagratis.database.managed.config.{SupportedInstanceType, User}

case object MySQL_8_0_31 extends SupportedInstanceType {
  override val dockerImage: String = "mysql:8.0.31"

  override def createUserDDL(users: Seq[User]): Seq[String] =
    users.map { user =>
      s"CREATE USER '${user.name}'@'%' IDENTIFIED BY '${user.password}';"
    }

  override def grantUserPermissionDDL(users: Seq[User], databaseName: String): Seq[String] =
    users.map { user =>
      s"""GRANT ${user.privilege} ON $databaseName.* TO '${user.name}'@'%';"""
    }

  override def getAllTablesQuery(databaseName: String): String =
    s"""SELECT table_name FROM information_schema.tables
       |WHERE table_type = 'BASE TABLE' AND table_schema='$databaseName';""".stripMargin

  override def truncateTableQuery(tableName: String): String = s"""TRUNCATE TABLE $tableName"""

  override def createDatabaseQuery(databaseName: String): String = s"create database $databaseName"
}