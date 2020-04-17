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
  case class Team(team_id: String)

  case class AnswerUpdate(answer: String, player_id: java.util.UUID, question_uuid: java.util.UUID)
  case class Answer(answer_by: String, answer_text: String, answer_uuid: java.util.UUID, question_uuid: java.util.UUID)
  type AnswerSet = Set[Answer]

  case class QuestionList(num_questions: Int, questions: Vector[Question])
  case class Question(title: String, idx: Int, question_uuid: java.util.UUID)

  case class Game(game_name: String, game_uuid: java.util.UUID)

  case class State(
      ws: Option[WebSocket],
      games_list: Vector[Game],
      game_uuid: Option[java.util.UUID],
      team: Team,
      player_id: String,
      player_code: Int,
      color: String,
      questions: QuestionList,
      answers: AnswerSet,
      // serverState
      wsLog: Vector[String],
      ctl: RouterCtl[Item]
  ) {
    override def toString = productPrefix + (productElementNames zip productIterator).filterNot(_._1 == "wsLog").map(_._2).mkString("(", ",", ")")

    def initWS() = {
      if (allowSend) {
        val player_id = SessionStorage("player_id")
        val msg = Init("init", player_id)
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
          "team_id" -> Json.fromString(id)
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
