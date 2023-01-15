package io.github.kpagratis.database.managed.config

object InstanceDefinition {
  final case class Builder(instanceType: SupportedInstanceType,
                           rootPassword: Option[String] = None,
                           superUser: Option[SuperUser] = None,
                           env: Seq[String] = Seq.empty[String],
                           cmd: Seq[String] = Seq.empty[String]
                          ) {
    def withEnvironmentVariables(env: Seq[String]): Builder = copy(env = env)

    def withArguments(cmd: Seq[String]): Builder = copy(cmd = cmd)

    def withSuperUser(user: SuperUser): Builder = copy(superUser = Some(user))

    def build: InstanceDefinition = {
      InstanceDefinition(instanceType, superUser.getOrElse(SuperUser("root")), env, cmd)
    }
  }
}

final case class InstanceDefinition(instanceType: SupportedInstanceType, superUser: SuperUser, env: Seq[String], cmd: Seq[String])

