package scalajsreact.template.components.items

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import org.scalajs.dom.{WebSocket, MessageEvent, Event, CloseEvent}
import org.scalajs.dom.ext.{KeyCode, SessionStorage}
import scala.scalajs.js

import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._

import io.circe.generic.extras.Configuration

import scalajsreact.template.utils.Debounce

import scala.scalajs.js.annotation.JSImport

import scalajsreact.template.models.State._
import scalajsreact.template.models.types._

import scalajsreact.template.utils.ColorCircle
import japgolly.scalajs.react.extra.StateSnapshot

import japgolly.scalajs.react.extra.router.RouterCtl

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._
import japgolly.scalajs.react.vdom.HtmlStyles.color
import scalacss.internal.Attrs.marginBlockStart

import scalajsreact.template.routes.Item
import scalacss.internal.DslBase.ToStyle

trait MessageObj

case class ServerMessage(msg_type: String, payload: Json)

object QuizData {
  final case class Props(game_uuid: Option[java.util.UUID])(
      implicit ctl: RouterCtl[Item]
  ) {
    def routerCtl = this.ctl
  }

  val colors = ColorCircle.COLORS
  val playerColors = Map.from(colors.zipWithIndex.map {
    case (c, idx) => idx -> c
  })
  def playerFromColor(color: String): Option[Int] =
    playerColors.find { case (idx, c) => c == color }.map(_._1)

  val url = "ws://localhost:6789"

  case class Product(
      name: String,
      price: Double,
      category: String,
      stocked: Boolean
  )

  class Backend($ : BackendScope[Props, State]) {
    def onColorChange(color: String): Callback = {
      val json_data = Json.obj(
        "action" -> Json.fromString("set_color"),
        "color" -> Json.fromString(color)
      )

      $.modState(_.copy(color = color)) >>
        $.state.map(_.ws.map(_.send(json_data.asJson.noSpaces)).getOrElse())
    }

    object Style extends StyleSheet.Inline {
      import dsl._
      val container = style() //style(display.flex, minHeight(600.px))

      val header =
        style(borderBottom :=! "1px solid rgb(223, 220, 220)")

      val unsafeInputs = playerColors.map{ case (idx, c) =>
          (unsafeChild(s".player-$idx .answer-input input")(
            &.focus (borderColor(Color(c)), boxShadow := "none", outline := "none")
          ))
      }.toVector

      val content = style(
        padding(30.px),
        unsafeChild(".answer-input input")(
          border := "1px solid #ced4da",
          borderRadius(0.25.rem),
          padding(0.375.rem, 0.75.rem),
          height(2.rem),
          width(250.px),
          fontSize(1.1.rem)
        ),
        unsafeInputs
      )
    }
    Style.addToDocument()

    private val colorPickerRef = Ref.toScalaComponent(ColorCircle.Component)

    def render(s: State) = {
      val colorZ = $.zoomState(_.color)(value => _.copy(color = value))

      val stateSnapshot = StateSnapshot(s).setStateVia($)
      val colorV = stateSnapshot.zoomState(_.color)(c => _.copy(color = c))
      val playerClass: Option[String] =
        playerFromColor(colorV.value).map(idx => s"player-$idx")

      <.div(
        Style.container,
        <.div(
          Style.header,
          <.h1("Social Quiztancing"),
          colorPickerRef.component(
            ColorCircle
              .Props(14, onColorChange, colors, 15, 15, colorV)
          )
        ),
        <.div(
          Style.content,
          <.div(s"State info: ${stateSnapshot.value.toString}"),
          <.div(
          ^.`class` :=? playerClass,
          GameId(stateSnapshot, sendMessage),
          TeamId.Component(stateSnapshot, sendMessage),
          PlayerId.Component(stateSnapshot, sendMessage),
          GameHeader.Component(stateSnapshot, sendMessage),
          QuestionList_.Component(stateSnapshot, sendMessage),
          ),
          <.h4("Connection log"),
          <.pre(
            ^.width := "83%",
            ^.height := 300.px,
            ^.overflow := "scroll",
            ^.border := "1px solid"
          )(
            s.wsLog.map(<.p(_)): _*
          ) // Display log
        )
      )
    }

    def sendMessage(ws: WebSocket, msg: String): Callback = {
      // Send a message to the WebSocket
      def send = Callback(ws.send(msg))

      // Update the log, clear the text box
      def updateState =
        $.modState(s => s.log(s"Sent: ${msg}"))

      send >> updateState
    }

    def start: Callback = {

      // This will establish the connection and return the WebSocket
      def connect = CallbackTo[WebSocket] {

        // Get direct access so WebSockets API can modify state directly
        // (for access outside of a normal DOM/React callback).
        // This means that calls like .setState will now return Unit instead of Callback.
        val direct = $.withEffectsImpure

        // These are message-receiving events from the WebSocket "thread".

        def onopen(e: Event): Unit = {
          // Indicate the connection is open
          direct.modState(_.log("Connected."))
          direct.state.initWS()
          direct.state.game_uuid.foreach(direct.state.loadGame(_))
        }

        def onmessage(e: MessageEvent): Unit = {
          // Echo message received
          val msg = decode[ServerMessage](e.data.toString)
          msg match {
            case Left(error) => //
              direct.modState(_.log(s"Error: ${error}"))
            case Right(msg) =>
              print(msg)
              direct.modState(_.log(s" connecting: ${msg.payload}"))
              msg.msg_type match {
                case "games_list" =>
                  msg.payload.as[Vector[Game]] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(s"Error decoding games_list: ${error.getMessage}")
                      )
                    case Right(q) =>
                      direct.modState(_.copy(games_list = q))
                  }
                case "init" =>
                  msg.payload.as[QuestionList] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(s"Error decoding init: ${error.getMessage}")
                      )
                    case Right(q) =>
                      direct.modState(_.copy(questions = q))
                  }
                case "player_id" =>
                  msg.payload.as[(String, Option[String])] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(
                          s"Error decoding player_id: ${error.getMessage}"
                        )
                      )
                    case Right((id, color)) =>
                      SessionStorage.update("player_id", id)
                      direct.modState(_.copy(player_id = id))
                      color.foreach { c => direct.modState(_.copy(color = c)) }
                  }
                case "answer_changed" =>
                  msg.payload.as[AnswerUpdate] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(
                          s"Error decoding update_answer: ${error.getMessage}"
                        )
                      )
                    case Right(AnswerUpdate(answer, player_id, question_uuid)) =>
                      val ans = Answer(answer_by = "x", answer_text = answer, answer_uuid = player_id, question_uuid = question_uuid)
                      direct.modState(_.copy(answers = direct.state.answers.filterNot(_.answer_uuid == ans.answer_uuid) + ans))
                  }
              }
          }
          direct.modState(_.log(s"Echo: ${e.data.toString}"))
        }

        def onerror(e: Event): Unit = {
          // Display error message
          val msg: String =
            e.asInstanceOf[js.Dynamic]
              .message
              .asInstanceOf[js.UndefOr[String]]
              .fold(s"Error occurred!")("Error occurred: " + _)
          direct.modState(_.log(msg))
        }

        def onclose(e: CloseEvent): Unit = {
          // Close the connection
          direct.modState(
            _.copy(ws = None).log(s"""Closed. Reason = "${e.reason}"""")
          )
        }

        // Create WebSocket and setup listeners
        val ws = new WebSocket(url)
        ws.onopen = onopen _
        ws.onclose = onclose _
        ws.onmessage = onmessage _
        ws.onerror = onerror _
        ws
      }

      // Here use attempt to catch any exceptions in connect
      connect.attempt.flatMap {
        case Right(ws) => {
          $.modState(_.log(s"Connecting to $url ...").copy(ws = Some(ws)))
        }
        case Left(error) =>
          $.modState(_.log(s"Error connecting: ${error.getMessage}"))
      }
    }

    def end: Callback = {
      def closeWebSocket = $.state.map(_.ws.foreach(_.close())).attempt
      def clearWebSocket = $.modState(_.copy(ws = None))
      closeWebSocket >> clearWebSocket
    }

  }

  val SocialQuiztancing = ScalaComponent
    .builder[Props]("SocialQuiztancing")
    .initialStateFromProps {
      case p @ Props(game_uuid) =>
        State(
          None,
          Vector.empty,
          game_uuid,
          Team(""),
          "",
          0,
          "",
          QuestionList(20, Vector.empty),
          Set.empty,
          Vector.empty,
          p.routerCtl
        )
    }
    .renderBackend[Backend]
    .componentDidMount(_.backend.start)
    .componentWillUnmount(_.backend.end)
    .build

  val GameId = ScalaComponent
    .builder[StateSnapshotWS[State]]("GameId")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newMessage = e.target.value
          val newUUID =
            try { Some(java.util.UUID.fromString(newMessage)) }
            catch { case _: Exception => None }
          stateSnapshot.modState(_.copy(game_uuid = newUUID))
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          for {
            game_uuid <- stateSnapshot.value.game_uuid
          } yield Callback(stateSnapshot.value.loadGame(game_uuid))

        def sendOnEnter(e: ReactKeyboardEvent): Callback =
          CallbackOption
            .keyCodeSwitch(e) {
              case KeyCode.Enter => send.getOrEmpty
            }
            .asEventDefault(e)

        def sendOnClick(e: ReactMouseEvent): Option[Callback] = {
          e.preventDefault()
          send
        }

        def gamesList() = {
          stateSnapshot.value.games_list.map { game =>
            stateSnapshot.value.ctl.link(Item.Quiz(Some(game.game_uuid)))(
              <.button(game.game_name),
              ^.key := game.game_uuid.toString()
            ),
          }.toVdomArray
        }

        <.div(
          gamesList(),
          <.form(
            ^.onSubmit -->? send,
            <.input.text(
              ^.autoFocus := true,
              ^.placeholder := "Game ID",
              ^.value := stateSnapshot.value.game_uuid
                .map(_.toString)
                .getOrElse(""),
              ^.onChange ==> onChange,
              ^.onKeyDown ==> sendOnEnter
            ),
            <.button(
              ^.disabled := send.isEmpty, // Disable button if unable to send
              ^.onClick ==>? sendOnClick, // --> suffixed by ? because it's for Option[Callback]
              "Send"
            )
          )
        )
    }
    .build

  def apply(props: Props)(implicit ctl: RouterCtl[Item]) =
    SocialQuiztancing
      .withKey(props.game_uuid.map(_.toString).getOrElse(""))(props)
      .vdomElement
}

object Game {
  final case class Props(state: StateSnapshot[State]) {
    @inline def render: VdomElement = Component(this)
  }

  //implicit val reusabilityProps: Reusability[Props] =
  //  Reusability.derive

  final case class State(
      game_uuid: java.util.UUID,
      team: Team,
      player_id: String,
      color: String,
      questions: QuestionList,
      answers: AnswerSet
  )

  final class Backend($ : BackendScope[Props, Unit]) {
    def render(p: Props): VdomNode = {
      val s = p.state.value
      <.div
      //  TeamId(stateSnapshot, sendMessage),
      //  PlayerId(stateSnapshot, sendMessage),
      //   QuestionList_((s, this)),

    }
  }

  val Component = ScalaComponent
    .builder[Props]("Game")
    .renderBackend[Backend]
    //.configure(Reusability.shouldComponentUpdate)
    .build
}

object AnswerInput {
  val Component = ScalaComponent
    .builder[WS[(Question, AnswerSet)]]("AnswerInput")
    .render_P {
      case ((q, a), wsSnapshot, sendMessage) =>
//     def onChange(e: ReactEventFromInput): Callback = {
//        val newMessage = e.target.value
//        val newTeamC = $.state.map(_.team).map(_.copy(team_id = newMessage))
//        newTeamC >>= (nt => $.modState(_.copy(team = nt)))
//      }

//      // Can only send if WebSocket is connected and user has entered text
//      val send: Option[Callback] =
//        for {
//          ws <- s.ws if s.allowSend
//          val json_data = Json.obj("action" -> Json.fromString("load_game"), "game_id" -> Json.fromString(s.team.team_id))
//        } yield b.sendMessage(ws, json_data.asJson.noSpaces)

//      def sendOnEnter(e: ReactKeyboardEvent): Callback =
//        CallbackOption
//          .keyCodeSwitch(e) {
//            case KeyCode.Enter => send.getOrEmpty
//          }
//          .asEventDefault(e)

        def changedAnswer(e: ReactEventFromInput) =
          e.extract(_.target.value)(value => {
//       $.state.map{ s =>
//         for {
//           ws_ <- s.ws
//         } ws_.send(ReqInfo.asJson.noSpaces)
//
///       } >>
            print(value)
            //$.modState(_.copy(filterText = value))
          })

        val delayedOnAnswerChange: ((java.util.UUID, String)) => Callback =
          Debounce.callback() {
            case (qid, value) =>
              val json_data = Json.obj(
                "action" -> Json.fromString("update_answer"),
                "question_uuid" -> Json.fromString(qid.toString),
                "answer" -> Json.fromString(value)
              )
              sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
          }

        def onAnswerChange(q: java.util.UUID)(e: ReactEventFromInput) =
          e.extract(_.target.value)(value => {
//       $.state.map{ s =>
//         for {
//           ws_ <- s.ws
//         } ws_.send(ReqInfo.asJson.noSpaces)
//
///       } >>
            delayedOnAnswerChange(q, value)
          })

        //  val onAnswerChange: (ReactEventFromInput) => Callback = Debounce.callback() { case e =>

        <.form(
          ^.`class` := "answer-input",
          <.input.text(
            ^.placeholder := "Answer ...",
//            ^.value := s.filterText,
            ^.onChange ==> onAnswerChange(q.question_uuid)
          )
        )
    }
    .build
}

object Answers {
  val Component = ScalaComponent
    .builder[WS[(Question, AnswerSet)]]("Answers")
    .render_P {
      case ((q, a), wsSnapshot, sendMessage) =>
        <.div(
          AnswerInput.Component(((q, a), wsSnapshot, sendMessage)),
          <.ol(a.toTagMod(i => <.li(^.color := i.answer_by, i.answer_text)))
        )
    }
    .build
}

object Question_ {
  val Component = ScalaComponent
    .builder[WS[(Question, AnswerSet)]]("Question")
    .render_P {
      case ((q, a), wsSnapshot, sendMessage) =>
        <.div(
          <.header(
            <.p(
              ^.`class` := "lead",
              s"Question ${q.idx}",
              ^.textTransform := "uppercase",
              ^.letterSpacing := 2.px,
              VdomStyle("marginBlockEnd") := 0.px
            ),
            <.h2(q.title, VdomStyle("marginBlockStart") := 0.px)
          ),
          Answers.Component(((q, a), wsSnapshot, sendMessage))
        )
    }
    .build
}


object GameHeader {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("GameHeader")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        <.h2(s"Game: ${stateSnapshot.value.game_uuid}")
    }
    .build
}

object QuestionList_ {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("QuestionList")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        val wsSnapshot = stateSnapshot.zoomState(_.ws)(w => _.copy(ws = w))
        val answers = stateSnapshot.value.answers
        stateSnapshot.value.questions.questions.zipWithIndex.map {
          case (q, idx) =>
            Question_.Component.withKey(s"qid-${idx}")(
              ((q, answers), wsSnapshot, sendMessage)
            )
        }.toVdomArray
    }
    .build
}

object TeamId {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("TeamId")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newId = e.target.value
          stateSnapshot.modState(s =>
            s.copy(team = s.team.copy(team_id = newId))
          )
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          if (stateSnapshot.value.team.team_id.isEmpty())
            None
            else
          Some(Callback(stateSnapshot.value.joinTeam(stateSnapshot.value.team.team_id)))

        def sendOnEnter(e: ReactKeyboardEvent): Callback =
          CallbackOption
            .keyCodeSwitch(e) {
              case KeyCode.Enter => send.getOrEmpty
            }
            .asEventDefault(e)

        def sendOnClick(e: ReactMouseEvent): Option[Callback] = {
          e.preventDefault()
          send
        }

        <.form(
          <.input.text(
            ^.autoFocus := true,
            ^.placeholder := "Team ID",
            ^.value := stateSnapshot.value.team.team_id,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> sendOnEnter
          ),
          <.button(
            ^.disabled := send.isEmpty, // Disable button if unable to send
            ^.onClick ==>? sendOnClick,
            "Send"
          )
        )
    }
    .build
}

object PlayerId {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("PlayerId")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newMessage = e.target.value
          stateSnapshot.modState(_.copy(player_id = newMessage))
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          for {
            ws <- stateSnapshot.value.ws if stateSnapshot.value.allowSend
            json_data = Json.obj(
              "action" -> Json.fromString("load_player"),
              "player_id" -> Json.fromString(stateSnapshot.value.player_id)
            )
          } yield sendMessage(ws, json_data.asJson.noSpaces)

        def sendOnEnter(e: ReactKeyboardEvent): Callback =
          CallbackOption
            .keyCodeSwitch(e) {
              case KeyCode.Enter => send.getOrEmpty
            }
            .asEventDefault(e)

        <.form(
          <.input.text(
            ^.autoFocus := true,
            ^.placeholder := "Player ID",
            ^.value := stateSnapshot.value.player_id,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> sendOnEnter
          ),
          <.button(
            ^.disabled := send.isEmpty, // Disable button if unable to send
            ^.onClick -->? send, // --> suffixed by ? because it's for Option[Callback]
            "Send"
          )
        )
    }
    .build
}
