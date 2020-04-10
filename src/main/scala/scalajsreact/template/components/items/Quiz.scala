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

import scalajsreact.template.utils.ColorCircles
import japgolly.scalajs.react.extra.StateSnapshot

import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

trait MessageObj

case class ServerMessage(msg_type: String, payload: Json)

object QuizData {
  type StateSnapshotWS[A] = (StateSnapshot[A], (WebSocket, String) => Callback)

  val url = "ws://localhost:6789"

  case class Product(
      name: String,
      price: Double,
      category: String,
      stocked: Boolean
  )

  class Backend($ : BackendScope[Unit, State]) {
    def onColorChange(color: String): Callback = {
      val json_data = Json.obj(
        "action" -> Json.fromString("set_color"),
        "color" -> Json.fromString(color)
      )

      $.modState(_.copy(color = color)) >>
        $.state.map(_.ws.map(_.send(json_data.asJson.noSpaces)).getOrElse())
    }

    private val colorPickerRef = Ref.toScalaComponent(ColorCircles.ColorCircle)

    def render(s: State) = {
      val colorZ = $.zoomState(_.color)(value => _.copy(color = value))

      object Style extends StyleSheet.Inline {
        import dsl._
        val container = style() //style(display.flex, minHeight(600.px))

        val header =
          style(width(190.px), borderBottom :=! "1px solid rgb(223, 220, 220)")

        val content = style(padding(30.px))
      }
      Style.addToDocument()

      val stateSnapshot = StateSnapshot(s).setStateVia($)
      val colorV = stateSnapshot.zoomState(_.color)(c => _.copy(color = c))

      <.div(
        Style.container,
        <.div(
          Style.header,
          colorPickerRef.component(
            ColorCircles
              .Props(14, onColorChange, ColorCircles.COLORS, 15, 15, colorV)
          )
        ),
        <.div(
          Style.content,
          GameId((stateSnapshot, sendMessage)),
          TeamId(stateSnapshot, sendMessage),
          PlayerId(stateSnapshot, sendMessage),
          QuestionList_((s, this)),
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

    def onAnswerChange(q: String)(e: ReactEventFromInput) =
      e.extract(_.target.value)(value => {
//       $.state.map{ s =>
//         for {
//           ws_ <- s.ws
//         } ws_.send(ReqInfo.asJson.noSpaces)
//
///       } >>
        delayedOnAnswerChange($, q, value)
      })

    val delayedOnAnswerChange
        : ((BackendScope[Unit, State], String, String)) => Callback =
      Debounce.callback() {
        case (self, qid, value) =>
          self.state.map { s =>
            val json_data = Json.obj(
              "action" -> Json.fromString("update_answer"),
              "answer_id" -> Json.fromString(qid),
              "answer" -> Json.fromString(value),
              "player_id" -> Json.fromString(s.player_id)
            )
            s.ws.map(_.send(json_data.asJson.noSpaces))
          } >>
            self.modState(s => s.log(s"Entered: ${value}"))
      }
    //  val onAnswerChange: (ReactEventFromInput) => Callback = Debounce.callback() { case e =>

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
                case "init" =>
                  msg.payload.as[QuestionList] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(s"Error connecting: ${error.getMessage}")
                      )
                    case Right(q) =>
                      direct.modState(_.copy(questions = q))
                  }
                case "player_id" =>
                  msg.payload.as[(String, Option[String])] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(
                          s"Error connecting player_id: ${error.getMessage}"
                        )
                      )
                    case Right((id, color)) =>
                      SessionStorage.update("player_id", id)
                      direct.modState(_.copy(player_id = id))
                      color.foreach { c => direct.modState(_.copy(color = c)) }
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

  val AnswerInput_ = ScalaComponent
    .builder[(Question, AnswerSet, Backend)]("AnswerInput")
    .render_P {
      case (q, a, b) =>
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

        <.form(
          ^.`class` := "answer-input",
          <.input.text(
            ^.placeholder := "Answer ...",
//            ^.value := s.filterText,
            ^.`class` := "own_input",
            ^.onChange ==> b.onAnswerChange(q.toString)
          ),
        )
    }
    .build

  val Answers_ = ScalaComponent
    .builder[(Question, AnswerSet, Backend)]("Answers")
    .render_P {
      case (q, a, b) =>
        <.div(
          AnswerInput_((q, a, b)),
          <.ol(a.toTagMod(i => <.li(^.color := i.answer_by, i.answer_text)))
        )
    }
    .build

  val Question_ = ScalaComponent
    .builder[(Question, Backend)]("Question")
    .render_P {
      case (q, b) =>
        <.div(<.h1(q.title), Answers_(q, q.answers, b))
    }
    .build

  val QuestionList_ = ScalaComponent
    .builder[(State, Backend)]("QuestionList")
    .render_P {
      case (s, b) =>
        s.questions.questions.zipWithIndex.map {
          case (q, idx) =>
            Question_.withKey(s"qid-${idx}")(q, b)
        }.toVdomArray
    }
    .build

  val SocialQuiztancing = ScalaComponent
    .builder[Unit]("SocialQuiztancing")
    .initialState(
      State(
        None,
        "",
        Team(""),
        "",
        "",
        QuestionList(20, Vector.empty),
        Vector.empty
      )
    )
    .renderBackend[Backend]
    .componentDidMount(_.backend.start)
    .componentWillUnmount(_.backend.end)
    .build

  val TeamId = ScalaComponent
    .builder[StateSnapshotWS[State]]("TeamId")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newMessage = e.target.value
          stateSnapshot.modState(s =>
            s.copy(team = s.team.copy(team_id = newMessage))
          )
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          for {
            ws <- stateSnapshot.value.ws if stateSnapshot.value.allowSend
            json_data = Json.obj(
              "action" -> Json.fromString("load_team"),
              "team_id" -> Json.fromString(stateSnapshot.value.team.team_id)
            )
          } yield sendMessage(ws, json_data.asJson.noSpaces)

        def sendOnEnter(e: ReactKeyboardEvent): Callback =
          CallbackOption
            .keyCodeSwitch(e) {
              case KeyCode.Enter => send.getOrEmpty
            }
            .asEventDefault(e)

        <.div(
          <.input.text(
            ^.autoFocus := true,
            ^.placeholder := "Team ID",
            ^.value := stateSnapshot.value.team.team_id,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> sendOnEnter
          ),
          <.button(
            ^.disabled := send.isEmpty, // Disable button if unable to send
            ^.onClick --> stateSnapshot
              .modState(_.copy(color = "#FF9800")), //send, // --> suffixed by ? because it's for Option[Callback]
            "Send"
          )
        )
    }
    .build

  val GameId = ScalaComponent
    .builder[StateSnapshotWS[State]]("GameId")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newMessage = e.target.value
          stateSnapshot.modState(_.copy(game_id = newMessage))
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          for {
            ws <- stateSnapshot.value.ws
            if stateSnapshot.value.allowSend && stateSnapshot.value.game_id.nonEmpty
            json_data = Json.obj(
              "action" -> Json.fromString("load_game"),
              "game_id" -> Json.fromString(stateSnapshot.value.game_id)
            )
          } yield sendMessage(ws, json_data.asJson.noSpaces)

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
          ^.onSubmit -->? send,
          <.input.text(
            ^.autoFocus := true,
            ^.placeholder := "Game ID",
            ^.value := stateSnapshot.value.game_id,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> sendOnEnter
          ),
          <.button(
            ^.disabled := send.isEmpty, // Disable button if unable to send
            ^.onClick ==>? sendOnClick, // --> suffixed by ? because it's for Option[Callback]
            "Send"
          )
        )
    }
    .build

  val PlayerId = ScalaComponent
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

  def apply() = SocialQuiztancing().vdomElement
}
