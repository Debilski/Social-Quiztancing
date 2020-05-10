package scalajsreact.template.components.items

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import scalajsreact.template.models.State.{AnswerSet, Answer, Question, Team}
import scalajsreact.template.models.types.WS
import scalajsreact.template.utils.ReactAddons

import scalacss.ScalaCssReact._
import scalacss.DevDefaults._
import org.scalajs.dom.raw.WebSocket

import japgolly.scalajs.react.extra.ReusabilityOverlay

object Answers {
  type Props = WS[(Team, Question, AnswerSet)]


  object Style extends StyleSheet.Inline {
    import dsl._
    val voteButton = style(
      borderRadius(50.%%),
      border := none,
      padding := "0 0.4rem",
      margin(0.1.rem),
      fontSize(0.813.rem),
      width(1.75.rem),
      height(1.75.rem),
      lineHeight(1.75.rem),
      color(rgba(0, 0, 0, .87)),
      backgroundColor(Color("#FFF")),
      boxShadow := "0 0 2px rgba(0,0,0,.12),0 2px 2px rgba(0,0,0,.2)",
      &.active(
        boxShadow := "0 0 4px rgba(0,0,0,.12),1px 3px 4px rgba(0,0,0,.2)"
      ),
      &.disabled(
        opacity(0.6),
        boxShadow := none
      )
    )

    val answerContainer = style(
      display.flex,
      flexWrap.wrap,
      flexDirection.row,
      borderRadius(6.px),
      border := "1px solid black",
      padding := 10.px
    )
  }
  Style.addToDocument()

  class Backend($ : BackendScope[Props, Unit]) {

    def render(p: Props) = p match {

      case ((t, q, a), wsSnapshot, sendMessage) =>
        val voteAnswer: (java.util.UUID) => Callback = { aid =>
          val json_data = Json.obj(
            "action" -> Json.fromString("vote_answer"),
            "answer_uuid" -> Json.fromString(aid.toString)
          )
          sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
        }
        val unVoteAnswer: (java.util.UUID) => Callback = { aid =>
          val json_data = Json.obj(
            "action" -> Json.fromString("unvote_answer"),
            "answer_uuid" -> Json.fromString(aid.toString)
          )
          sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
        }
        val selectAnswer: (java.util.UUID) => Callback = { aid =>
          val json_data = Json.obj(
            "action" -> Json.fromString("select_answer"),
            "answer_uuid" -> Json.fromString(aid.toString)
          )
          sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
        }
        val unSelectAnswer: (java.util.UUID) => Callback = { aid =>
          val json_data = Json.obj(
            "action" -> Json.fromString("unselect_answer"),
            "answer_uuid" -> Json.fromString(aid.toString)
          )
          sendMessage(wsSnapshot.value.get, json_data.asJson.noSpaces)
        }

        <.div(
          Style.answerContainer,
          <.div(
            ^.padding := 5.px,
            ^.minWidth := 300.px,
            AnswerInput.Component(((q, a), wsSnapshot, sendMessage))
          ),
          <.div(
            ^.`class` := "team-answers",
            ^.padding := 5.px,
            ^.minWidth := 300.px,
            ReactAddons.FlipMove(
              a
              // remove empty, unvoted answers from list
                .filterNot(ans =>
                  ans.answer_text.isEmpty && ans.votes.size == 0
                )
                .toVdomArray(ans => AnswerComponent.withKey(ans.answer_uuid.toString)(t, ans, voteAnswer, unVoteAnswer, selectAnswer, unSelectAnswer))
            )
          )
        )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("Answers")
    .renderBackend[Backend]
    .build


    // TODO: If updates are a problem, remove the always here
  implicit val uuidCBReuse = Reusability.always[(java.util.UUID) => Callback]
  implicit val teamReuse = Reusability.derive[Team]
  implicit val qReuse = Reusability.derive[Question]
  implicit val ansReuse = Reusability.derive[Answer]
  implicit val answerComponentReuse = Reusability.derive[(Team, Answer, (java.util.UUID) => Callback, (java.util.UUID) => Callback, (java.util.UUID) => Callback, (java.util.UUID) => Callback)]


  val AnswerComponent = ScalaComponent
    .builder[
      (Team, Answer, (java.util.UUID) => Callback, (java.util.UUID) => Callback, (java.util.UUID) => Callback, (java.util.UUID) => Callback)
    ]("Answer")
    .render_P {
      case (t, i, voteAnswer, unVoteAnswer, selectAnswer, unSelectAnswer) =>
        val member = t.members.find(m => m.player_id == i.player_id)
        val playerClass = member.flatMap(m =>
          QuizData
            .playerFromColor(m.player_color)
            .map(idx => s"player-$idx")
        )
        val playerName = member.map(_.player_name)
        val selfId = t.self_id
        val canUpvote = !(i.votes contains selfId)
        val canDownvote = (i.votes contains selfId)
        <.div(
          ^.`class` :=? playerClass,
          ^.padding := 5.px,
          <.div(
            ^.`class` := "answer-inner",
            i.answer_text,
            ^.title :=? playerName
          ),
          <.div(
            ^.display := "flex",
            <.input.radio(
              ^.checked := i.is_selected,
              ^.onChange --> {if (i.is_selected) unSelectAnswer(i.answer_uuid) else selectAnswer(i.answer_uuid)}
            ),
            <.button(
              "+",
              Style.voteButton,
              ^.disabled := !canUpvote,
              ^.onClick -->? Option
                .when(canUpvote)(voteAnswer(i.answer_uuid))
            ),
            <.button(
              "-",
              Style.voteButton,
              ^.disabled := !canDownvote,
              ^.onClick -->? Option
                .when(canDownvote)(unVoteAnswer(i.answer_uuid))
            ),
            i.votes.toTagMod { ii =>
              val member = t.members.find(m => m.player_id == ii)
              val text: String =
                member.map(_.player_name).getOrElse("???")
              val playerClass = member.flatMap(m =>
                QuizData
                  .playerFromColor(m.player_color)
                  .map(idx => s"player-$idx")
              )
              <.div(
                <.div(
                  ^.`class` := "colored-dot-inner",
                  ^.width := "100%",
                  ^.height := "100%",
                  ^.position := "relative",
                  ^.outline := "none",
                  ^.borderRadius := "50%",
                  ^.background := "transparent"
                ),
                ^.`class` := s"colored-dot ${playerClass.getOrElse("")}",
                ^.title := text,
                ^.width := 14.px,
                ^.height := 14.px,
                ^.marginTop := 3.px,
                ^.marginLeft := 3.px,
                ^.marginRight := 4.px,
                ^.marginBottom := 7.px
              )
            }
          )
        )

    }
    .configure(Reusability.shouldComponentUpdate)
//    .configure(ReusabilityOverlay.install)
    .build

}
