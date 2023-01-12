package io.github.kpagratis.database.managed.config

object DatabaseDefinition {
  final case class Builder(databaseName: String,
                           users: Seq[User] = Seq.empty[User],
                           databaseDDL: Seq[String] = Seq.empty[String]) {
    def withUsers(users: Seq[User]): Builder = copy(users = users)

    def withDatabaseDDL(databaseDDL: Seq[String]): Builder = copy(databaseDDL = databaseDDL)

    def build: DatabaseDefinition = DatabaseDefinition(databaseName, users, databaseDDL)
  }
}

final case class DatabaseDefinition(databaseName: String, users: Seq[User], databaseDDL: Seq[String])