package io.github.kpagratis.database.managed

import io.github.kpagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition}

trait ManagedDatabaseForTest[InnerClient] {
  def instanceDefinition(): InstanceDefinition

  def databaseDefinition(): DatabaseDefinition

  def managedDatabase(): ManagedDatabase[InnerClient]
}
