package scalajsreact.template.components.items

import japgolly.scalajs.react.ScalaComponent
import japgolly.scalajs.react.vdom.html_<^._
import scalajsreact.template.models.State.{AnswerSet, Question, State, Team}
import scalajsreact.template.models.types.{StateSnapshotWS, WS}

object QuestionListC {

}

object Question_ {
  val Component = ScalaComponent
    .builder[WS[(Team, Question, AnswerSet)]]("Question")
    .render_P {
      case ((t, q, a), wsSnapshot, sendMessage) =>
        <.div(
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
    }
    .build
}

object QuestionList_ {
  val Component = ScalaComponent
    .builder[StateSnapshotWS[State]]("QuestionList")
    .render_P {
      case (stateSnapshot, sendMessage) =>
        val wsSnapshot = stateSnapshot.zoomState(_.ws)(w => _.copy(ws = w))
        val answers = stateSnapshot.value.answers
        stateSnapshot.value.team map { team =>
          stateSnapshot.value.questions.questions.zipWithIndex.map {
            case (q, idx) =>
              Question_.Component.withKey(s"qid-${idx}")(
                ((team, q, answers.getOrElse(q.question_uuid, Vector.empty)), wsSnapshot, sendMessage)
              )
          }.toVdomArray
        }
    }
    .build
}
