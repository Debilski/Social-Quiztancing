package scalajsreact.template.components.items

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalajsreact.template.models.State.State
import scalajsreact.template.models.types.StateSnapshotWS
import scalajsreact.template.utils.ColorCircle
import scalacss.DevDefaults._
import scalacss.ScalaCssReact._

object PlayerInfo {
  type Props = StateSnapshotWS[State]
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
      val (stateSnapshot, sendMessage) = p

      def styledPlayer(name: String, color: String) =
        <.span(
          <.i("unknown").when(name.isEmpty()),
          name.unless(name.isEmpty()),
          ^.borderBottom := s"1px double ${color}"
        )

      def onColorChange(color: String): Callback = {
        val json_data = Json.obj(
          "action" -> Json.fromString("set_color"),
          "color" -> Json.fromString(color)
        )

        val player = stateSnapshot.value.player.copy(player_color = color)

        (stateSnapshot.modState(_.copy(player = player))
          >> Callback(
            stateSnapshot.value.ws.foreach(_.send(json_data.asJson.noSpaces))
          ))
      }

      val playerV = stateSnapshot.zoomState(_.player)(p => _.copy(player = p))
      val colorV =
        playerV.zoomState(_.player_color)(c => _.copy(player_color = c))

      <.div(
        <.p(
          "Player: ",
          styledPlayer(
            stateSnapshot.value.player.player_name,
            stateSnapshot.value.player.player_color
          ),
          " ",
          <.button(
            ^.onClick --> $.modState(s => !s),
            "Change name and color."
          )
        ),
        <.div(
          Style.popupContent,
          PlayerName.Component(stateSnapshot, sendMessage),
          <.div(
          ColorCircle.Component(
            ColorCircle.Props(290, onColorChange, colors, 10, 10, colorV),
          ),
          ^.padding := 10.px,
          ^.paddingLeft := 5.px,
          ),
          PlayerId.Component(stateSnapshot, sendMessage),
        ).when(showEdits)
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]
    .initialState(false)
    .renderBackend[Backend]
    .build
}
