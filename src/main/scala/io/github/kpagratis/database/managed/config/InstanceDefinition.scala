package io.github.kpagratis.database.managed.config

object InstanceDefinition {
  final case class Builder(instanceType: SupportedInstanceType,
                           env: Seq[String] = Seq.empty[String],
                           cmd: Seq[String] = Seq.empty[String]
                          ) {
    def withEnv(env: Seq[String]): Builder = copy(env = env)

    def withCmd(cmd: Seq[String]): Builder = copy(cmd = cmd)

    def build: InstanceDefinition = {
      InstanceDefinition(instanceType, env, cmd)
    }
  }
}

final case class InstanceDefinition(instanceType: SupportedInstanceType, env: Seq[String], cmd: Seq[String])

