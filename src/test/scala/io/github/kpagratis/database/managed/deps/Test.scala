package io.github.kpagratis.database.managed.deps

import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.must.Matchers

trait Test extends AnyFunSuite with BeforeAndAfterEach with Matchers with IdiomaticMockito {}
