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

import japgolly.scalajs.react.extra.ReusabilityOverlay

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

    object Style extends StyleSheet.Inline {
      import dsl._
      val container = style(
        //     maxWidth(1000.px)
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

      val gameSnapshot =
        if (s.game.isDefined)
          Some(
            stateSnapshot.zoomState(s =>
              GameState(
                ws = s.ws,
                game = s.game.get,
                team = s.team,
                player = s.player,
                player_code = s.player_code,
                questions = s.questions,
                answers = s.answers,
                selected_answers = s.selected_answers
              )
            )(g =>
              _.copy(
                ws = g.ws,
                game = Some(g.game),
                team = g.team,
                player = g.player,
                player_code = g.player_code,
                questions = g.questions,
                answers = g.answers,
                selected_answers = g.selected_answers
              )
            )
          )
        else None

      def styledPlayer(name: String, color: String) =
        <.span(
          name,
          ^.borderBottom := s"1px double ${color}"
        )

      <.div(
        Style.container,
        <.div(
          Style.header,
          <.h1("Social Quiztancing"),
          <.div(
            ^.`class` := "alert alert-warning",
            s"Lost connection to Quiz server (${QuizData.url}). ",
            <.button(
              "Click to reconnect.",
              ^.onClick --> reconnectWebSocket
            )
          ).unless(stateSnapshot.value.ws.isDefined)
        ),
        <.div(
          Style.content,
          ^.`class` :=? playerClass,
          PlayerInfo.Component(stateSnapshot, sendMessage),
          gameSnapshot match {
            // show game if it exists
            case Some(gs) =>
              println(gs)
              <.div(
                TeamInfo.Component(gs, sendMessage),
                GameHeader.Component(gs, sendMessage),
                QuestionList_.Component(gs, sendMessage)
              )
            // show list of games otherwise
            case None => GamesList.Component(stateSnapshot, sendMessage)
          }
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
    }

    def sendMessage(ws: WebSocket, msg: String): Callback = {
      // Send a message to the WebSocket
      def send = Callback(ws.send(msg))

      // Update the log, clear the text box
      def updateState =
        $.modState(s => s.log(s"Sent: ${msg}"))

      send >> updateState
    }

    def reconnectWebSocket: Callback = start

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
          direct.props.game_uuid.foreach(direct.state.loadGame(_))
        }

        def onmessage(e: MessageEvent): Unit = {
          // Echo message received
          val msg = decode[ServerMessage](e.data.toString)
          msg match {
            case Left(error) => //
              direct.modState(_.log(s"Error: ${error}"))
            case Right(msg) =>
              direct.modState(_.log(s" connecting: ${msg.payload}"))

              def logFail(err: DecodingFailure) = direct.modState(
                _.log(s"Error decoding ${msg.msg_type}: ${err.getMessage}")
              )
              implicit class JsonExt(json: Json) {
                def asMatch[A: Decoder](fn: A => Unit) = json.as[A] match {
                  case Left(err) => logFail(err)
                  case Right(x)  => fn(x)
                }
              }

              msg.msg_type match {
                case "games_list" =>
                  msg.payload.asMatch[Vector[Game]] { q =>
                    direct.modState(_.copy(games_list = q))
                  }
                case "init" =>
                  msg.payload.asMatch[Game] { game =>
                    direct.modState(
                      _.copy(
                        game = Some(game),
                        questions =
                          QuestionList(game.num_questions, Vector.empty)
                      )
                    )
                  }
                case "team_id" =>
                  msg.payload.asMatch[Team] {
                    case t @ Team(
                          team_name,
                          team_code,
                          members,
                          self_id,
                          quizadmin
                        ) =>
                      val oldTeam = direct.state.team.map(_.team_code)
                      direct.modState(_.copy(team = Some(t)))
                      // clear answers if team has changed
                      if (Some(team_code) != oldTeam) {
                        direct.modState(_.copy(answers = Map.empty))
                      }
                      // update Player, if member with my Id has changed
                      val me = members.find(_.player_id == self_id)
                      direct.modStateOption { s =>
                        me.map { m =>
                          val updatedPlayer = s.player.copy(
                            player_name = m.player_name,
                            player_color = m.player_color
                          )
                          import org.scalajs.dom.document
                          document.body.style
                            .setProperty("--focus", m.player_color)
                          s.copy(player = updatedPlayer)
                        }
                      }
                  }
                case "player_id" =>
                  msg.payload.asMatch[Player] { player =>
                    SessionStorage
                      .update("player_id", player.player_uuid.toString())
                    import org.scalajs.dom.document
                    document.body.style
                      .setProperty("--focus", player.player_color)
                    direct.modState(_.copy(player = player))

                  }

                case "update_question" =>
                  msg.payload.asMatch[Question] { question =>
                    val questionList = direct.state.questions
                    direct.modState(
                      _.copy(questions = addToQuestionList(questionList, question))
                    )
                  }

                case "set_questions" =>
                  msg.payload.asMatch[Seq[Question]] { questions =>
                    val newQuestionList = questions.foldLeft(
                      direct.state.questions.copy(questions = Vector.empty)
                    ) {
                      case (questionList, q) =>
                        addToQuestionList(questionList, q)
                    }
                    direct.modState(
                      _.copy(questions = newQuestionList)
                    )
                  }

                case "set_answers" =>
                  msg.payload.asMatch[Seq[AnswerUpdate]] { answers =>
                    val newAnswers = answers.foldLeft(Map.empty: GivenAnswers) {
                      case (
                          ansMap,
                          AnswerUpdate(
                            answer,
                            player_id,
                            answer_uuid,
                            question_uuid,
                            votes,
                            is_selected,
                            timestamp
                          )
                          ) =>
                        addToGivenAnswers(
                          ansMap,
                          Answer(
                            player_id = player_id,
                            answer_text = answer,
                            answer_uuid = answer_uuid,
                            question_uuid = question_uuid,
                            votes = votes,
                            is_selected = is_selected,
                            timestamp = timestamp
                          )
                        )
                    }
                    direct.modState(
                      _.copy(answers = newAnswers)
                    )
                  }
case "set_selected_answers" =>
                  msg.payload.asMatch[Seq[SelectedAnswerUpdate]] { answers =>
                    val newAnswers = answers.foldLeft(Map.empty: SelectedAnswers) {
                      case (
                          ansMap,
                          SelectedAnswerUpdate(
                            answer,
                            answer_uuid,
                            question_uuid,
                            team_code
                          )
                          ) =>
                        addToSelectedAnswers(
                          ansMap,
                          SelectedAnswer(
                            answer = answer,
                            answer_uuid = answer_uuid,
                            question_uuid = question_uuid,
                            team_code = team_code
                          )
                        )
                    }
                    direct.modState(
                      _.copy(selected_answers = newAnswers)
                    )
                  }

                case "answer_changed" =>
                  msg.payload.asMatch[AnswerUpdate] {
                    case AnswerUpdate(
                        answer,
                        player_id,
                        answer_uuid,
                        question_uuid,
                        votes,
                        is_selected,
                        timestamp
                        ) =>
                      val ans = Answer(
                        player_id = player_id,
                        answer_text = answer,
                        answer_uuid = answer_uuid,
                        question_uuid = question_uuid,
                        votes = votes,
                        is_selected = is_selected,
                        timestamp = timestamp
                      )
                      direct.modState(
                        _.copy(answers =
                          addToGivenAnswers(direct.state.answers, ans)
                        )
                      )
                  }
                case "question_changed" =>
                  msg.payload.asMatch[Question] {
                    case q @ Question(
                          title,
                          idx,
                          question_uuid,
                          is_active
                        ) =>
                      direct.modState(
                        _.copy(questions =
                          addToQuestionList(direct.state.questions, q)
                        )
                      )
                  }
                case "update_answer" => // todo
                case rest            => print(s"Not matching: ${rest}")
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
      // must reset, otherwise we get error for not properly unmounting
      def resetWebSocket =
        $.state.map(_.ws.foreach(_.onclose = { (e: CloseEvent) => () }))
      def closeWebSocket = $.state.map(_.ws.foreach(_.close())).attempt
      def clearWebSocket = $.modState(_.copy(ws = None))
      resetWebSocket >> closeWebSocket >> clearWebSocket
    }

  }

  val SocialQuiztancing = ScalaComponent
    .builder[Props]("SocialQuiztancing")
    .initialStateFromProps {
      case p @ Props(game_uuid) =>
        State(
          None,
          Vector.empty,
          None,
          None,
          Player(java.util.UUID.randomUUID, "", ""), // TODO set good defaults
          0,
          QuestionList(20, Vector.empty),
          Map.empty,
          Map.empty,
          Vector.empty,
          p.routerCtl
        )
    }
    .renderBackend[Backend]
    .componentDidMount(_.backend.start)
    .componentWillUnmount(_.backend.end)
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
    .builder[StateSnapshotWS[GameState]]("GameHeader")
    .render_P {
      case (gameStateSnapshot, sendMessage) =>
        <.h2(s"Quiz: ${gameStateSnapshot.value.game.game_name}")
    }
    .build
}
