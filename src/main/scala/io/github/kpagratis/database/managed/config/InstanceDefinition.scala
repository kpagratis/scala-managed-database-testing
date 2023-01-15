package io.github.kpagratis.database.managed.config

object InstanceDefinition {
  final case class Builder(instanceType: SupportedInstanceType,
                           rootPassword: Option[String] = None,
                           env: Seq[String] = Seq.empty[String],
                           cmd: Seq[String] = Seq.empty[String]
                          ) {
    def withEnvironmentVariables(env: Seq[String]): Builder = copy(env = env)

    def withArguments(cmd: Seq[String]): Builder = copy(cmd = cmd)

    def withRootPassword(password: String): Builder = copy(rootPassword = Some(password))

    def build: InstanceDefinition = {
      InstanceDefinition(instanceType, rootPassword, env, cmd)
    }
  }
}

final case class InstanceDefinition(instanceType: SupportedInstanceType, rootPassword: Option[String], env: Seq[String], cmd: Seq[String])

