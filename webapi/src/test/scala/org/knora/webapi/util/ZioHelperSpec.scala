package org.knora.webapi.util

import zio._
import zio.test._

object ZioHelperSpec extends ZIOSpecDefault {

  val spec: Spec[Any, Throwable] = suite("ZioHelper")(
    suite(".sequence(List)")(
      test("should sequence a List of ZIOs") {
        val list = List(ZIO.succeed("1"), ZIO.succeed("2"))
        for {
          actual <- ZioHelper.sequence(list)
        } yield assertTrue(actual == List("1", "2"))
      },
      test("should sequence an empty List") {
        val list: List[Task[String]] = List.empty
        for {
          actual <- ZioHelper.sequence(list)
        } yield assertTrue(actual == List.empty[String])
      }
    ),
    suite("sequence(Set)")(
      test("should sequence a Set of ZIOs") {
        val set: Set[Task[String]] = Set(ZIO.succeed("1"), ZIO.succeed("2"))
        for {
          actual <- ZioHelper.sequence(set)
        } yield assertTrue(actual == Set("1", "2"))
      },
      test("should sequence an empty Set") {
        val set: Set[Task[String]] = Set.empty
        for {
          actual <- ZioHelper.sequence(set)
        } yield assertTrue(actual == Set.empty[String])
      }
    )
  )
}
