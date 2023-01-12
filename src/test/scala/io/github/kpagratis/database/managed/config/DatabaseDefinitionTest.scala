package io.github.kpagratis.database.managed.config

import io.github.kpagratis.database.managed.deps.Test

class DatabaseDefinitionTest extends Test {
  test("builder builds") {
    val built = DatabaseDefinition
      .Builder("testing")
      .withUsers(Seq(User("user1", "user1Password", "ALL")))
      .withDatabaseDDL(Seq("create something awesome"))
      .build

    built mustBe DatabaseDefinition(
      "testing",
      Seq(User("user1", "user1Password", "ALL")),
      Seq("create something awesome"))
  }
}
