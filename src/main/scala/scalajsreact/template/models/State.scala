package scalajsreact.template.models

import org.scalajs.dom.{WebSocket, MessageEvent, Event, CloseEvent}
import org.scalajs.dom.ext.SessionStorage

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

import monocle.Lens
import monocle.macros.GenLens

import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.extra.router.RouterCtl

import scalajsreact.template.routes.Item

object types {
  type WS[A] =
    (A, StateSnapshot[Option[WebSocket]], (WebSocket, String) => Callback)
  type StateSnapshotWS[A] = (StateSnapshot[A], (WebSocket, String) => Callback)
  type StateSnapshotWS2[A, B] =
    (StateSnapshot[A], StateSnapshot[B], (WebSocket, String) => Callback)
}

object State {

  case class Init(action: String, uuid: Option[String])

  case class Message(action: String)
  case class Team(team_name: String, team_code: String, members: Set[TeamMember], self_id: Int)
  case class TeamMember(player_name: String, player_color: String, player_id: Int)
  implicit val TeamMemberOrdering: Ordering[TeamMember] = Ordering.by(teamMember => (teamMember.player_name, teamMember.player_id))

  case class AnswerUpdate(answer: String, player_id: Int, answer_uuid: java.util.UUID, question_uuid: java.util.UUID, votes: Vector[Int], timestamp: Long)
  case class Answer(player_id: Int, answer_text: String, answer_uuid: java.util.UUID, question_uuid: java.util.UUID, votes: Vector[Int], timestamp: Long)

  implicit val AnswerOrdering: Ordering[Answer] = Ordering.by(answer => (-answer.votes.size, answer.timestamp))

  type AnswerSet = Vector[Answer]
  type GivenAnswers = Map[java.util.UUID, AnswerSet]

  def addToGivenAnswers(givenAnswers: GivenAnswers, answer: Answer) = {
    val question_uuid = answer.question_uuid
    val filtered = givenAnswers.getOrElse(question_uuid, Vector.empty).filterNot(_.answer_uuid == answer.answer_uuid)
    givenAnswers.updated(question_uuid, (filtered :+ answer).sorted)
  }

  case class QuestionList(num_questions: Int, questions: Vector[Question])
  case class Question(title: String, idx: Int, question_uuid: java.util.UUID)

  case class Game(game_name: String, game_uuid: java.util.UUID)

  case class Player(player_uuid: java.util.UUID, player_name: String, player_color: String)

  case class State(
      ws: Option[WebSocket],
      games_list: Vector[Game],
      game_uuid: Option[java.util.UUID],
      team: Option[Team],
      player: Player,
      player_code: Int,
      questions: QuestionList,
      answers: GivenAnswers,
      // serverState
      wsLog: Vector[String],
      ctl: RouterCtl[Item]
  ) {
    override def toString = productPrefix + (productElementNames zip productIterator).filterNot(_._1 == "wsLog").map(_._2).mkString("(", ",", ")")

    def initWS(player_id: Option[String] = None) = {
      if (allowSend) {
        val msg = Init("init", player_id orElse SessionStorage("player_id"))
        ws.foreach(_.send(msg.asJson.noSpaces))
      }
    }

    def loadGame(uuid: java.util.UUID) = {
      if (allowSend) {
        val json_data = Json.obj(
          "action" -> Json.fromString("load_game"),
          "game_uuid" -> Json.fromString(uuid.toString())
        )
        ws.foreach(_.send(json_data.asJson.noSpaces))
      }
    }

    def joinTeam(id: String) = {
      if (allowSend) {
        val json_data = Json.obj(
          "action" -> Json.fromString("join_team"),
          "team_code" -> Json.fromString(id),
        )
        ws.foreach(_.send(json_data.asJson.noSpaces))
      }
    }

    def setName(name: String) = {
      if (allowSend) {
        val json_data = Json.obj(
          "action" -> Json.fromString("set_name"),
          "player_name" -> Json.fromString(name),
        )
        ws.foreach(_.send(json_data.asJson.noSpaces))
      }
    }

    def allowSend: Boolean =
      ws.exists(_.readyState == WebSocket.OPEN)

    // Create a new state with a line added to the log
    def log(line: String): State =
      copy(wsLog = wsLog.takeRight(20) :+ line)
  }

//  val questions: Lens[State, QuestionList] = GenLens[State](_.questions)
//  val team: Lens[State, Team] = GenLens[State](_.team)
//  val team_id: Lens[Team, String] = GenLens[Team](_.team_id)
}
