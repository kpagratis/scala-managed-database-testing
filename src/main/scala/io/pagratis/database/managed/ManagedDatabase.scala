package io.pagratis.database.managed

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DockerClientBuilder
import io.pagratis.database.managed.ManagedDatabase.dockerClient
import io.pagratis.database.managed.client.DatabaseClient
import io.pagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition}

import java.util.concurrent.atomic.AtomicBoolean

object ManagedDatabase{
  private val dockerClient: DockerClient = DockerClientBuilder.getInstance.build
}

final class ManagedDatabase[InnerClient](
                                          instanceDefinition: InstanceDefinition,
                                          databaseDefinition: DatabaseDefinition,
                                          clientBuilder: Int => DatabaseClient[InnerClient],
                                          dockerClient: DockerClient = dockerClient
                                        ) {
  private val started = new AtomicBoolean(false)
  private val starting = new AtomicBoolean(false)

  @volatile private var database: Database = _
  @volatile private var instance: Instance = _
  @volatile private var databaseClient: DatabaseClient[InnerClient] = _

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

  def start(): Unit = {
    started synchronized {
      if (starting.compareAndSet(false, true)) {
        instance = Instance.getInstance(dockerClient, instanceDefinition)
        databaseClient = clientBuilder(instance.dockerPort)
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
