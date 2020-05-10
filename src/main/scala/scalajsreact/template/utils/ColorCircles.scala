package scalajsreact.template.utils

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import japgolly.scalajs.react.extra.StateSnapshot
import scalacss.ScalaCssReact._
import scalacss.ProdDefaults._

object Swatch {

  final case class Props(
      color: String,
      onClick: String => Callback,
      circleSize: Int,
      active: Boolean
  ) {
    @inline def render: VdomElement = Component(this)
  }

  class Backend(
      $ : BackendScope[Props, Unit]
  ) {
    def render(value: Props) = value match {
      case Props(color, onClick, circleSize, active) =>
        <.div(
          ^.width := "100%",
          ^.height := "100%",
          ^.cursor := "pointer",
          ^.position := "relative",
          ^.outline := "none",
          ^.transition := "100ms transform ease",
          ^.background := color,
          ^.borderRadius := "50%",
          ^.background := "transparent",
          ^.boxShadow := f"inset 0 0 0 ${if (active) circleSize/5 else circleSize}px ${color}",
          ^.transition := "100ms box-shadow ease",
          ^.color := color, //$.props
          ^.onClick --> onClick(color)
        )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("Swatch")
    .renderBackend[Backend]
    .build
}

object CircleSwatch {

  final case class Props(
      color: String,
      onClick: String => Callback,
      circleSize: Int,
      circleSpacing: Int,
      active: Boolean
  )(key: Key) {
    @inline def render: VdomElement = Component.withKey(key)(this)
  }

  object Styles extends StyleSheet.Inline {
    import dsl._

    val hoveringStyle = style(
      transform := "scale(1)",
      transition := "100ms transform ease",
      &.hover(
        transform := "scale(1.2)"
      )
    )
  }
  Styles.addToDocument()

  class Backend(
      $ : BackendScope[Props, Unit]
  ) {
    def render(value: Props) = value match {
      case Props(color, onClick, circleSize, circleSpacing, active) =>
        <.div(
          ^.width := (2 * circleSize).px,
          ^.height := (2 * circleSize).px,
          ^.marginRight := circleSpacing.px,
          ^.marginBottom := circleSpacing.px,
          Styles.hoveringStyle,
          Swatch.Props(color, onClick, circleSize, active).render
        )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("CircleSwatch")
    .renderBackend[Backend]
    .build
}

object ColorCircle {

  final case class Props(
      width: Int,
      onChange: String => Callback,
      colors: Seq[String],
      circleSize: Int,
      circleSpacing: Int,
      initial: StateSnapshot[String]
  ) {
    @inline def render: VdomElement = Component(this)
  }

  final class Backend($ : BackendScope[Props, Unit]) {
    def render(props: Props) = {
      val colorSnapshot = props.initial
      val handleChange = (hexCode: String) => {
        colorSnapshot.setState(hexCode) >> props.onChange(hexCode)
      }

      <.div(
        ^.width := props.width.px,
        ^.display := "flex",
        ^.flexWrap := "wrap",
        ^.marginRight := (- props.circleSpacing).px,
        ^.marginBottom := (- props.circleSpacing).px,
        props.colors.toVdomArray { c =>
          CircleSwatch
            .Props(c, handleChange, circleSize=props.circleSize, circleSpacing=props.circleSpacing, c == colorSnapshot.value)(c)
            .render
        }
      )
    }
  }

  val Component = ScalaComponent
    .builder[Props]("ColorCircle")
    .renderBackend[Backend]
    .build

  val COLORS =
    "#F44336" ::
      "#E91E63" ::
      "#9C27B0" ::
      "#673AB7" ::
      "#3F51B5" ::
      "#2196F3" ::
      "#03A9F4" ::
      "#00BCD4" ::
      "#009688" ::
      "#4CAF50" ::
      "#8BC34A" ::
      "#CDDC39" ::
      "#FFEB3B" ::
      "#FFC107" ::
      "#FF9800" ::
      "#FF5722" ::
//      "#795548" ::
      "#9E9E9E" ::
      "#607D8B" :: Nil

}
