package lila.api

import java.nio.file.Files
import java.nio.file.Paths

class DevOmokStartRouteTest extends munit.FunSuite:

  test("dev omok start route accepts hyphenated full ids like the scaffold parser"):
    val routes = Files.readString(Paths.get("conf/routes"))
    val omokStartRoute = routes.linesIterator.find(_.contains("controllers.Round.omokStart"))

    assertEquals(
      omokStartRoute.map(_.trim),
      Some(
        """GET   /dev/omok/start/$fullId<[\w-]{12}>  controllers.Round.omokStart(fullId: GameFullId, ruleSet: String ?= "renju")"""
      )
    )
