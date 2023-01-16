package io.github.kpagratis.database.managed

import com.twitter.conversions.DurationOps.richDurationFromInt
import com.twitter.finagle.mysql.{Client, IntValue, LongValue, ResultSet, Row, StringValue, Transactions}
import com.twitter.util.Await
import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, SuperUser, User}
import io.github.kpagratis.database.managed.deps.{FinagleMysqlClient, MySQL_8_0_31, Test}

class ManagedMySQLDatabaseFinagleFeatureTest extends Test with ManagedDatabaseForTest[Client with Transactions, FinagleMysqlClient] {
  val ClientTimeout = 5.seconds
  override val instanceDefinition: InstanceDefinition = InstanceDefinition
    .Builder(MySQL_8_0_31)
    .withEnvironmentVariables(Seq("MYSQL_ROOT_PASSWORD=someSecret"))
    .withSuperUser(SuperUser("root", Some("someSecret")))
    .withArguments(Seq("--character_set_server=utf8", "--collation_server=utf8_general_ci", "--default-authentication-plugin=mysql_native_password"))
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

  override val managedDatabase: ManagedDatabase[Client with Transactions, FinagleMysqlClient] =
    new ManagedDatabase[Client with Transactions, FinagleMysqlClient](
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
    Await.result(roClient.query("select 1 from dual"), ClientTimeout)

    intercept[Exception] {
      Await.result(roClient.query("""insert into testing values(2, "Bob Smith Jr")"""), ClientTimeout)
    }
    Await.result(rwClient.query("""insert into testing values(2, "Bob Smith Jr")"""), ClientTimeout)

    1 mustBe 1
  }

  test("table should be empty because of truncation") {
    val roClient = managedDatabase.getClient("roUser")
    val result = Await.result(roClient.query("""SELECT COUNT(*) FROM testing"""), ClientTimeout)

    result match {
      case ResultSet(_, rows: Seq[Row]) =>
        rows.flatMap(_.values).foreach{
          case LongValue(l) => l mustBe 0
          case _ => fail("could not get count")
        }
      case _ => fail("unknown error")
    }
  }
}