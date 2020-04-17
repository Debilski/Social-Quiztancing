package scalajsreact.template.routes

import scalajsreact.template.components.items.{QuizData, ItemsInfo}
import scalajsreact.template.pages.ItemsPage

import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.extra.router.RouterConfigDsl
import japgolly.scalajs.react.vdom.VdomElement

sealed abstract class Item(val title: String,
                           val routerPath: String,
                           val render: (RouterCtl[Item]) => VdomElement)

object Item {

  case object Info extends Item("Info", "info", (_) => ItemsInfo())

  case class Quiz(game: Option[java.util.UUID]) extends Item("Quiz", "quiz", (ctl) => { implicit val a = ctl ; QuizData(QuizData.Props(game)) })

  val menu = Vector(Info, Quiz(None))

  val routes = RouterConfigDsl[Item].buildRule { dsl =>
    import dsl._

    staticRoute(Info.routerPath, Info) ~> renderR(
      r => ItemsPage(ItemsPage.Props(Info, r))
    ) |
    dynamicRouteCT(Quiz(None).routerPath  ~ ("/" ~ uuid).option.caseClass[Quiz]) ~> dynRenderR{
      case (page, r) => {
        ItemsPage(ItemsPage.Props(page, r))
      }
    }
  }
}
