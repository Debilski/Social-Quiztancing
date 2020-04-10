package scalajsreact.template.models

import org.scalajs.dom.{WebSocket, MessageEvent, Event, CloseEvent}
import org.scalajs.dom.ext.SessionStorage

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._
import monocle.macros._

object State {

  case class Init(action: String, uuid: Option[String])

  case class Message(action: String)
  case class Team(team_id: String)

  case class Answer(answer_by: String, answer_text: String)
  type AnswerSet = Set[Answer]

  case class QuestionList(num_questions: Int, questions: Vector[Question])
  case class Question(title: String, answers: AnswerSet)

  case class State (
      ws: Option[WebSocket],
      game_id: String,
      team: Team,
      player_id: String,
      color: String,
      questions: QuestionList,
      wsLog: Vector[String],
  ) {
    def initWS() = {
      if (allowSend) {
        val player_id = SessionStorage("player_id")
        val msg =  Init("init", player_id)
        ws.foreach(_.send(msg.asJson.noSpaces))
      }
    }

    def allowSend: Boolean =
      ws.exists(_.readyState == WebSocket.OPEN)

    // Create a new state with a line added to the log
    def log(line: String): State =
      copy(wsLog = wsLog :+ line)
  }
}

