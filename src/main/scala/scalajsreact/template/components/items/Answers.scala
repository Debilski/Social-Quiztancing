package scalajsreact.template.components.items

import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import japgolly.scalajs.react.{Callback, ScalaComponent}
import japgolly.scalajs.react.vdom.html_<^._
import scalajsreact.template.models.State.{AnswerSet, Question, Team}
import scalajsreact.template.models.types.WS
import scalajsreact.template.utils.ReactAddons

object Answers {
  val Component = ScalaComponent
    .builder[WS[(Team, Question, AnswerSet)]]("Answers")
    .render_P {
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

        <.div(
          ^.display := "flex",
          ^.borderRadius := 5.px,
          ^.border := "1px solid black",
          ^.padding := 5.px,
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
              .filterNot(ans => ans.answer_text.isEmpty && ans.votes.size == 0)
              .toVdomArray { i =>
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
                  ^.key := i.answer_uuid.toString,
                  ^.`class` :=? playerClass,
                  ^.padding := 5.px,
                  <.div(
                    ^.`class` := "answer-inner",
                    i.answer_text,
                    ^.title :=? playerName
                  ),
                  <.div(
                    ^.display := "flex",
                    <.button(
                      "+",
                      ^.disabled := !canUpvote,
                      ^.onClick -->? Option
                        .when(canUpvote)(voteAnswer(i.answer_uuid))
                    ),
                    <.button(
                      "-",
                      ^.disabled := !canDownvote,
                      ^.onClick -->? Option
                        .when(canDownvote)(unVoteAnswer(i.answer_uuid))
                    ),
                    i.votes.toTagMod {
                      ii =>
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
            )
          )
        )
    }
    .build
}