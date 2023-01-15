package io.github.kpagratis.database.managed

import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, User}
import io.github.kpagratis.database.managed.deps.{JdbcMySQLClient, MySQL_8_0_31, Test}

import java.sql.Connection

class ManagedDatabaseFeatureTest extends Test with ManagedDatabaseForTest[Connection, JdbcMySQLClient] {
  override val instanceDefinition: InstanceDefinition = InstanceDefinition
    .Builder(MySQL_8_0_31)
    .withEnvironmentVariables(Seq("MYSQL_ROOT_PASSWORD=someSecret"))
    .withRootPassword("someSecret")
    .withArguments(Seq("--character_set_server=utf8", "--collation_server=utf8_general_ci"))
    .build
  override val databaseDefinition: DatabaseDefinition = DatabaseDefinition
    .Builder("testDB")
    .withUsers(Seq(
      User("rwUser", "rwUser", "ALL PRIVILEGES"),
      User("roUser", "roUser", "SELECT"),
    ))
    .withDatabaseDDL(Seq(
      "create table testing(id int, name varchar(64))",
      """insert into testing values(1, "Bob Smith")"""
    ))
    .build

  override val managedDatabase: ManagedDatabase[Connection, JdbcMySQLClient] =
    new ManagedDatabase[Connection, JdbcMySQLClient](
      instanceDefinition,
      databaseDefinition,
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    managedDatabase.truncateTables()
  }

  test("users are configured correctly") {
    val roClient = managedDatabase.getClient("roUser")
    val rwClient = managedDatabase.getClient("rwUser")
    roClient.createStatement().execute("select 1 from dual")

    intercept[Exception] {
      roClient.createStatement().execute("""insert into testing values(2, "Bob Smith Jr")""")
    }
    rwClient.createStatement().execute("""insert into testing values(2, "Bob Smith Jr")""")

    1 mustBe 1
  }

  test("table should be empty because of truncation") {
    val roClient = managedDatabase.getClient("roUser")
    val result = roClient.createStatement().executeQuery("""SELECT COUNT(*) FROM testing""")
    result.next() mustBe true
    result.getInt(1) mustBe 0

  }
}