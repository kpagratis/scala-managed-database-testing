package io.github.kpagratis.database.managed.config

case class SuperUser(name: String, password: Option[String] = None)
case class User(name: String, password: String, privilege: String)
