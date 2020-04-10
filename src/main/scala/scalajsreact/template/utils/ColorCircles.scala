package scalajsreact.template.utils

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._

import japgolly.scalajs.react.extra.StateSnapshot

object ColorCircles {

  class SwatchBackend(
      $ : BackendScope[(String, String => Callback, Boolean), Unit]
  ) {

    def render(value: (String, String => Callback, Boolean)) = value match {
      case (color, onClick, active) =>
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
          ^.boxShadow := f"inset 0 0 0 ${if (active) "3" else "14"}px ${color}",
          ^.transition := "100ms box-shadow ease",
          ^.color := color, //$.props
          ^.onClick --> onClick(color)
        )
    }
  }

  val Swatch = ScalaComponent
    .builder[(String, String => Callback, Boolean)]("Swatch")
    .initialState()
    .renderBackend[SwatchBackend]
    .build

  import scalacss.ScalaCssReact._
  import scalacss.DevDefaults._

  class CircleSwatchBackend(
      $ : BackendScope[(String, String => Callback, Boolean), Unit]
  ) {

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

    def render(value: (String, String => Callback, Boolean)) = value match {
      case (color, onClick, active) =>
        <.div(
          ^.width := 28.px,
          ^.height := 28.px,
          ^.marginRight := 14.px,
          ^.marginBottom := 14.px,
          Styles.hoveringStyle,
          Swatch((color, onClick, active))
        )
    }
  }

  val CircleSwatch = ScalaComponent
    .builder[(String, String => Callback, Boolean)]("CircleSwatch")
    .initialState()
    .renderBackend[CircleSwatchBackend]
    .build

  case class Props(
      width: Int,
      onChange: String => Callback,
      colors: Seq[String],
      circleSize: Int,
      circleSpacing: Int,
      initial: StateSnapshot[String]
  )

  class ColorCircleBackend($ : BackendScope[Props, Unit]) {

    def render(props: Props) = {
      val colorSnapshot = props.initial
      val handleChange = (hexCode: String) => {
        colorSnapshot.setState(hexCode) >> props.onChange(hexCode)
      }

      <.div(
        ^.width := "400px", //$.props.map(_.width.toString()),
        ^.display := "flex",
        ^.flexWrap := "wrap",
        ^.marginRight := -14.px, //-$.props.map(_.circleSpacing),
        ^.marginBottom := -14.px, //-$.props.map(_.circleSpacing),
        props.colors.toVdomArray { c =>
          CircleSwatch((c, handleChange, c == colorSnapshot.value))
        }
      )
    }
  }

  val ColorCircle = ScalaComponent
    .builder[Props]("ColorCircle")
    .initialState()
    .renderBackend[ColorCircleBackend]
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
