package io.github.kpagratis.database.managed.config

import io.github.kpagratis.database.managed.deps.Test

class InstanceDefinitionTest extends Test {
  test("builder builds") {
    val mockInstanceType = mock[SupportedInstanceType]
    val built = InstanceDefinition
      .Builder(mockInstanceType)
      .withEnv(Seq("variable1", "variable2"))
      .withCmd(Seq("--param1", "--param2"))
      .build

    built mustBe InstanceDefinition(
      mockInstanceType,
      Seq("variable1", "variable2"),
      Seq("--param1", "--param2"))
  }

}
