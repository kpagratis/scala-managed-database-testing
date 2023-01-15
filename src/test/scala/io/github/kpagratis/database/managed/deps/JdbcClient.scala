package io.github.kpagratis.database.managed.deps

import io.github.kpagratis.database.managed.client.DatabaseClient
import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, User}

import java.sql.{Connection, DriverManager, ResultSet}
import java.util.Properties
import java.util.concurrent.{Callable, ConcurrentHashMap}
import scala.concurrent.duration.DurationDouble
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

abstract class JdbcClient(
                       dockerInstancePort: Int,
                       instanceDefinition: InstanceDefinition,
                       databaseDefinition: DatabaseDefinition
                     ) extends DatabaseClient[Connection](dockerInstancePort, instanceDefinition, databaseDefinition) {

  protected def baseUrl: String
  protected def databaseUrl: String

  private lazy val driver = DriverManager.getDriver(baseUrl)
  private val closeables: ConcurrentHashMap[String, Connection] = new ConcurrentHashMap[String, Connection]()
  sys.addShutdownHook {
    closeables.values().asScala.foreach(c => Try(c.close()))
  }

  override def databaseInstanceConnectionCheck(): Unit = {
    connectionToInstanceWithoutDatabase()
  }

  private def connectionToInstanceWithoutDatabase(): Connection = {
    /**
     * Not adding this client to the client map above because this initial client is used to create the database schema
     */
    val props = new Properties()
    props.put("user", instanceDefinition.superUser.name)
    instanceDefinition.superUser.password.foreach(p => props.put("password", p))
    retry(100.milliseconds, 30.seconds)(() => driver.connect(baseUrl, props))
  }

  override def initDatabase(): Unit = {
    val createDbConnection = connectionToInstanceWithoutDatabase()
    val createDbSql = instanceDefinition.instanceType.createDatabaseQuery(databaseDefinition.databaseName)
    Try(createDbConnection.prepareStatement(createDbSql).execute()) match {
      case _ => createDbConnection.close()
    }

    val connection = getSuperUserClient()
    databaseDefinition.databaseDDL.foreach(connection.prepareStatement(_).execute())
    instanceDefinition.instanceType
      .createUserDDL(databaseDefinition.users)
      .foreach(connection.prepareStatement(_).execute())
    instanceDefinition.instanceType
      .grantUserPermissionDDL(databaseDefinition.users, databaseDefinition.databaseName)
      .foreach(connection.prepareStatement(_).execute())

  }

  override def truncateTables(preserveTables: Seq[String]): Unit = {
    val connection = getSuperUserClient()
    connection.setAutoCommit(false)
    Try{
      instanceDefinition.instanceType.prepareTruncationSql.foreach(connection.prepareStatement(_).execute())
      val resultSet: ResultSet = connection
        .createStatement()
        .executeQuery(instanceDefinition.instanceType.getAllTablesQuery(databaseDefinition.databaseName))
      new Iterator[String] {
        override def hasNext: Boolean = resultSet.next()

        override def next(): String = resultSet.getString(1)
      }
        .toSeq
        .map(instanceDefinition.instanceType.truncateTableQuery)
        .filterNot(preserveTables.contains)
        .foreach(connection.prepareStatement(_).execute())
      connection.commit()
    }.map{_ =>
      instanceDefinition.instanceType.cleanupTruncationSql.foreach(connection.prepareStatement(_).execute())
    }.get
  }

  def getSuperUserClient(): Connection = {
    closeables.computeIfAbsent(instanceDefinition.superUser.name, _ => {
      val props = new Properties()
      props.put("user", instanceDefinition.superUser.name)
      instanceDefinition.superUser.password.foreach(p => props.put("password", p))
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
