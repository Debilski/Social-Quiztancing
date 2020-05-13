package scalajsreact.template.components.items

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import scalajsreact.template.models.State.{AnswerSet, SelectedAnswerSet, Question, GameState, Team}
import scalajsreact.template.models.types.{StateSnapshotWS, WS}

import scalacss.ScalaCssReact._
import scalacss.DevDefaults._
import org.scalajs.dom.ext.KeyCode

object QuestionListC {}

object Question_ {
  val Component = ScalaComponent
    .builder[WS[(Team, Question, AnswerSet)]]
    .render_P {
      case ((t, q, a), wsSnapshot, sendMessage) =>
        if (q.is_active)
          <.div(
            ^.id := s"qid-${q.idx}",
            <.header(
              <.p(
                ^.`class` := "lead",
                s"Question ${q.idx}",
                ^.textTransform := "uppercase",
                ^.letterSpacing := 2.px,
                VdomStyle("marginBlockEnd") := 0.px
              ),
              <.h2(q.title, VdomStyle("marginBlockStart") := 0.px)
            ),
            Answers.Component(((t, q, a), wsSnapshot, sendMessage))
          )
        else
          <.div()
    }
    .build
}

object AdminQuestion_ {
  type Props = WS[(Team, Question, SelectedAnswerSet)]
  case class State(questionText: String, editMode: Boolean)

  class Backend($ : BackendScope[Props, State]) {

    def render(p: Props, s: State) = {
      val ((t, q, a), wsSnapshot, sendMessage) = p

      def editButton =
        <.button(
          "Edit",
          ^.onClick --> $.modState(_.copy(editMode = true))
        ).when(!s.editMode)

      def publishButton = {
        if (!q.is_active)
          <.button(
            "Publish",
            ^.onClick --> publishQuestion
          )
        else
          <.button(
            "Unpublish",
            ^.onClick --> unPublishQuestion
          )
      }.unless(s.editMode)

      def updateQuestion(questionText: String): Callback = {
        val json_data = Json.obj(
          "action" -> Json.fromString("update_question"),
          "question_uuid" -> Json.fromString(q.question_uuid.toString),
          "question_text" -> Json.fromString(questionText)
        )
        sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
      }

      def publishQuestion: Callback = {
        val json_data = Json.obj(
          "action" -> Json.fromString("publish_question"),
          "question_uuid" -> Json.fromString(q.question_uuid.toString)
        )
        sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
      }

      def unPublishQuestion: Callback = {
        val json_data = Json.obj(
          "action" -> Json.fromString("unpublish_question"),
          "question_uuid" -> Json.fromString(q.question_uuid.toString)
        )
        sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
      }

      def handleUpdate(e: ReactEventFromInput) =
        e.preventDefaultCB >>
          updateQuestion(s.questionText) >>
          $.modState(_.copy(editMode = false))

      def cancelEdit: Callback = $.setState(State(q.title, editMode = false))

      def handleKey(e: ReactKeyboardEvent): Callback = {
        CallbackOption.keyCodeSwitch(e) {
          case KeyCode.Escape => cancelEdit
        } >> e.preventDefaultCB
      }

      def onChange(e: ReactEventFromInput) = {
        val newValue = e.target.value
        $.modState(_.copy(questionText = newValue))
      }

      def questionOrEdit =
        if (s.editMode) {
          <.form(
            ^.onSubmit ==> handleUpdate,
            <.input.text(
              ^.value := s.questionText,
              ^.onKeyDown ==> handleKey,
              ^.onChange ==> onChange,
              ^.autoFocus := true
            ),
            <.button("Update"),
            <.button(
              "Cancel",
              ^.onClick --> cancelEdit
            )
          )
        } else {
          <.h2(q.title, ^.display.inline)
        }

      def questionHeader = {
        <.div(
          questionOrEdit,
          " ".unless(q.is_active),
          <.i("(inactive)").unless(q.is_active),
          " ".unless(q.is_active),
          editButton,
          publishButton,
          VdomStyle("marginBlockStart") := 0.px
        )
      }

      <.div(
        ^.id := s"qid-${q.idx}",
        <.header(
          <.p(
            ^.`class` := "lead",
            s"Question ${q.idx}",
            ^.textTransform := "uppercase",
            ^.letterSpacing := 2.px,
            VdomStyle("marginBlockEnd") := 0.px
          ),
          questionHeader
        ),
        //Answers.Component(((t, q, a), wsSnapshot, sendMessage))
        a.map{ case (k, v) =>
          <.p(k, ": ", v.answer)
    }.toVdomArray
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("Question")
    .initialStateFromProps {
      case ((t, q, a), wsSnapshot, sendMessage) =>
        State(q.title, editMode = false)
    }
    .renderBackend[Backend]
    .build

}

object QuestionList_ {

  object Style extends StyleSheet.Inline {
    import dsl._
    val borderStyle = style(
      padding(1.em),
      borderImage := "0 0 0 9 repeating-linear-gradient(30deg, red 0, red 2em, transparent 0, transparent 2em, transparent 0, transparent 4em)"
    )

  }
  Style.addToDocument()

  val Component = ScalaComponent
    .builder[StateSnapshotWS[GameState]]("QuestionList")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        val wsSnapshot = stateSnapshot.zoomState(_.ws)(w => _.copy(ws = w))
        val answers = stateSnapshot.value.answers
        val selectedAnswers = stateSnapshot.value.selected_answers

        <.div(
          Style.borderStyle.when(stateSnapshot.value.adminMode),
          stateSnapshot.value.team map { team =>
            stateSnapshot.value.questions.questions.zipWithIndex.map {
              case (q, idx) =>
                if (stateSnapshot.value.adminMode) {
                  AdminQuestion_.Component.withKey(s"qid-${idx}")((
                    (team, q, selectedAnswers.getOrElse(q.question_uuid, Map.empty)),
                    wsSnapshot,
                    sendMessage
                  ))
                }
                 else {
                   Question_.Component.withKey(s"qid-${idx}")((
                    (team, q, answers.getOrElse(q.question_uuid, Vector.empty)),
                    wsSnapshot,
                    sendMessage
                  ))
                 }
            }.toVdomArray
          }
        )
    }
    .build
}
