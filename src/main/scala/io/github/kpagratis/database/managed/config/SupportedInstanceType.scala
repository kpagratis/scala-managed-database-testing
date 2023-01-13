package io.github.kpagratis.database.managed.config

trait SupportedInstanceType {
  def dockerImage(): String

  def createUserDDL(users: Seq[User]): Seq[String]

  def grantUserPermissionDDL(users: Seq[User], databaseName: String): Seq[String]

  def getAllTablesQuery(databaseName: String): String

  def truncateTableQuery(tableName: String): String

  def createDatabaseQuery(databaseName: String): String
}
