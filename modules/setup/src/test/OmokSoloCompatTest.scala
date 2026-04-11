package lila.setup

class OmokSoloCompatTest extends munit.FunSuite:

  test("binary round-trip preserves tree order current path and node text fields") {
    val file = OmokSoloCompat.SoloFile(
      currentPath = List("H8", "H9", "J10"),
      root = OmokSoloCompat.SoloNode(
        moveKey = None,
        text = "",
        boxText = "root note",
        children = List(
          OmokSoloCompat.SoloNode(
            moveKey = Some("H8"),
            text = "",
            boxText = "first",
            children = List(
              OmokSoloCompat.SoloNode(
                moveKey = Some("H9"),
                text = "A",
                boxText = "second",
                children = List(
                  OmokSoloCompat.SoloNode(Some("J10"), "B", "third", Nil),
                  OmokSoloCompat.SoloNode(Some("G10"), "C", "branch", Nil)
                )
              )
            )
          ),
          OmokSoloCompat.SoloNode(Some("I8"), "X", "alt", Nil)
        )
      )
    )

    val bytes = OmokSoloCompat.writeBinary(file).fold(err => fail(err), identity)
    assert(bytes.length > 8)
    assertEquals(bytes(0) & 0xff, 0xac)
    assertEquals(bytes(1) & 0xff, 0xed)

    val decoded = OmokSoloCompat.readBinary(bytes).fold(err => fail(err), identity)
    assertEquals(decoded.currentPath, file.currentPath)
    assertEquals(decoded.root.boxText, "root note")
    assertEquals(decoded.root.children.map(_.moveKey), List(Some("H8"), Some("I8")))

    val second = decoded.root.children.head.children.head
    assertEquals(second.moveKey, Some("H9"))
    assertEquals(second.text, "A")
    assertEquals(second.boxText, "second")
    assertEquals(second.children.map(_.moveKey), List(Some("J10"), Some("G10")))
    assertEquals(second.children.map(_.text), List("B", "C"))
  }

  test("A1 and O15 survive coordinate conversion through the binary format") {
    val file = OmokSoloCompat.SoloFile(
      currentPath = List("A1", "O15"),
      root = OmokSoloCompat.SoloNode(
        moveKey = None,
        text = "",
        boxText = "",
        children = List(
          OmokSoloCompat.SoloNode(
            moveKey = Some("A1"),
            text = "",
            boxText = "",
            children = List(
              OmokSoloCompat.SoloNode(Some("O15"), "", "edge", Nil)
            )
          )
        )
      )
    )

    val decoded = OmokSoloCompat.readBinary(OmokSoloCompat.writeBinary(file).fold(err => fail(err), identity)).fold(err => fail(err), identity)
    assertEquals(decoded.currentPath, List("A1", "O15"))
    assertEquals(decoded.root.children.head.moveKey, Some("A1"))
    assertEquals(decoded.root.children.head.children.head.moveKey, Some("O15"))
    assertEquals(decoded.root.children.head.children.head.boxText, "edge")
  }
