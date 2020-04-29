package scalajsreact.template.components.items

import io.circe.Json
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import scalajsreact.template.models.State._
import scalajsreact.template.models.types._
import scalajsreact.template.utils.Debounce

object AnswerInput {
  val Component = ScalaComponent
    .builder[WS[(Question, AnswerSet)]]("AnswerInput")
    .render_P {
      case ((q, a), wsSnapshot, sendMessage) =>
        //     def onChange(e: ReactEventFromInput): Callback = {
        //        val newMessage = e.target.value
        //        val newTeamC = $.state.map(_.team).map(_.copy(team_id = newMessage))
        //        newTeamC >>= (nt => $.modState(_.copy(team = nt)))
        //      }

        //      // Can only send if WebSocket is connected and user has entered text
        //      val send: Option[Callback] =
        //        for {
        //          ws <- s.ws if s.allowSend
        //          val json_data = Json.obj("action" -> Json.fromString("load_game"), "game_id" -> Json.fromString(s.team.team_id))
        //        } yield b.sendMessage(ws, json_data.asJson.noSpaces)

        //      def sendOnEnter(e: ReactKeyboardEvent): Callback =
        //        CallbackOption
        //          .keyCodeSwitch(e) {
        //            case KeyCode.Enter => send.getOrEmpty
        //          }
        //          .asEventDefault(e)

        def changedAnswer(e: ReactEventFromInput) =
          e.extract(_.target.value)(value => {
            //       $.state.map{ s =>
            //         for {
            //           ws_ <- s.ws
            //         } ws_.send(ReqInfo.asJson.noSpaces)
            //
            ///       } >>
            print(value)
            //$.modState(_.copy(filterText = value))
          })

        val delayedOnAnswerChange: ((java.util.UUID, String)) => Callback =
          Debounce.callback() {
            case (qid, value) =>
              val json_data = Json.obj(
                "action" -> Json.fromString("update_answer"),
                "question_uuid" -> Json.fromString(qid.toString),
                "answer" -> Json.fromString(value)
              )
              sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
          }

        def onAnswerChange(q: java.util.UUID)(e: ReactEventFromInput) =
          e.extract(_.target.value)(value => {
            //       $.state.map{ s =>
            //         for {
            //           ws_ <- s.ws
            //         } ws_.send(ReqInfo.asJson.noSpaces)
            //
            ///       } >>
            delayedOnAnswerChange(q, value)
          })

        //  val onAnswerChange: (ReactEventFromInput) => Callback = Debounce.callback() { case e =>

        <.form(
          ^.`class` := "answer-input",
          <.input.text(
            ^.placeholder := "Answer ...",
            //            ^.value := s.filterText,
            ^.onChange ==> onAnswerChange(q.question_uuid)
          )
        )
    }
    .build
}
