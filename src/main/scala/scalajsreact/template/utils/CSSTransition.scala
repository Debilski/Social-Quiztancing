package scalajsreact.template.utils

object rawjs {
  import scala.scalajs.js
  import scala.scalajs.js.annotation._
  @JSImport("react-flip-move", JSImport.Namespace, "FlipMove")
  @js.native
  object FlipMove extends js.Object
}

object ReactAddons {
  import japgolly.scalajs.react._

  lazy val FlipMove =
    JsComponent[Null, Children.Varargs, Null](rawjs.FlipMove)
}
