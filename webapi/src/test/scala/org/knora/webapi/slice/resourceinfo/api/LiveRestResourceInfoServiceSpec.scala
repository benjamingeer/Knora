package org.knora.webapi.slice.resourceinfo.api

import org.knora.webapi.slice.resourceinfo.repo.ResourceInfoRepo.{DESC, creationDate}
import org.knora.webapi.slice.resourceinfo.repo.TestResourceInfoRepo.{knownProjectIRI, knownResourceClass}
import org.knora.webapi.slice.resourceinfo.repo.{ResourceInfo, TestResourceInfoRepo}
import zio.test._

import java.time.Instant.now
import java.time.temporal.ChronoUnit.DAYS
import java.util.UUID.randomUUID

object LiveRestResourceInfoServiceSpec extends ZIOSpecDefault {
  override def spec =
    suite("LiveRestResourceInfoServiceSpec")(
      test("should return empty list if no resources found // unknown project and resourceClass") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass("unknown", "unknown")
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown resourceClass") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass(knownProjectIRI, "unknown")
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test("should return empty list if no resources found // unknown project") {
        for {
          actual <- RestResourceInfoService.findByProjectAndResourceClass("unknown", knownResourceClass)
        } yield assertTrue(actual == ListResponseDto.empty)
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass
          | then it should return all info sorted by lastModificationDate
          |""".stripMargin.linesIterator.mkString("")
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(10, DAYS), now.minus(9, DAYS))
        val given2 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(20, DAYS), now.minus(8, DAYS), now)
        for {
          _      <- TestResourceInfoRepo.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <- RestResourceInfoService.findByProjectAndResourceClass(knownProjectIRI, knownResourceClass)
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto(_)).sortBy(_.lastModificationDate)
          assertTrue(actual == ListResponseDto(items))
        }
      },
      test(
        """given two ResourceInfo exist
          | when findByProjectAndResourceClass ordered by CreationDate DESC
          | then it should return all info sorted correctly 
          |""".stripMargin.linesIterator.mkString("")
      ) {
        val given1 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(10, DAYS), now.minus(9, DAYS))
        val given2 = ResourceInfo("http://resourceIri/" + randomUUID, now.minus(20, DAYS), now.minus(8, DAYS), now)
        for {
          _ <- TestResourceInfoRepo.addAll(List(given1, given2), knownProjectIRI, knownResourceClass)
          actual <- RestResourceInfoService.findByProjectAndResourceClass(
                      knownProjectIRI,
                      knownResourceClass,
                      ordering = (creationDate, DESC)
                    )
        } yield {
          val items = List(given1, given2).map(ResourceInfoDto(_)).sortBy(_.creationDate).reverse
          assertTrue(actual == ListResponseDto(items))
        }
      }
    ).provide(
      LiveRestResourceInfoService.layer,
      TestResourceInfoRepo.layer
    )
}
