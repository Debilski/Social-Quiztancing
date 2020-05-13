package scalajsreact.template.components.items

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalajsreact.template.models.State.State
import scalajsreact.template.models.types.StateSnapshotWS

object PlayerName {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]
    .initialState("")
    .renderPS {
      case (bs, (stateSnapshot, sendMessage), s) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newId = e.target.value
          bs.setState(newId)
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          if (s.isEmpty())
            None
          else
            Some(
              Callback(stateSnapshot.value.setName(bs.state)) >> bs.setState("")
            )

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
            ^.display := "inline",
            ^.autoFocus := true,
            ^.placeholder := "Player Name",
            ^.value := bs.state,
            ^.onChange ==> onChange,
            ^.onKeyDown ==> sendOnEnter
          ),
          <.button(
            ^.disabled := send.isEmpty, // Disable button if unable to send
            ^.onClick ==>? sendOnClick,
            "Change name"
          )
        )

    }
    .build
}
