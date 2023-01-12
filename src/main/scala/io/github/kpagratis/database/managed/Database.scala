package io.github.kpagratis.database.managed

import io.github.kpagratis.database.managed.config.{DatabaseDefinition, User}

import java.util.concurrent.ConcurrentHashMap

object Database {
  /**
   * (Docker Port, DatabaseDefinition) -> Database
   */
  val databaseCache: ConcurrentHashMap[(Int, DatabaseDefinition), Database] =
    new ConcurrentHashMap[(Int, DatabaseDefinition), Database]()

  val databaseDefinitionCache: ConcurrentHashMap[(Int, String), DatabaseDefinition] =
    new ConcurrentHashMap[(Int, String), DatabaseDefinition]()

  def getDatabase(definition: DatabaseDefinition, instanceDockerPort: Int): Database = {
    val existing = databaseDefinitionCache.computeIfAbsent((instanceDockerPort, definition.databaseName), _ => definition)
    if(definition != existing) {
      throw new IllegalStateException(
        s"""
           |Definition mismatch for database ${definition.databaseName} on $instanceDockerPort:
           |\tOriginal config: $existing
           |\tAttempted config: $definition""".stripMargin)
    }

    databaseCache.computeIfAbsent((instanceDockerPort, definition), {
      case (_, definition) =>
        val database = new Database(
          definition.databaseName,
          definition.users
        )
        database
    })
  }
}

final case class Database(databaseName: String, users: Seq[User])
