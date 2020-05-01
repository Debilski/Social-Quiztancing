package scalajsreact.template.components.items

import japgolly.scalajs.react.{BackendScope, Callback, CallbackOption, ReactEventFromInput, ReactKeyboardEvent, ReactMouseEvent, ScalaComponent}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.ext.KeyCode
import scalajsreact.template.models.State.{State, Team, TeamMember}
import scalajsreact.template.models.types.StateSnapshotWS

object TeamId {
  class Backend($ : BackendScope[StateSnapshotWS[State], String]) {
    def render(p: StateSnapshotWS[State], s: String): VdomNode = p match {
      case (stateSnapshot, sendMessage) =>
        def onChange(e: ReactEventFromInput): Callback = {
          val newId = e.target.value
          $.setState(newId)
        }

        // Can only send if WebSocket is connected and user has entered text
        val send: Option[Callback] =
          if (s.isEmpty())
            None
          else
            Some($.state map (id => stateSnapshot.value.joinTeam(id)))

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
              ^.autoFocus := true,
              ^.placeholder := "Team ID",
              ^.value := s,
              ^.onChange ==> onChange,
              ^.onKeyDown ==> sendOnEnter
            ),
            <.button(
              ^.disabled := send.isEmpty, // Disable button if unable to send
              ^.onClick ==>? sendOnClick,
              "Send"
            )
          ),
          stateSnapshot.value.team match {
            case Some(Team(team_name, team_code, members, self_id)) =>
              <.p(
                s"Joined team: $team_code with members: ", {
                  def styledMember(member: TeamMember) =
                    <.span(
                      member.player_name,
                      ^.borderBottom := s"1px double ${member.player_color}"
                    )

                  members.to(Seq).sorted.map(styledMember).mkTagMod(", ")
                },
                "."
              )
            case _ => <.p(<.i("Enter a code to join a team."))
          }
        )
    }
  }

  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("TeamId")
    .initialStateFromProps { p =>
      p._1.value.team.map(_.team_code) getOrElse ""
    }
    .renderBackend[Backend]
    .build
}