package io.github.kpagratis.database.managed.deps

import com.twitter.conversions.DurationOps.richDurationFromInt
import com.twitter.finagle.Mysql
import com.twitter.finagle.mysql._
import com.twitter.util.{Await, Future, Return, Throw}
import io.github.kpagratis.database.managed.client.DatabaseClient
import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, User}

import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

class FinagleMysqlClient(
                          dockerInstancePort: Int,
                          instanceDefinition: InstanceDefinition,
                          databaseDefinition: DatabaseDefinition
                        ) extends DatabaseClient[Client with Transactions](dockerInstancePort, instanceDefinition, databaseDefinition) {

  private val dest = s"${InetAddress.getLoopbackAddress.getHostAddress}:$dockerInstancePort"
  private val baseClient: Mysql.Client = Mysql.client
  private val ClientTimeout = 30.seconds
  private val closeables: ConcurrentHashMap[String, Client with Transactions] = new ConcurrentHashMap[String, Client with Transactions]()
  sys.addShutdownHook {
    closeables.values().asScala.foreach(c => Try(c.close()))
  }

  private def rootClientWithoutDatabase(): Client with Transactions = {
    Mysql
      .client.withSessionQualifier
      .noFailureAccrual.withSessionQualifier.noFailFast
      .withCredentials(
        instanceDefinition.superUser.name,
        instanceDefinition.superUser.password.orNull)
      .newRichClient(dest)
  }

  override def databaseInstanceConnectionCheck(): Unit = {
    val rootClient = rootClientWithoutDatabase()
    val wait = waitForPing(rootClient)
      .before(rootClient.close(ClientTimeout))
    Await.result(wait, ClientTimeout)
  }

  private def waitForPing(client: Client): Future[Unit] = {
    client.ping().transform {
      case Throw(_) =>
        waitForPing(client)
      case Return(_) =>
        Future.Done
    }
  }

  private def getTablesToTruncate(preserveTables: Seq[String]): Seq[String] = {
    val rootClient = getSuperUserClient()
    val tablesFuture = rootClient
      .query(instanceDefinition.instanceType.getAllTablesQuery(databaseDefinition.databaseName))
      .map {
        case ResultSet(_, rows) =>
          rows.flatMap(_.values).flatMap {
            case StringValue(s) => Some(s)
            case _ => None
          }
        case unknown =>
          throw new RuntimeException(s"Unexpected result: $unknown")
      }
      .map(_.filterNot(preserveTables.contains))
    Await.result(tablesFuture, ClientTimeout)
  }

  override def truncateTables(preserveTables: Seq[String]): Unit = {
    val rootClient = getSuperUserClient()
    val truncateTableDDL: Seq[String] = getTablesToTruncate(preserveTables).map(instanceDefinition.instanceType.truncateTableQuery)
    val truncateFuture = rootClient.session{client =>
      Future
        .collect(instanceDefinition.instanceType.prepareTruncationSql.map(client.query))
        .flatMap(_ => Future.collect(truncateTableDDL.map(client.query)))
        .ensure(Future.collect(instanceDefinition.instanceType.cleanupTruncationSql.map(client.query)))
    }
     Await.result(truncateFuture, ClientTimeout)
  }

  override def initDatabase(): Unit = {
    val rootClient = rootClientWithoutDatabase()
    val createDbSql = instanceDefinition.instanceType.createDatabaseQuery(databaseDefinition.databaseName)
    val dbFuture: Future[Unit] = rootClient.query(createDbSql).unit
    Await.result(dbFuture.before(rootClient.close(ClientTimeout)))

    val databaseRootClient = getSuperUserClient()
    val setupFuture: Future[Unit] = for {
      _ <- Future.collect(databaseDefinition.databaseDDL.map(databaseRootClient.query))
      userDDL = instanceDefinition.instanceType.createUserDDL(databaseDefinition.users)
      _ <- Future.collect(userDDL.map(databaseRootClient.query))
      grantDDL = instanceDefinition.instanceType.grantUserPermissionDDL(databaseDefinition.users, databaseDefinition.databaseName)
      _ <- Future.collect(grantDDL.map(databaseRootClient.query))
    } yield ()
    Await.result(setupFuture, ClientTimeout)
  }

  override def getSuperUserClient(): Client with Transactions = {
    closeables.computeIfAbsent(instanceDefinition.superUser.name, _ => {
      baseClient
        .withDatabase(databaseDefinition.databaseName)
        .withCredentials(
          instanceDefinition.superUser.name,
          instanceDefinition.superUser.password.orNull)
        .newRichClient(dest)
    })
  }

  override def getClient(user: User): Client with Transactions = {
    closeables.computeIfAbsent(user.name, _ => {
      baseClient
        .withDatabase(databaseDefinition.databaseName)
        .withCredentials(
          user.name,
          user.password)
        .newRichClient(dest)
    })
  }
}
