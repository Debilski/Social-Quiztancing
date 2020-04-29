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

object PlayerId {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("PlayerId")
    .initialState("")
    .renderPS {
      case (bs, (stateSnapshot, sendMessage), s) =>

        def onChange(e: ReactEventFromInput): Callback = {
          val newId = e.target.value
          bs.setState(newId)
        }

        // Can only send if WebSocket is connected and user has entered text that is a UUID
        val send: Option[Callback] =
          if (s.isEmpty())
            None
          else {
            try {
              val uuid = java.util.UUID.fromString(s)
              Some(Callback(stateSnapshot.value.initWS(Some(bs.state))) >> bs.setState(""))
            } catch {
              case _: java.lang.IllegalArgumentException => None
            }
          }

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
            ^.placeholder := "Player ID",
            ^.value := bs.state,
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
