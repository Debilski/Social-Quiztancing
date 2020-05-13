package scalajsreact.template.components.items

import japgolly.scalajs.react.{BackendScope, Callback, CallbackOption, ReactEventFromInput, ReactKeyboardEvent, ReactMouseEvent, ScalaComponent}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalajsreact.template.models.State.{GameState, Team, TeamMember}
import scalajsreact.template.models.types.StateSnapshotWS

object TeamId {
  class Backend($ : BackendScope[StateSnapshotWS[GameState], String]) {
    def render(p: StateSnapshotWS[GameState], s: String): VdomNode = p match {
      case (gameStateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newId = e.target.value
          $.setState(newId)
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          if (s.isEmpty())
            None
          else
            Some($.state map (id => gameStateSnapshot.value.joinTeam(id)))

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

        <.div(
          <.form(
            <.input.text(
              ^.display := "inline",
              ^.autoFocus := true,
              ^.placeholder := "Team ID",
              ^.value := s,
              ^.onChange ==> onChange,
              ^.onKeyDown ==> sendOnEnter
            ),
            <.button(
              ^.disabled := send.isEmpty, // Disable button if unable to send
              ^.onClick ==>? sendOnClick,
              "Join Team"
            )
          ),

        )
    }
  }

  val Component = ScalaComponent
    .builder[StateSnapshotWS[GameState]]
    .initialStateFromProps { p =>
      p._1.value.team.map(_.team_code) getOrElse ""
    }
    .renderBackend[Backend]
    .build
}