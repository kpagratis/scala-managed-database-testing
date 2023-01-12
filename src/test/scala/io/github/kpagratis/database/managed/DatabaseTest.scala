package io.github.kpagratis.database.managed

import io.github.kpagratis.database.managed.config.{DatabaseDefinition, User}
import io.github.kpagratis.database.managed.deps.Test

class DatabaseTest extends Test {
  test("Database::getDatabase") {
    val definition = DatabaseDefinition(
      "testDb",
      Seq(User("user", "password", "all")),
      Seq("select from everything")
    )

    val database = Database.getDatabase(definition, 1234)
    database.databaseName mustBe "testDb"
    database.users must contain allElementsOf Seq(User("user", "password", "all"))
  }

  test("Database::getDatabase caches database") {
    val definition = DatabaseDefinition(
      "testDb",
      Seq(User("user", "password", "all")),
      Seq("select from everything")
    )

    Database.getDatabase(definition, 1234) mustBe theSameInstanceAs (Database.getDatabase(definition, 1234))
  }

  test("Database::getDatabase allows identical definition for separate instances") {
    val definition = DatabaseDefinition(
      "testDb",
      Seq(User("user", "password", "all")),
      Seq("select from everything")
    )

    Database.getDatabase(definition, 1234) mustNot be theSameInstanceAs Database.getDatabase(definition, 5678)
  }

  test("Database::getDatabase ensures database can only be configured 1 way per instance") {
    val definition = DatabaseDefinition(
      "testDb",
      Seq(User("user", "password", "all")),
      Seq("select from everything")
    )
    val badDefinition = DatabaseDefinition(
      "testDb",
      Seq(User("user", "different password", "all")),
      Seq("select from everything")
    )
    Database.getDatabase(definition, 1234)
    intercept[IllegalStateException]{
      Database.getDatabase(badDefinition, 1234)
    }
  }
}
