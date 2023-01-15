package io.github.kpagratis.database.managed.deps

import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition}

class JdbcMySQLClient(
                       dockerInstancePort: Int,
                       instanceDefinition: InstanceDefinition,
                       databaseDefinition: DatabaseDefinition
                     ) extends JdbcClient(
  dockerInstancePort,
  instanceDefinition,
  databaseDefinition
) {

  override protected val baseUrl = s"jdbc:mysql://localhost:$dockerInstancePort"
  override protected val databaseUrl = s"$baseUrl/${databaseDefinition.databaseName}"
}
