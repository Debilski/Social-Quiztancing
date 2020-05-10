package scalajsreact.template.components.items

import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import scalajsreact.template.models.State.{AnswerSet, Question, State, Team, Game}
import scalajsreact.template.models.types.{StateSnapshotWS, WS}
import scalajsreact.template.routes.Item


object GamesList {
  val Component = ScalaComponent
  .builder[StateSnapshotWS[State]]("GameId")
  .render_P {
    case (stateSnapshot, sendMessage) =>
      def numQuestions(game: Game) = game.num_questions match {
        case 1 => "1 question"
        case n => s"$n questions"
      }

      def gamesList() = {
        stateSnapshot.value.games_list.map { game =>
          stateSnapshot.value.ctl.link(Item.Quiz(Some(game.game_uuid)))(
            <.button(
              <.b(game.game_name),
              <.br,
              <.small(numQuestions(game))
              ),
            ^.key := game.game_uuid.toString()
          ),
        }.toVdomArray
      }

      <.div(
        gamesList()
      )
  }
  .build
}
