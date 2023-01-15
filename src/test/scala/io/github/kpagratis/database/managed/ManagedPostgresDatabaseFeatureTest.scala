package io.github.kpagratis.database.managed

import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, SuperUser, User}
import io.github.kpagratis.database.managed.deps.{JdbcPostgresClient, Postgres_15_1, Test}

import java.sql.Connection

class ManagedPostgresDatabaseFeatureTest extends Test with ManagedDatabaseForTest[Connection, JdbcPostgresClient] {
  override val instanceDefinition: InstanceDefinition = InstanceDefinition
    .Builder(Postgres_15_1)
    .withEnvironmentVariables(Seq("POSTGRES_PASSWORD=someSecret"))
    .withSuperUser(SuperUser("postgres", Some("someSecret")))
    .build
  override val databaseDefinition: DatabaseDefinition = DatabaseDefinition
    .Builder("testdb")
    .withUsers(Seq(
      User("rwuser", "rwuser", "ALL PRIVILEGES"),
      User("rouser", "rouser", "SELECT"),
    ))
    .withDatabaseDDL(Seq(
      "create table testing(id int, name varchar(64))",
      """insert into testing(id, name) values(1, 'Bob Smith')"""
    ))
    .build

  override val managedDatabase: ManagedDatabase[Connection, JdbcPostgresClient] =
    new ManagedDatabase[Connection, JdbcPostgresClient](
      instanceDefinition,
      databaseDefinition,
    )

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    managedDatabase.truncateTables()
  }

  test("users are configured correctly") {
    val roClient = managedDatabase.getClient("rouser")
    val rwClient = managedDatabase.getClient("rwuser")

    intercept[Exception] {
      roClient.createStatement().execute("""insert into testing(id, name) values(2, 'Bob Smith Jr')""")
    }
    rwClient.createStatement().execute("""insert into testing(id, name) values(2, 'Bob Smith Jr')""")

    1 mustBe 1
  }

  test("table should be empty because of truncation") {
    val roClient = managedDatabase.getClient("rouser")
    val result = roClient.createStatement().executeQuery("""SELECT COUNT(*) FROM testing""")
    result.next() mustBe true
    result.getInt(1) mustBe 0

  }
}