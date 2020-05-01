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

object PlayerInfo {
  type Props = StateSnapshotWS[State]
  val colors = ColorCircle.COLORS

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
        >> Callback(stateSnapshot.value.ws.foreach(_.send(json_data.asJson.noSpaces))))
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
          s" (id: ${stateSnapshot.value.player.player_uuid})",
          <.button(
            ^.onClick --> $.modState(s => !s),
            "Edit Player"
          )
        ),
        <.div(
          ColorCircle.Component(
            ColorCircle.Props(14, onColorChange, colors, 15, 15, colorV)
          ),
          PlayerId.Component(stateSnapshot, sendMessage),
          PlayerName.Component(stateSnapshot, sendMessage)
        ).when(showEdits)
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("PlayerInfo")
    .initialState(false)
    .renderBackend[Backend]
    .build
}
