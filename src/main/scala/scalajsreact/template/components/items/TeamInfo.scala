package scalajsreact.template.components.items

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalajsreact.template.models.State.{GameState, Team, TeamMember}
import scalajsreact.template.models.types.StateSnapshotWS
import scalajsreact.template.utils.ColorCircle
import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

object TeamInfo {
  type Props = StateSnapshotWS[GameState]
  val colors = ColorCircle.COLORS

  object Style extends StyleSheet.Inline {
    import dsl._

    val popupContent =
      style(
        borderRadius(5.px),
        border := "1px solid black",
        padding(5.px),
        marginBottom(1.rem)
      )
  }
  Style.addToDocument()

  final class Backend($ : BackendScope[Props, Boolean]) {

    def render(p: Props, showEdits: Boolean): VdomNode = {
      val (gameStateSnapshot, sendMessage) = p

      def styledPlayer(name: String, color: String) =
        <.span(
          <.i("unknown").when(name.isEmpty()),
          name.unless(name.isEmpty()),
          ^.borderBottom := s"1px double ${color}"
        )

      def styledMember(member: TeamMember) =
        styledPlayer(member.player_name, member.player_color)

      val playerV = gameStateSnapshot.zoomState(_.player)(p => _.copy(player = p))
      val colorV =
        playerV.zoomState(_.player_color)(c => _.copy(player_color = c))

      <.div(
        <.p(
          gameStateSnapshot.value.team match {
            case Some(Team(team_name, team_code, members, self_id, quizadmin)) =>
              <.span(
                s"Joined ${if (quizadmin) "admin " else ""}team: $team_code with members: ", {
                  members.to(Seq).sorted.map(styledMember).mkTagMod(", ")
                },
                "."
              )
            case _ => <.i("Enter a code to join a team.")
          },
          " ",
          <.button(
            ^.onClick --> $.modState(s => !s),
            "Change team."
          )
        ),
        <.div(
          Style.popupContent,
          TeamId.Component(gameStateSnapshot, sendMessage),
          ^.padding := 10.px,
          ^.paddingLeft := 5.px
        ).when(showEdits)
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("TeamInfo")
    .initialState(false)
    .renderBackend[Backend]
    .build
}
