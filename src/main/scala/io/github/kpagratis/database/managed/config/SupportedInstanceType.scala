package io.github.kpagratis.database.managed.config

trait SupportedInstanceType {

  /**
    * @return default port for this database type. eg: 3306
   */
  def defaultPort(): Int

  /**
   * Docker image name. This will be used as the IMAGE parameter for `docker run`
   * @return image name. eg: mysql:8.0.31
   */
  def dockerImage(): String

  /**
   * @param users Users to create
   * @return A sequence of SQL statements to create a user in the database
   */
  def createUserDDL(users: Seq[User]): Seq[String]

  /**
   * @param users Users to grant permissions to
   * @param databaseName Database name to give permission to
   * @return A sequence of SQL statements to configure database permissions
   */
  def grantUserPermissionDDL(users: Seq[User], databaseName: String): Seq[String]

  /**
   * @param databaseName Name of the database to query for
   * @return A SQL query that returns the names of all the tables in {{{databaseName}}}.
   *         The results of this query are intended to be used for table truncation between
   *         test cases.
   */
  def getAllTablesQuery(databaseName: String): String

  /**
   * @param tableName Table to truncate
   * @return A SQL query that truncates the provided table name
   */
  def truncateTableQuery(tableName: String): String

  /**
   * SQL queries to run prior to truncating tables. This could be a query to disable foreign key checks
   * @return SQL queries
   */
  def prepareTruncationSql: Seq[String]

  /**
   * SQL queries to run after truncating tables. This could be a query to re-enable foreign key checks
   *
   * @return SQL queries
   */
  def cleanupTruncationSql: Seq[String]

  /**
   * @param databaseName Database name to create
   * @return A SQL query that creates a database named {{{databaseName}}}
   */
  def createDatabaseQuery(databaseName: String): String
}
