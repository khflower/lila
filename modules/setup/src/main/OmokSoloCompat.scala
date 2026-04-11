package lila.setup

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }

import play.api.libs.json.*

import com.renju_note.isoo.SeqTree

object OmokSoloCompat:
  val SiteFormat = "omok-solo2/v1"
  val BinaryContentType = "application/octet-stream"
  private val Files = "ABCDEFGHIJKLMNO"
  private val BoardSize = 15

  final case class SoloNode(
      moveKey: Option[String],
      text: String,
      boxText: String,
      children: List[SoloNode]
  )

  final case class SoloFile(
      format: String = SiteFormat,
      version: Int = 1,
      currentPath: List[String],
      root: SoloNode
  )

  object SoloNode:
    given OFormat[SoloNode] = Json.format[SoloNode]

  object SoloFile:
    given OFormat[SoloFile] = Json.format[SoloFile]

  def readBinary(bytes: Array[Byte]): Either[String, SoloFile] =
    try
      val input = ObjectInputStream(ByteArrayInputStream(bytes))
      try
        input.readObject() match
          case tree: SeqTree => Right(fromSeqTree(tree))
          case other         => Left(s"Unsupported solo board binary object: ${other.getClass.getName}")
      finally input.close()
    catch
      case e: Throwable => Left(Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName))

  def writeBinary(file: SoloFile): Either[String, Array[Byte]] =
    try
      val out = ByteArrayOutputStream()
      val objectOut = ObjectOutputStream(out)
      try
        objectOut.writeObject(toSeqTree(file))
        objectOut.flush()
        Right(out.toByteArray)
      finally
        objectOut.close()
        out.close()
    catch
      case e: Throwable => Left(Option(e.getMessage).filter(_.nonEmpty).getOrElse(e.getClass.getSimpleName))

  private def fromSeqTree(tree: SeqTree): SoloFile =
    val head = tree.getHead()
    val root = SoloNode(
      moveKey = None,
      text = Option(head.getText()).getOrElse(""),
      boxText = Option(head.getBoxText()).orElse(Option(tree.getText_box())).getOrElse(""),
      children = decodeSiblings(head.getChild())
    )
    SoloFile(currentPath = currentPathOf(tree), root = root)

  private def toSeqTree(file: SoloFile): SeqTree =
    val tree = SeqTree()
    val head = tree.getHead()
    head.setText(emptyToNull(file.root.text))
    head.setBoxText(emptyToNull(file.root.boxText))
    buildChildren(head, file.root.children)
    val currentPath = file.currentPath.map(normalizeMoveKey)
    val pathNodes = followPath(head, currentPath)
    tree.clearNowBoard()
    pathNodes.zipWithIndex.foreach: (node, index) =>
      val moveNo = index + 1
      val stored =
        if moveNo % 2 == 1 then moveNo + 1
        else -1 * (moveNo + 1)
      tree.getNow_board()(node.getX())(node.getY()) = stored
    tree.setNow(pathNodes.lastOption.getOrElse(head))
    tree

  private def decodeSiblings(node: SeqTree.Node): List[SoloNode] =
    Iterator
      .iterate(Option(node))(_.flatMap(n => Option(n.getNext())))
      .takeWhile(_.nonEmpty)
      .flatten
      .map: current =>
        SoloNode(
          moveKey = Some(coordsToMoveKey(current.getX(), current.getY())),
          text = Option(current.getText()).getOrElse(""),
          boxText = Option(current.getBoxText()).getOrElse(""),
          children = decodeSiblings(current.getChild())
        )
      .toList

  private def buildChildren(parent: SeqTree.Node, children: List[SoloNode]): Unit =
    var previous: Option[SeqTree.Node] = None
    children.foreach: child =>
      val (x, y) = moveKeyToCoords(child.moveKey.getOrElse(throw IllegalArgumentException("Child node is missing moveKey")))
      val node = new SeqTree.Node(x, y)
      node.setParent(parent)
      node.setText(emptyToNull(child.text))
      node.setBoxText(emptyToNull(child.boxText))
      buildChildren(node, child.children)
      previous match
        case Some(prev) => prev.setNext(node)
        case None       => parent.setChild(node)
      previous = Some(node)

  private def followPath(head: SeqTree.Node, moves: List[String]): List[SeqTree.Node] =
    val nodes = scala.collection.mutable.ListBuffer.empty[SeqTree.Node]
    var parent = head
    moves.foreach: moveKey =>
      findChild(parent, moveKey) match
        case Some(node) =>
          nodes += node
          parent = node
        case None => throw IllegalArgumentException(s"Current path references missing move $moveKey")
    nodes.toList

  private def findChild(parent: SeqTree.Node, moveKey: String): Option[SeqTree.Node] =
    val normalized = normalizeMoveKey(moveKey)
    Iterator
      .iterate(Option(parent.getChild()))(_.flatMap(n => Option(n.getNext())))
      .takeWhile(_.nonEmpty)
      .flatten
      .find(node => coordsToMoveKey(node.getX(), node.getY()) == normalized)

  private def currentPathOf(tree: SeqTree): List[String] =
    val head = tree.getHead()
    Iterator
      .iterate(Option(tree.getNow()))(_.flatMap(node => Option(node.getParent())))
      .takeWhile(nodeOpt => nodeOpt.exists(_ ne head))
      .flatten
      .map(node => coordsToMoveKey(node.getX(), node.getY()))
      .toList
      .reverse

  private def moveKeyToCoords(raw: String): (Int, Int) =
    val moveKey = normalizeMoveKey(raw)
    val file = moveKey.headOption.getOrElse(throw IllegalArgumentException("Missing file letter"))
    val x = Files.indexOf(file)
    val rank = moveKey.drop(1).toIntOption.getOrElse(throw IllegalArgumentException(s"Invalid move key: $raw"))
    if x < 0 || x >= BoardSize || rank < 1 || rank > BoardSize then
      throw IllegalArgumentException(s"Move out of range: $raw")
    x -> (BoardSize - rank)

  private def coordsToMoveKey(x: Int, y: Int): String =
    if x < 0 || x >= BoardSize || y < 0 || y >= BoardSize then
      throw IllegalArgumentException(s"Coordinates out of range: $x,$y")
    s"${Files.charAt(x)}${BoardSize - y}"

  private def normalizeMoveKey(raw: String): String =
    raw.trim.toUpperCase

  private def emptyToNull(raw: String): String | Null =
    Option(raw).filter(_.nonEmpty).orNull
