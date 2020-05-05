// Copyright (c) 2016-2020 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model

import cats.{ Eq, Show }
import monocle.macros.Lenses
import monocle.Iso
import monocle.macros.GenIso

@Lenses
final case class Operator(value: String)

object Operator {

  val Zero: Operator =
    Operator("")

  implicit val equal: Eq[Operator] =
    Eq.fromUniversalEquals

  implicit val shows: Show[Operator] =
    Show.show(_.value)

  val valueI: Iso[Operator, String] = GenIso[Operator, String]

}
