package io.github.kpagratis.database.managed.deps

import io.github.kpagratis.database.managed.client.DatabaseClient
import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, SupportedInstanceType, User}

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Properties
import java.util.concurrent.{Callable, ConcurrentHashMap}
import scala.concurrent.duration.DurationDouble
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

class JdbcMySQLClient(
                       dockerInstancePort: Int,
                       instanceDefinition: InstanceDefinition,
                       databaseDefinition: DatabaseDefinition
                     ) extends DatabaseClient[Connection](dockerInstancePort, instanceDefinition, databaseDefinition) {

  private val baseUrl = s"jdbc:mysql://localhost:$dockerInstancePort"
  private val databaseUrl = s"$baseUrl/${databaseDefinition.databaseName}"
  private val driver = DriverManager.getDriver(baseUrl)
  private val closeables: ConcurrentHashMap[String, Connection] = new ConcurrentHashMap[String, Connection]()
  sys.addShutdownHook {
    closeables.values().asScala.foreach(c => Try(c.close()))
  }

  override def databaseInstanceConnectionCheck(): Unit = {
    /**
     * Not adding this client to the client map above because this initial client is used to create the database schema
     */
    val props = new Properties()
    props.put("user", "root")
    instanceDefinition.rootPassword.foreach(p => props.put("password", p))
    val rootClient = retry(100.milliseconds, 30.seconds)(() => driver.connect(baseUrl, props))
    Try(rootClient.createStatement().execute(instanceDefinition.instanceType.createDatabaseQuery(databaseDefinition.databaseName))) match {
      case _ => rootClient.close()
    }
  }

  override def init(): Unit = {
    val connection = getRootClient()
    instanceDefinition.instanceType
      .createUserDDL(databaseDefinition.users)
      .foreach(connection.createStatement().execute)
    instanceDefinition.instanceType
      .grantUserPermissionDDL(databaseDefinition.users, databaseDefinition.databaseName)
      .foreach(connection.createStatement().execute)

    databaseDefinition.databaseDDL.foreach(connection.createStatement().execute)
  }

  override def truncateTables(preserveTables: Seq[String]): Unit = {
    val connection = getRootClient()
    val resultSet: ResultSet = connection
      .createStatement()
      .executeQuery(instanceDefinition.instanceType.getAllTablesQuery(databaseDefinition.databaseName))
    new Iterator[String] {
      override def hasNext: Boolean = resultSet.next()

      override def next(): String = resultSet.getString("table_name")
    }
      .toSeq
      .map(instanceDefinition.instanceType.truncateTableQuery)
      .filterNot(preserveTables.contains)
      .foreach(connection.createStatement().execute)
  }

  def getRootClient(): Connection = {
    closeables.computeIfAbsent("root", _ => {
      val props = new Properties()
      props.put("user", "root")
      instanceDefinition.rootPassword.foreach(p => props.put("password", p))
      retry(10.milliseconds, 30.seconds)(clientRunnable(props))
    })
  }

  def getClient(user: User): Connection = {
    closeables.computeIfAbsent(user.name, _ => {
      val props = new Properties()
      props.put("user", user.name)
      props.put("password", user.password)
      retry(10.milliseconds, 30.seconds)(clientRunnable(props))
    })
  }

  private def clientRunnable(properties: Properties): Callable[Connection] = () => {
    val connection = driver.connect(databaseUrl, properties)
    connection.setNetworkTimeout(scheduler, 30.seconds.toMillis.toInt)
    connection
  }
}
