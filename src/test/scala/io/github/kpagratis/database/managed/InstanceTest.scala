package io.github.kpagratis.database.managed

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.command._
import com.github.dockerjava.api.model.{ExposedPort, HostConfig, InternetProtocol}
import io.github.kpagratis.database.managed.config.{InstanceDefinition, SupportedInstanceType}
import io.github.kpagratis.database.managed.deps.Test
import org.mockito.captor.{ArgCaptor, Captor}

class InstanceTest extends Test {
  private val env: Seq[String] = Seq("variable1", "variable2")
  private val cmd: Seq[String] = Seq("--parameter1", "--parameter2")

  private val aDockerImage = "aDockerImage"
  private val aDefaultPort = 9999
  val aDockerGUID = "aDockerGUID"

  private def withCreateContainerMocked(instanceDefinition: InstanceDefinition, startSuccess: Boolean): (DockerClient, Captor[HostConfig]) = {
    val mockDockerClient = mock[DockerClient]
    val mockCreateContainerCmd = mock[CreateContainerCmd]
    val mockStartContainerCmd = mock[StartContainerCmd]
    val mockCreateResponse = mock[CreateContainerResponse]
    val hostConfig = ArgCaptor[HostConfig]

    mockCreateContainerCmd.withEnv(instanceDefinition.env:_*) returns mockCreateContainerCmd
    mockCreateContainerCmd.withHostConfig(hostConfig) returns mockCreateContainerCmd
    mockCreateContainerCmd.withCmd(instanceDefinition.cmd:_*) returns mockCreateContainerCmd
    mockCreateContainerCmd.exec() returns mockCreateResponse

    mockCreateResponse.getId returns aDockerGUID

    mockDockerClient.createContainerCmd(aDockerImage) returns mockCreateContainerCmd
    mockDockerClient.startContainerCmd(aDockerGUID) returns mockStartContainerCmd
    if(startSuccess) {
      mockStartContainerCmd.exec().doesNothing()
    } else {
      mockStartContainerCmd.exec() throws new Exception("oops")
    }
    (mockDockerClient, hostConfig)
  }

  private def withContainerShutdownMocked(dockerClient: DockerClient): Unit = {
    val mockInspectContainerCmd = mock[InspectContainerCmd]
    val mockInspectContainerResponse = mock[InspectContainerResponse]
    val mockContainerState = mock[InspectContainerResponse#ContainerState]
    val mockStopContainerCmd = mock[StopContainerCmd]
    val mockRemoveContainerCmd = mock[RemoveContainerCmd]
    val mockMount = mock[InspectContainerResponse.Mount]
    val mockRemoveVolumeCmd = mock[RemoveVolumeCmd]

    dockerClient.inspectContainerCmd(aDockerGUID) returns mockInspectContainerCmd

    mockInspectContainerCmd.exec() returns mockInspectContainerResponse
    mockInspectContainerResponse.getState returns mockContainerState
    mockInspectContainerResponse.getMounts returns java.util.List.of(mockMount)
    mockContainerState.getRunning returns true

    mockMount.getName returns "mountName"

    dockerClient.removeVolumeCmd("mountName") returns mockRemoveVolumeCmd
    mockRemoveVolumeCmd.exec().doesNothing()

    dockerClient.stopContainerCmd(aDockerGUID) returns mockStopContainerCmd
    mockStopContainerCmd.exec().doesNothing()

    dockerClient.removeContainerCmd(aDockerGUID) returns mockRemoveContainerCmd
    mockRemoveContainerCmd.exec().doesNothing()
  }

  private def withMockedInstanceType(): SupportedInstanceType = {
    val mockInstanceType = mock[SupportedInstanceType]
    mockInstanceType.dockerImage() returns aDockerImage
    mockInstanceType.defaultPort() returns aDefaultPort
    mockInstanceType
  }

  test("Instance::getInstance") {
    val mockInstanceType = withMockedInstanceType()

    val instanceDefinition = InstanceDefinition.Builder(
      mockInstanceType)
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build


    val (mockDockerClient, hostConfig) = withCreateContainerMocked(instanceDefinition, startSuccess = true)

    val instance = Instance.getInstance(mockDockerClient, instanceDefinition)
    val value = hostConfig.value.getPortBindings.getBindings.get(new ExposedPort(aDefaultPort, InternetProtocol.TCP))
    value mustNot be(null)
    value(0).toString mustBe s"${instance.dockerPort}"
  }

  test("Instance::getInstance caches the instance") {
    val mockInstanceType = withMockedInstanceType()

    val instanceDefinition = InstanceDefinition.Builder(
      mockInstanceType)
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build


    val (mockDockerClient, _) = withCreateContainerMocked(instanceDefinition, startSuccess = true)
    Instance.getInstance(mockDockerClient, instanceDefinition)
    Instance.getInstance(mockDockerClient, instanceDefinition)
    mockDockerClient wasNever calledAgain
  }

  test("Instance::startInstance") {
    val instanceDefinition = InstanceDefinition.Builder(
      mock[SupportedInstanceType])
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build

    val (mockDockerClient, hostConfig) = withCreateContainerMocked(instanceDefinition, startSuccess = true)

    val instance = new Instance(mockDockerClient, aDockerImage, aDefaultPort, 5678, instanceDefinition.env, instanceDefinition.cmd)

    instance.startInstance()
    val value = hostConfig.value.getPortBindings.getBindings.get(new ExposedPort(aDefaultPort, InternetProtocol.TCP))
    value mustNot be(null)
    value(0).toString mustBe s"${instance.dockerPort}"

    mockDockerClient.startContainerCmd(any) wasCalled once
    instance.startInstance()
    mockDockerClient wasNever calledAgain
  }

  test("Instance::stopInstance after Instance::startInstance") {
    val instanceDefinition = InstanceDefinition.Builder(
      mock[SupportedInstanceType])
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build

    val (mockDockerClient, _) = withCreateContainerMocked(instanceDefinition, startSuccess = true)

    val instance = new Instance(mockDockerClient, aDockerImage, aDefaultPort, 5678, instanceDefinition.env, instanceDefinition.cmd)

    instance.startInstance()
    withContainerShutdownMocked(mockDockerClient)
    instance.stopInstance()

  }

  test("Instance::stopInstance without calling Instance::startInstance") {
    val instanceDefinition = InstanceDefinition.Builder(
      mock[SupportedInstanceType])
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build

    val mockDockerClient = mock[DockerClient]
    val instance = new Instance(mockDockerClient, aDockerImage, aDefaultPort, 5678, instanceDefinition.env, instanceDefinition.cmd)
    intercept[IllegalStateException] {
      instance.stopInstance()
    }.getMessage mustBe "Docker container not created"
  }

  test("Instance::stopInstance when container hasn't been created") {
    val instanceDefinition = InstanceDefinition.Builder(
      mock[SupportedInstanceType])
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build

    val mockDockerClient = mock[DockerClient]
    val instance = new Instance(mockDockerClient, aDockerImage, aDefaultPort, 5678, instanceDefinition.env, instanceDefinition.cmd)

    mockDockerClient.createContainerCmd(aDockerImage) throws new Exception("oops")
    intercept[Exception] {
      instance.startInstance()
    }
    intercept[IllegalStateException] {
      instance.stopInstance()
    }.getMessage mustBe "Docker container not created"
  }

  test("Instance::stopInstance when container has failed to start") {
    val instanceDefinition = InstanceDefinition.Builder(
      mock[SupportedInstanceType])
      .withEnvironmentVariables(env)
      .withArguments(cmd)
      .build

    val (mockDockerClient, _) = withCreateContainerMocked(instanceDefinition, startSuccess = false)
    val instance = new Instance(mockDockerClient, aDockerImage, aDefaultPort, 5678, instanceDefinition.env, instanceDefinition.cmd)
    intercept[Exception] {
      instance.startInstance()
    }
    intercept[IllegalStateException] {
      instance.stopInstance()
    }.getMessage mustBe "Instance has not been started"
  }

}
