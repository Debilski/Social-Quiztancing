package scalajsreact.template.css

import scalacss.DevDefaults._

object GlobalStyle extends StyleSheet.Inline {

  import dsl._

  style(/*
    unsafeRoot("body")(
      margin.`0`,
      padding.`0`,
      fontSize(14.px),
      fontFamily := """-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"Helvetica Neue",Arial,"Noto Sans",sans-serif,"Apple Color Emoji","Segoe UI Emoji","Segoe UI Symbol","Noto Color Emoji""""
    ),
    unsafeRoot("pre p")(
      margin.`0`
    ),
    unsafeRoot("h1, h2, h3, h4, h5, h6")(
      marginBottom(0.5.rem),
      fontWeight._500,
      lineHeight(1.2)
    ),
    unsafeRoot("h1")(
      fontSize(2.5.rem)
    ),
    unsafeRoot("h2")(
      fontSize(2.rem)
    ),
    unsafeRoot("h3")(
      fontSize(1.75.rem)
    ),
    unsafeRoot("h4")(
      fontSize(1.5.rem)
    ),
    unsafeRoot("h5")(
      fontSize(1.25.rem)
    ),
    unsafeRoot("h6")(
      fontSize(1.rem)
    ),*/
    unsafeRoot(".lead")(
      fontSize(1.rem),
      fontWeight._300
    ),
    unsafeRoot(".alert-warning")(
      color(Color("#856404")),
      backgroundColor(Color("#fff3cd")),
      borderColor(Color("#ffeeba"))
    ),
    unsafeRoot(".alert")(
      position := "relative",
      padding := ".75rem 1.25rem",
      marginBottom(1.rem),
      border := "1px solid transparent",
      borderRadius(.25.rem)
    )
  )
}
