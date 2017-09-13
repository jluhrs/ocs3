// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package horizons

import gem.math.Ephemeris
import gem.util.InstantMicros

import atto.ParseResult
import atto.syntax.parser._

import cats.implicits._

import fs2.Pipe


/** Horizons ephemeris parser.  Parses horizons output generated with the flags
  *
  *   `QUANTITIES=1; time digits=FRACSEC; extra precision=YES`
  *
  * into an `Ephemeris` object, or a `Stream[Ephemeris.Element]`.
  */
object EphemerisParser {

  private object impl {

    import atto._
    import Atto._
    import cats.implicits._
    import gem.parser.CoordinateParsers._
    import gem.parser.MiscParsers._
    import gem.parser.TimeParsers._

    val SOE           = "$$SOE"
    val EOE           = "$$EOE"

    val soe           = string(SOE)
    val eoe           = string(EOE)
    val skipPrefix    = manyUntil(anyChar, soe)  ~> verticalWhitespace
    val skipEol       = skipMany(noneOf("\n\r")) ~> verticalWhitespace
    val solarPresence = oneOf("*CNA ").void namedOpaque "solarPresence"
    val lunarPresence = oneOf("mrts ").void namedOpaque "lunarPresence"

    val utc: Parser[InstantMicros] =
      instantUTC(
        genYMD(monthMMM, hyphen) named "yyyy-MMM-dd",
        genLocalTime(colon)
      ).map(InstantMicros.truncate)

    val element: Parser[Ephemeris.Element] =
      for {
        _ <- space
        i <- utc           <~ space
        _ <- solarPresence
        _ <- lunarPresence <~ spaces1
        c <- coordinates
      } yield (i, c)

    val elementLine: Parser[Ephemeris.Element] =
      element <~ skipEol

    val ephemeris: Parser[Ephemeris] =
      skipPrefix ~> (
        many(elementLine).map(Ephemeris.fromFoldable[List]) <~ eoe
      )
  }

  import impl.{ element, ephemeris, SOE, EOE }

  /** Parses an ephemeris file into an `Ephemeris` object in memory.
    *
    * @param s string containing the ephemeris data from horizons
    *
    * @return result of parsing the string into an `Ephemeris` object
    */
  def parse(s: String): ParseResult[Ephemeris] =
    ephemeris.parseOnly(s)

  /** An `fs2.Pipe` that converts a `Stream[F, String]` of ephemeris data from
    * horizons into a `Stream[F, Ephemeris.Element]`.
    *
    * @tparam F effect to use
    *
    * @return pipe for a `Stream[F, String]` into a `Stream[F, Ephemeris.Element]`
    */
  def elements[F[_]]: Pipe[F, String, Ephemeris.Element] =
    _.through(fs2.text.lines)
     .dropThrough(_.trim =!= SOE)
     .takeWhile(_.trim =!= EOE)
     .map { s => element.parseOnly(s).either.left.map(new RuntimeException(_)) }
     .rethrow

}
