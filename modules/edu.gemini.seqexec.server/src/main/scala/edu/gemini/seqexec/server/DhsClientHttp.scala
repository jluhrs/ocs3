// Copyright (c) 2016-2017 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package edu.gemini.seqexec.server

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicInteger

import argonaut._
import Argonaut._
import edu.gemini.seqexec.model.dhs.ImageFileId
import edu.gemini.seqexec.server.DhsClient.{ImageParameters, KeywordBag}
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.{EntityEnclosingMethod, PostMethod, PutMethod}

import scala.io.Source
import scalaz.concurrent.Task
import scalaz.EitherT
import scalaz._

/**
  * Implementation of DhsClient that interfaces with the real DHS over the http interface
  */
class DhsClientHttp(val baseURI: String) extends DhsClient {
  import DhsClientHttp._

  // Connection timeout, im milliseconds
  val timeout = 10000

  implicit def errorDecode: DecodeJson[Error] = DecodeJson[Error]( c => for {
      t   <- (c --\ "type").as[ErrorType]
      msg <- (c --\ "message").as[String]
    } yield Error(t, msg)
  )

  implicit def obsIdDecode: DecodeJson[TrySeq[ImageFileId]] = DecodeJson[TrySeq[ImageFileId]](c => {
    val r = c --\ "response"
    val s = (r --\ "status").as[String]
    s.flatMap {
      case "success" =>
        (r --\ "result").as[String].map(TrySeq(_))
      case "error"   =>
        (r --\ "errors").as[List[Error]].map(l =>
          TrySeq.fail[ImageFileId](SeqexecFailure.Unexpected(l.mkString(", ")))
        )
      case r         =>
        DecodeResult.fail(s"Unknown response: $r", s.history.getOrElse(CursorHistory.empty))
    }
  } )

  implicit def unitDecode: DecodeJson[TrySeq[Unit]] = DecodeJson[TrySeq[Unit]]( c => {
    val r = c --\ "response"
    val s = (r --\ "status").as[String]
    s flatMap {
      case "success" =>
        DecodeResult.ok(TrySeq(()))
      case "error"   =>
        (r --\ "errors").as[List[Error]].map(
          l => TrySeq.fail[Unit](SeqexecFailure.Unexpected(l.mkString(", "))))
      case r         =>
        DecodeResult.fail(s"Unknown response: $r", s.history.getOrElse(CursorHistory.empty))
    }
  } )

  implicit def imageParametersEncode: EncodeJson[DhsClient.ImageParameters] = EncodeJson[DhsClient.ImageParameters]( p =>
    ("lifetime" := p.lifetime.str) ->: ("contributors" := p.contributors) ->: Json.jEmptyObject )

  implicit def keywordEncode: EncodeJson[DhsClient.InternalKeyword] = EncodeJson[DhsClient.InternalKeyword]( k =>
    ("name" := k.name) ->: ("type" := k.keywordType.str) ->: ("value" := k.value) ->: Json.jEmptyObject )

  private def sendRequest[T](method: EntityEnclosingMethod, body: Json, errMsg: String)(implicit decoder: argonaut.DecodeJson[TrySeq[T]]): SeqAction[T] = EitherT ( Task.delay {
      val client = new HttpClient()

      client.setConnectionTimeout(timeout)

      method.addRequestHeader("Content-Type", "application/json")
      method.setRequestBody(body.nospaces)

      client.executeMethod(method)

      val r = Source.fromInputStream(method.getResponseBodyAsStream).getLines().mkString.decodeOption[TrySeq[T]](decoder)

      method.releaseConnection()

      r.getOrElse(TrySeq.fail[T](SeqexecFailure.Execution(errMsg)))
    } )

  private def createImage(reqBody: Json): SeqAction[ImageFileId] =
    sendRequest[ImageFileId](new PostMethod(baseURI), Json.jSingleObject("createImage", reqBody), "Unable to get label")

  def createImage: SeqAction[ImageFileId] = createImage(Json.jEmptyObject)

  override def createImage(p: ImageParameters): SeqAction[ImageFileId] = createImage(p.asJson)

  def setParameters(id: ImageFileId, p: ImageParameters): SeqAction[Unit] =
    sendRequest[Unit](new PutMethod(baseURI + "/" + id), Json.jSingleObject("setParameters", p.asJson), "Unable to set parameters for image " + id)

  override def setKeywords(id: ImageFileId, keywords: KeywordBag, finalFlag: Boolean = false): SeqAction[Unit] =
    sendRequest[Unit](new PutMethod(baseURI + "/" + id + "/keywords"),
      Json.jSingleObject("setKeywords", ("final" := finalFlag) ->: ("keywords" := keywords.keywords) ->: Json.jEmptyObject ),
      "Unable to write keywords for image " + id)
}

object DhsClientHttp {

  sealed case class ErrorType(str: String)
  object BadRequest extends ErrorType("BAD_REQUEST")
  object DhsError extends ErrorType("DHS_ERROR")
  object InternalServerError extends ErrorType("INTERNAL_SERVER_ERROR")

  implicit def errorTypeDecode: DecodeJson[ErrorType] = DecodeJson[ErrorType]( c =>  c.as[String].map {
      case BadRequest.str          => BadRequest
      case DhsError.str            => DhsError
      case InternalServerError.str => InternalServerError
      case _                       => InternalServerError
    }
  )

  final case class Error(t: ErrorType, msg: String) {
    override def toString = s"(${t.str}) $msg"
  }

  def apply(uri: String): DhsClient = new DhsClientHttp(uri)
}

/**
  * Implementation of the Dhs client that simulates a dhs without external dependencies
  */
class DhsClientSim(date: LocalDate) extends DhsClient {
  private val counter = new AtomicInteger(0)

  val format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

  override def createImage(p: ImageParameters): SeqAction[ImageFileId] =
    EitherT(Task.delay{
      TrySeq(f"S${date.format(format)}S${counter.incrementAndGet()}%04d")
    })

  override def setKeywords(id: ImageFileId, keywords: KeywordBag, finalFlag: Boolean): SeqAction[Unit] = SeqAction(())

}

object DhsClientSim {
  def apply(date: LocalDate): DhsClient = new DhsClientSim(date)
}