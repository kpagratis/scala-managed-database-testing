package io.pagratis.database.managed

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command.CreateContainerResponse
import com.github.dockerjava.api.model.{HostConfig, PortBinding}
import io.pagratis.database.managed.config.InstanceDefinition

import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import scala.util.{Try, Using}

object Instance {
  val instanceCache: ConcurrentHashMap[InstanceDefinition, Instance] =
    new ConcurrentHashMap[InstanceDefinition, Instance]()

  sys.addShutdownHook {
    instanceCache.values().forEach{instance =>
      Try(instance.stopInstance(false))
    }
  }

  def getInstance(dockerClient: DockerClient, definition: InstanceDefinition): Instance = {
    instanceCache.computeIfAbsent(definition, definition => {
      val instance: Instance = new Instance(
        dockerClient,
        definition.instanceType.dockerImage(),
        freePort(),
        definition.env,
        definition.cmd
      )
      instance.startInstance()
      instance
    })
  }

  private def freePort(): Int = Using(new ServerSocket(0))(_.getLocalPort).get
}

final class Instance(dockerClient: DockerClient, dockerImage: String, val dockerPort: Int, env: Seq[String], cmd: Seq[String]) {
  private val closed = new AtomicBoolean(false)
  private val starting = new AtomicBoolean(false)
  private val started = new AtomicBoolean(false)
  private val containerCreated = new AtomicBoolean(false)
  private var dockerId: String = _

  def startInstance(): Unit = {
    if (closed.get()) throw new IllegalStateException("Closed instances cannot be restarted")
    started synchronized {
      if (starting.compareAndSet(false, true)) {
        //TODO handle
        val createResponse: CreateContainerResponse = dockerClient
          .createContainerCmd(dockerImage)
          .withEnv(env:_*)
          .withHostConfig(HostConfig.newHostConfig().withPortBindings(PortBinding.parse(s"$dockerPort:3306")))
          .withCmd(cmd:_*)
          .exec()
        dockerId = createResponse.getId
        containerCreated.set(true)
        //TODO handle
        dockerClient.startContainerCmd(dockerId).exec()
        //TODO ping until alive?
        started.set(true)
      }
    }
  }

  def stopInstance(): Unit = stopInstance(true)

  private def stopInstance(stateCheck: Boolean): Unit = {
    if (stateCheck && !containerCreated.get()) throw new IllegalStateException("Docker container not created")
    else if (stateCheck && !started.get()) throw new IllegalStateException("Instance has not been started")
    closed synchronized {
      //TODO what if inspect fails? Not there at all. handle
      if (started.get() && dockerClient.inspectContainerCmd(dockerId).exec().getState.getRunning) {
        //TODO handle
        dockerClient.stopContainerCmd(dockerId).exec()
      }
      //TODO handle
      if(containerCreated.get()) {
        dockerClient.removeContainerCmd(dockerId).exec()
      }
      closed.set(true)
      containerCreated.set(false)
      started.set(false)
      starting.set(false)
    }
  }
}

