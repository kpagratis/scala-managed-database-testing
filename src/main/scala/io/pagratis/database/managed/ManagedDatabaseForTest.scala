package io.pagratis.database.managed

import io.pagratis.database.managed.config.{DatabaseDefinition, InstanceDefinition}

trait ManagedDatabaseForTest[InnerClient] {
  def instanceDefinition(): InstanceDefinition

  def databaseDefinition(): DatabaseDefinition

  def managedDatabase(): ManagedDatabase[InnerClient]
}
