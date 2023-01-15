package io.github.kpagratis.database.managed.config

import io.github.kpagratis.database.managed.deps.Test

class InstanceDefinitionTest extends Test {
  test("builder builds") {
    val mockInstanceType = mock[SupportedInstanceType]
    val built = InstanceDefinition
      .Builder(mockInstanceType)
      .withEnvironmentVariables(Seq("variable1", "variable2"))
      .withArguments(Seq("--param1", "--param2"))
      .withRootPassword("password")
      .build

    built mustBe InstanceDefinition(
      mockInstanceType,
      Some("password"),
      Seq("variable1", "variable2"),
      Seq("--param1", "--param2"))
  }

}
