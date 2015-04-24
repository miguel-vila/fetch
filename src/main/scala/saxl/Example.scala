package saxl

import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scalaz.syntax.applicative._
import scalaz.std.stream.streamInstance

/**
 * Created by mglvl on 23/04/15.
 */

/**
 * Tipos bases
 */
class Example extends FetchInstance  {

  println("Example")

  /**
   * Tipos que representan las posibles respuestas de un servicio
   */
  type PostId = Int
  type PostViews = Int
  type PostDate = Date

  trait PostContent
  object PostContent extends PostContent

  case class PostInfo(
                       postId: PostId,
                       postDate: PostDate,
                       postTopic: String)

  type PostIds = Stream[PostId]

  /**
   * Tipos que representan los requests que atienden los servicios
   */
  case object GetPostIds extends Request[PostIds]

  case class GetPostInfo(postId: PostId) extends Request[PostInfo]

  case class GetPostContent(postId: PostId) extends Request[PostContent]

  case class GetPostViews(postId: PostId) extends Request[PostViews]

  /**
   * Valores/Funciones Fetch que representan los queries básicos
   * a partir de los cuales se realiza la composición para implementar
   * alguna lógica de negocio
   */

  val getPostIds = dataFetch(GetPostIds)

  def getPostInfo(postId: PostId) = dataFetch(GetPostInfo(postId))

  def getPostContent(postId: PostId) = dataFetch(GetPostContent(postId))

  def getPostViews(postId: PostId) = dataFetch(GetPostViews(postId))

  /**
   * Representa un documento HTML
   */
  trait HTML
  object HTML extends HTML

  /**
   * Funciones de "Renderización"
   */
  def renderPosts(content: Stream[(PostInfo,PostContent)]): HTML = HTML
  def renderPostList(content: Stream[(PostInfo,PostContent)]): HTML = HTML
  def renderTopics(topicsCount: Map[String, Int]): HTML = HTML
  def renderLeftPane(popularPostsPane: HTML, topicsPane: HTML): HTML = HTML
  def renderPage(leftPane: HTML, mainPane: HTML): HTML = HTML

  /**
   * Composición de servicios / Lógica de negocio
   */
  val getAllPostsInfo: Fetch[Stream[PostInfo]] = getPostIds.flatMap { postIds =>
    Fetch.traverse(postIds)(getPostInfo)
  }

  val mainPane: Fetch[HTML] = {
    for {
      posts <- getAllPostsInfo
      ordered = posts.sortBy(_.postDate).take(5)
      content <- Fetch.traverse(ordered)(p => getPostContent(p.postId))
      renderingContent = ordered zip content
    } yield renderPosts(renderingContent)
  }

  def getPostDetails(postId: PostId): Fetch[(PostInfo, PostContent)] = {
    ( getPostInfo(postId) |@| getPostContent(postId) ) { (_,_) }
  }

  val popularPosts: Fetch[HTML] = for {
    postIds <- getPostIds
    views <- Fetch.traverse(postIds)(getPostViews)
    ordered = (postIds zip views).sortBy(_._2).map(_._1).take(5)
    content <- Fetch.traverse(ordered)(getPostDetails)
  } yield renderPostList(content)


  val topics: Fetch[HTML] = for {
    posts <- getAllPostsInfo
    topicCounts = posts.groupBy(_.postTopic).mapValues(_.size)
  } yield renderTopics(topicCounts)

  val leftPane: Fetch[HTML] = (popularPosts |@| topics)(renderLeftPane)

  val pageHTML: Fetch[HTML] = (leftPane |@| mainPane)(renderPage)

  /**
   * Implementaciones de los servicios base
   */
  def getPostIdsImpl(): Future[PostIds] = {
    Future.successful(Stream(1,2,3,4,5,6,7))
  }

  val postInfoData = Map(
    1 -> PostInfo(1,new Date(),"topic post 1"),
    2 -> PostInfo(2,new Date(),"topic post 2"),
    3 -> PostInfo(3,new Date(),"topic post 3"),
    4 -> PostInfo(4,new Date(),"topic post 4"),
    5 -> PostInfo(5,new Date(),"topic post 5"),
    6 -> PostInfo(6,new Date(),"topic post 6"),
    7 -> PostInfo(7,new Date(),"topic post 7")
  )

  def getPostInfoImpl(postId: PostId): Future[PostInfo] = {
    Future.successful(postInfoData(postId))
  }

  def getPostContentImpl(postId: PostId): Future[PostContent] = {
    Future.successful(PostContent)
  }

  val postViewsData = Map(
    1 -> 10,
    2 -> 20,
    3 -> 30,
    4 -> 40,
    5 -> 50,
    6 -> 60,
    7 -> 70
  )

  def getPostViewsImpl(postId: PostId): Future[PostViews] = {
    Future.successful(postViewsData(postId))
  }

  /**
   * Función que ejecuta un query y realiza el side effect correspondiente
   */
  def processRequest(br: BlockedRequest[_])(implicit executionContext: ExecutionContext): Future[Unit] = {
    br match {
      case bra @ BlockedRequest(GetPostIds,_)             => processBlockedRequest( bra, getPostIdsImpl() )
      case bra @ BlockedRequest(GetPostInfo(postId),_)    => processBlockedRequest( bra, getPostInfoImpl(postId) )
      case bra @ BlockedRequest(GetPostContent(postId),_) => processBlockedRequest( bra, getPostContentImpl(postId) )
      case bra @ BlockedRequest(GetPostViews(postId),_)   => processBlockedRequest( bra, getPostViewsImpl(postId) )
    }
  }

  /**
   * Fetcher que utiliza la anterior funcion para ejecutar de forma independiente
   * cada servicio. Es posible que dados multiples servicios uno quiera agrupar
   * requests a la misma fuente de datos. En este ejemplo no se hace eso, cada
   * servicio se ejecuta independientemente de los otros. Dado que las funciones
   * de tipo [[Fetcher]] reciben multiples [[BlockedRequest]] esto se podria implementar.
   */
  def fetcher(br: Seq[BlockedRequest[_]])(implicit executionContext: ExecutionContext): Future[Unit] = {
    for {
      _ <- Future.traverse(br)(processRequest)
    } yield ()
  }

}

object Test extends Example with App {

  import scala.concurrent.ExecutionContext.Implicits.global
  implicit val cache = Atom(DataCache())
  println("About to run")

  /**
   * Ejecucion
   */
  runFetch(pageHTML, fetcher).onComplete {
    case Success(html) =>
      println(s"Success!: $html")
      println(cache)
    case Failure(t) => println(s"Failure: $t")
  }

}