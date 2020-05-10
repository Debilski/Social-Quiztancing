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
              val withDashes = if ((s: String).contains("-")) {
                s
              } else {
                List(s.substring(0,8),
                s.substring(8,12),
                s.substring(12, 16),
                s.substring(16, 20),
                s.substring(20)).mkString("-")
              }
              val uuid = java.util.UUID.fromString(withDashes)
              Some(
                Callback(stateSnapshot.value.initWS(Some(bs.state))) >> bs
                  .setState("")
              )
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

        <.div(
          <.p(
          s"Player id: ${stateSnapshot.value.player.player_uuid}",
          <.br,
          <.small(<.i("Only change this, when you know what you’re doing."))
          ),
          <.form(
            <.input.text(
              ^.display := "inline",
              ^.autoFocus := true,
              ^.placeholder := "Player ID",
              ^.value := bs.state,
              ^.onChange ==> onChange,
              ^.onKeyDown ==> sendOnEnter
            ),
            <.button(
              ^.disabled := send.isEmpty, // Disable button if unable to send
              ^.onClick ==>? sendOnClick,
              "Change Player ID"
            )
          )
        )
    }
    .build
}
