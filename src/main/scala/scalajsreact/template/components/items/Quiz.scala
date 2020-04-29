package scalajsreact.template.components.items

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.{KeyCode, SessionStorage}
import org.scalajs.dom.{CloseEvent, Event, MessageEvent, WebSocket}
import scalacss.DevDefaults._
import scalacss.ScalaCssReact._
import scalajsreact.template.models.State._
import scalajsreact.template.models.types._
import scalajsreact.template.routes.Item
import scalajsreact.template.utils.{ColorCircle, Debounce, ReactAddons}

import scala.scalajs.js

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

      ($.state >>= { (state: State) =>
        val player = state.player.copy(player_color = color)
        $.modState(_.copy(player = player))
      }) >>
        $.state.map(_.ws.map(_.send(json_data.asJson.noSpaces)).getOrElse())
    }

    object Style extends StyleSheet.Inline {
      import dsl._
      val container = style(
        maxWidth(1000.px)
      ) //style(display.flex, minHeight(600.px))

      val header =
        style(borderBottom :=! "1px solid rgb(223, 220, 220)")

      val unsafeInputs = playerColors
        .map {
          case (idx, c) =>
            Vector(
              unsafeChild(s".player-$idx .answer-input input")(
                &.focus(
                  borderColor(Color(c)),
                  boxShadow := "none",
                  outline := "none"
                )
              ),
              unsafeChild(s".team-answers .player-$idx .answer-inner")(
                &(
                  borderBottomColor := Color(c),
                  borderBottomWidth(2.px),
                  borderBottomStyle := solid,
                  boxShadow := "none",
                  outline := "none"
                )
              ),
              unsafeChild(
                s".team-answers .player-$idx.colored-dot .colored-dot-inner"
              )(
                &(
                  borderColor := Color(c),
                  borderRadius(50.pc),
                  boxShadow := f"inset 0 0 0 2px ${c}"
                )
              )
            )
        }
        .toVector
        .flatten

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
        unsafeChild(".item-enter")(opacity(0)),
        unsafeChild(".item-enter-active")(
          opacity(1),
          transition := "opacity 500ms ease-in"
        ),
        unsafeChild(".item-exit")(opacity(1)),
        unsafeChild(".item-exit-active")(
          opacity(0),
          transition := "opacity 500ms ease-in"
        ),
        unsafeInputs
      )
    }
    Style.addToDocument()

    private val colorPickerRef = Ref.toScalaComponent(ColorCircle.Component)

    def render(s: State) = {
      val playerZ = $.zoomState(_.player)(value => _.copy(player = value))
      val colorZ =
        playerZ.zoomState(_.player_color)(value => _.copy(player_color = value))

      val stateSnapshot = StateSnapshot(s).setStateVia($)
      val playerV = stateSnapshot.zoomState(_.player)(p => _.copy(player = p))
      val colorV =
        playerV.zoomState(_.player_color)(c => _.copy(player_color = c))
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
            PlayerName.Component(stateSnapshot, sendMessage),
            GameHeader.Component(stateSnapshot, sendMessage),
            QuestionList_.Component(stateSnapshot, sendMessage)
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
                case "team_id" =>
                  msg.payload.as[Team] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(
                          s"Error decoding team_id: ${error.getMessage}"
                        )
                      )
                    case Right(
                        t @ Team(team_name, team_code, members, self_id)
                        ) =>
                      val oldTeam = direct.state.team.map(_.team_code)
                      direct.modState(_.copy(team = Some(t)))
                      // clear answers if team has changed
                      if (Some(team_code) != oldTeam) {
                        direct.modState(_.copy(answers = Map.empty))
                      }
                  }
                case "player_id" =>
                  msg.payload.as[Player] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(
                          s"Error decoding player_id: ${error.getMessage}"
                        )
                      )
                    case Right(player) =>
                      SessionStorage
                        .update("player_id", player.player_uuid.toString())
                      direct.modState(_.copy(player = player))
                  }
                case "answer_changed" =>
                  msg.payload.as[AnswerUpdate] match {
                    case Left(error) =>
                      direct.modState(
                        _.log(
                          s"Error decoding update_answer: ${error.getMessage}"
                        )
                      )
                    case Right(
                        AnswerUpdate(
                          answer,
                          player_id,
                          answer_uuid,
                          question_uuid,
                          votes,
                          timestamp
                        )
                        ) =>
                      val ans = Answer(
                        player_id = player_id,
                        answer_text = answer,
                        answer_uuid = answer_uuid,
                        question_uuid = question_uuid,
                        votes = votes,
                        timestamp = timestamp
                      )
                      direct.modState(
                        _.copy(answers = addToGivenAnswers(direct.state.answers, ans))
                      )
                  }
                case "update_answer" => // todo
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
          None,
          Player(java.util.UUID.randomUUID, "", ""), // TODO set good defaults
          0,
          QuestionList(20, Vector.empty),
          Map.empty,
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


object GameHeader {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("GameHeader")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        <.h2(s"Game: ${stateSnapshot.value.game_uuid}")
    }
    .build
}

