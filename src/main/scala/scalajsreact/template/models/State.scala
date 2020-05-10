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
  case class Team(team_name: String, team_code: String, members: Set[TeamMember], self_id: Int, quizadmin: Boolean)
  case class TeamMember(player_name: String, player_color: String, player_id: Int)
  implicit val TeamMemberOrdering: Ordering[TeamMember] = Ordering.by(teamMember => (teamMember.player_name, teamMember.player_id))

  case class AnswerUpdate(answer: String, player_id: Int, answer_uuid: java.util.UUID, question_uuid: java.util.UUID, votes: Vector[Int], is_selected: Boolean, timestamp: Long)
  case class Answer(player_id: Int, answer_text: String, answer_uuid: java.util.UUID, question_uuid: java.util.UUID, votes: Vector[Int], is_selected: Boolean, timestamp: Long)

  implicit val AnswerOrdering: Ordering[Answer] = Ordering.by(answer => (-answer.votes.size, answer.timestamp))

  case class SelectedAnswerUpdate(answer: String, answer_uuid: java.util.UUID, question_uuid: java.util.UUID, team_code: String)
  case class SelectedAnswer(answer: String, answer_uuid: java.util.UUID, question_uuid: java.util.UUID, team_code: String)

  implicit val SelectedAnswerOrdering: Ordering[SelectedAnswer] = Ordering.by(answer => answer.team_code)

  type AnswerSet = Vector[Answer]
  type SelectedAnswerSet = Map[String, SelectedAnswer]
  type GivenAnswers = Map[java.util.UUID, AnswerSet]
  type SelectedAnswers = Map[java.util.UUID, SelectedAnswerSet]

  def addToGivenAnswers(givenAnswers: GivenAnswers, answer: Answer): GivenAnswers = {
    val question_uuid = answer.question_uuid
    val filtered = givenAnswers.getOrElse(question_uuid, Vector.empty).filterNot(_.answer_uuid == answer.answer_uuid)
    givenAnswers.updated(question_uuid, (filtered :+ answer).sorted)
  }

  def addToSelectedAnswers(selectedAnswers: SelectedAnswers, answer: SelectedAnswer): SelectedAnswers = {
    val question_uuid = answer.question_uuid
    val filtered = selectedAnswers.getOrElse(question_uuid, Map.empty).updated(answer.team_code, answer)
    selectedAnswers.updated(question_uuid, filtered)
  }

  def addToQuestionList(questions: QuestionList, question: Question): QuestionList = {
    val QuestionList(num_questions, qs) = questions
    // remove old question with same uuid and add new question ordered
    val filtered = qs.filterNot(_.question_uuid == question.question_uuid)
    val insertIdx = filtered.indexWhere(_.idx > question.idx)
    val newQs = if (insertIdx == -1)
      filtered :+ question
    else {
      val (fst, snd) = filtered.splitAt(insertIdx)
      (fst :+ question) ++ snd
    }
    QuestionList(num_questions, newQs)
  }

  case class QuestionList(num_questions: Int, questions: Vector[Question])
  case class Question(title: String, idx: Int, question_uuid: java.util.UUID, is_active: Boolean)

  case class Game(game_name: String, game_uuid: java.util.UUID, num_questions: Int)

  case class Player(player_uuid: java.util.UUID, player_name: String, player_color: String)

  case class GameState(
      ws: Option[WebSocket],
      game: Game,
      team: Option[Team],
      player: Player,
      player_code: Int,
      questions: QuestionList,
      answers: GivenAnswers,
      selected_answers: SelectedAnswers
  ) {
     def joinTeam(id: String) = {
      if (allowSend) {
        val json_data = Json.obj(
          "action" -> Json.fromString("join_team"),
          "team_code" -> Json.fromString(id),
        )
        ws.foreach(_.send(json_data.asJson.noSpaces))
      }
    }

    def allowSend: Boolean =
      ws.exists(_.readyState == WebSocket.OPEN)

    def adminMode: Boolean = team.map(_.quizadmin) getOrElse false
  }

  case class State(
      ws: Option[WebSocket],
      games_list: Vector[Game],
      game: Option[Game],
      team: Option[Team],
      player: Player,
      player_code: Int,
      questions: QuestionList,
      answers: GivenAnswers,
      selected_answers: SelectedAnswers,
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
