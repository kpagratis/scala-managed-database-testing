package io.github.kpagratis.database.managed

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DockerClientBuilder
import io.github.kpagratis.database.managed.ManagedDatabase.dockerClient
import io.github.kpagratis.database.managed.client.DatabaseClient
import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition, User}

import java.util.concurrent.atomic.AtomicBoolean
import scala.reflect.{ClassTag, classTag}

object ManagedDatabase{
  private val dockerClient: DockerClient = DockerClientBuilder.getInstance.build
}

final class ManagedDatabase[InnerClient, Client <: DatabaseClient[InnerClient]](
                                          instanceDefinition: InstanceDefinition,
                                          databaseDefinition: DatabaseDefinition,
                                          dockerClient: DockerClient = dockerClient
                                        )(implicit ct: ClassTag[Client]) {
  private val started = new AtomicBoolean(false)
  private val starting = new AtomicBoolean(false)

  @volatile private var database: Database = _
  @volatile private var instance: Instance = _
  @volatile private var databaseClient: Client = _

  def getRootClient(): InnerClient = {
    start()
    databaseClient.getRootClient()
  }

  def getClient(userName: String): InnerClient = {
    start()
    databaseDefinition
      .users
      .find(_.name.equalsIgnoreCase(userName))
      .map(databaseClient.getClient) match {
      case Some(client) => client
      case _ => throw new IllegalArgumentException(s"$userName is not configured for this database")
    }
  }
  def getClient(user: User): InnerClient = {
    start()
    databaseDefinition
      .users
      .find(_ == user)
      .map(databaseClient.getClient) match {
      case Some(client) => client
      case _ => throw new IllegalArgumentException(s"$user is not configured for this database")
    }
  }

  def start(): Unit = {
    started synchronized {
      if (starting.compareAndSet(false, true)) {
        instance = Instance.getInstance(dockerClient, instanceDefinition)
        databaseClient = ct.runtimeClass.getConstructor(
          classOf[Int],
          classOf[InstanceDefinition],
          classOf[DatabaseDefinition]
        ).newInstance(
          instance.dockerPort,
          instanceDefinition,
          databaseDefinition
        ).asInstanceOf[Client]
        database = Database.getDatabase(databaseDefinition, instance.dockerPort)
        databaseClient.databaseInstanceConnectionCheck()
        databaseClient.init()
        started.set(true)
      }
    }
  }

  def truncateTables(preserveTables: Seq[String] = Seq.empty[String]): Unit = {
    start()
    databaseClient.truncateTables(preserveTables)
  }
}
