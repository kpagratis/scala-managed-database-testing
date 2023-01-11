package io.pagratis.database.managed.client

import io.pagratis.database.managed.config.User
import java.util.concurrent.{Callable, ScheduledThreadPoolExecutor}
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

trait DatabaseClient[InnerClient] {
  protected val scheduler = new ScheduledThreadPoolExecutor(4)

  def dockerInstancePort(): Int

  def databaseInstanceConnectionCheck(): Unit

  def truncateTables(preserveTables: Seq[String]): Unit

  def init(): Unit

  def getRootClient(): InnerClient

  def getClient(user: User): InnerClient

  protected def retry(delay: Duration, timeout: Duration)(fn: => Callable[InnerClient]): InnerClient = {
    scheduler.submit(() => _retry(delay)(fn)).get(timeout.length, timeout.unit)
  }

  protected def retry(timeout: Duration)(fn: => Callable[InnerClient]): InnerClient = {
    scheduler.submit(() => _retry()(fn)).get(timeout.length, timeout.unit)
  }

  @tailrec
  protected final def _retry(delay: Duration)(fn: => Callable[InnerClient]): InnerClient = {
    Try(scheduler.schedule(fn, delay.length, delay.unit).get()) match {
      case Success(connection) => connection
      //TODO need the exception eventually?
      case Failure(exception) => _retry(delay)(fn)
    }
  }

  @tailrec
  protected final def _retry()(fn: => Callable[InnerClient]): InnerClient = {
    Try(scheduler.submit(fn).get()) match {
      case Success(connection) => connection
      //TODO need the exception eventually?
      case Failure(exception) => _retry()(fn)
    }
  }


}

