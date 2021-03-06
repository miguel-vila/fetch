package fetch.examples.page_rendering

import java.util.Date

import fetch.{ DataSource, BlockedRequest, Fetch }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scalaz.std.stream.streamInstance
import scalaz.syntax.applicative._

/**
 * Created by mglvl on 23/04/15.
 */
trait PageRenderingExample {

  /**
   * Tipos que representan las posibles respuestas de un servicio
   */
  type PostId = Int
  type PostViews = Int
  type PostDate = Date

  case class PostContent(content: String)

  case class PostInfo(
    postId: PostId,
    postDate: PostDate,
    postTopic: String)

  type PostIds = Stream[PostId]

  /**
   * Tipos que representan los requests que atienden los servicios
   */
  sealed trait ExampleRequest[T]

  case object GetPostIds extends ExampleRequest[PostIds]

  case class GetPostInfo(postId: PostId) extends ExampleRequest[PostInfo]

  case class GetPostContent(postId: PostId) extends ExampleRequest[PostContent]

  case class GetPostViews(postId: PostId) extends ExampleRequest[PostViews]

  /**
   * Valores/Funciones Fetch que representan los queries básicos
   * a partir de los cuales se realiza la composición para implementar
   * alguna lógica de negocio
   */

  val getPostIds = Fetch.dataFetch(GetPostIds)

  def getPostInfo(postId: PostId) = Fetch.dataFetch(GetPostInfo(postId))

  def getPostContent(postId: PostId) = Fetch.dataFetch(GetPostContent(postId))

  def getPostViews(postId: PostId) = Fetch.dataFetch(GetPostViews(postId))

  /**
   * Representa un documento HTML
   */
  trait HTML
  object HTML extends HTML

  /**
   * Funciones de "Renderización"
   */
  def renderPosts(content: Stream[(PostInfo, PostContent)]): HTML = HTML
  def renderPostList(content: Stream[(PostInfo, PostContent)]): HTML = HTML
  def renderTopics(topicsCount: Map[String, Int]): HTML = HTML
  def renderLeftPane(popularPostsPane: HTML, topicsPane: HTML): HTML = HTML
  def renderPage(leftPane: HTML, mainPane: HTML): HTML = HTML

  /**
   * Tipo auxiliar para simplificar firmas
   */
  type ExampleFetch[A] = Fetch[ExampleRequest, A]

  /**
   * Composición de servicios / Lógica de negocio
   */
  val getAllPostsInfo: ExampleFetch[Stream[PostInfo]] = getPostIds.flatMap { postIds =>
    Fetch.traverse(postIds)(getPostInfo)
  }

  val mainPane: ExampleFetch[HTML] = {
    for {
      posts <- getAllPostsInfo
      ordered = posts.sortBy(_.postDate).take(5)
      content <- Fetch.traverse(ordered)(p => getPostContent(p.postId))
      renderingContent = ordered zip content
    } yield renderPosts(renderingContent)
  }

  def getPostDetails(postId: PostId): ExampleFetch[(PostInfo, PostContent)] = {
    (getPostInfo(postId) |@| getPostContent(postId)) { (_, _) }
  }

  val popularPosts: ExampleFetch[HTML] = for {
    postIds <- getPostIds
    views <- Fetch.traverse(postIds)(getPostViews)
    ordered = (postIds zip views).sortBy(_._2).map(_._1).take(5)
    content <- Fetch.traverse(ordered)(getPostDetails)
  } yield renderPostList(content)

  val topics: ExampleFetch[HTML] = for {
    posts <- getAllPostsInfo
    topicCounts = posts.groupBy(_.postTopic).mapValues(_.size)
  } yield renderTopics(topicCounts)

  val leftPane: ExampleFetch[HTML] = (popularPosts |@| topics)(renderLeftPane)

  val pageHTML: ExampleFetch[HTML] = (leftPane |@| mainPane)(renderPage)

  /**
   * Datos de prueba
   */
  val postsIds = Stream(1, 2, 3, 4, 5, 6, 7)

  val postInfoData = Map(
    1 -> PostInfo(1, new Date(), "topic post 1"),
    2 -> PostInfo(2, new Date(), "topic post 2"),
    3 -> PostInfo(3, new Date(), "topic post 3"),
    4 -> PostInfo(4, new Date(), "topic post 4"),
    5 -> PostInfo(5, new Date(), "topic post 5"),
    6 -> PostInfo(6, new Date(), "topic post 6"),
    7 -> PostInfo(7, new Date(), "topic post 7"))

  val postContentData = postsIds.map { pid =>
    pid -> PostContent(s"content for post $pid")
  }.toMap

  val postViewsData = Map(
    1 -> 10,
    2 -> 20,
    3 -> 30,
    4 -> 40,
    5 -> 50,
    6 -> 60,
    7 -> 70)

  type BlockedExampleRequest[A] = BlockedRequest[ExampleRequest, A]

  /**
   * Datasource que utiliza la anterior funcion para ejecutar de forma independiente cada servicio.
   */
  implicit object ExampleDataSource extends DataSource[ExampleRequest] {

    /**
     * Implementaciones de los servicios base
     */
    def getPostIdsImpl(): Future[PostIds] = {
      Future.successful(postsIds)
    }

    def getPostInfoImpl(postId: PostId): Future[PostInfo] = {
      Future.successful(postInfoData(postId))
    }

    def getPostContentImpl(postId: PostId): Future[PostContent] = {
      Future.successful(postContentData(postId))
    }

    def getPostViewsImpl(postId: PostId): Future[PostViews] = {
      Future.successful(postViewsData(postId))
    }

    def processRequest(r: ExampleRequest[_])(implicit executionContext: ExecutionContext): Future[_] = {
      r match {
        case GetPostIds             => getPostIdsImpl()
        case GetPostInfo(postId)    => getPostInfoImpl(postId)
        case GetPostContent(postId) => getPostContentImpl(postId)
        case GetPostViews(postId)   => getPostViewsImpl(postId)
      }
    }

    def name = "ExampleDataSource"

    def fetch(blockedRequests: Seq[ExampleRequest[_]])(implicit executionContext: ExecutionContext): Future[Seq[_]] = {
      Future.traverse(blockedRequests)(processRequest)
    }
  }

  // En este ejemplo solo hay un datasource -> todos los requests son atendidos por él
  def dataSource(request: ExampleRequest[_]): DataSource[ExampleRequest] = ExampleDataSource
}

object Test extends PageRenderingExample with App {

  import scala.concurrent.ExecutionContext.Implicits.global

  println("About to run")

  /**
   * Ejecucion
   */
  val resultF = Fetch.run(pageHTML)(dataSource)

  resultF.onComplete {
    case Success((html, cache)) =>
      println(s"Success!: $html")
      println("Caché:")
      println(cache)

      val resultF2 = Fetch.run(pageHTML)(dataSource, cache)

      resultF2.onComplete {
        case Success((html2, cache2)) =>
          println(s"Success2: $html")
          assert(cache == cache2)
          assert(html == html2)
          println("----Done!----")
        case Failure(t) => println(s"Failure: $t")
      }

    case Failure(t) => println(s"Failure: $t")
  }

}
