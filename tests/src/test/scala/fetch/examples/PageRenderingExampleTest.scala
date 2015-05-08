package fetch.examples

import fetch._
import fetch.examples.page_rendering.PageRenderingExample
import org.scalatest.mock.MockitoSugar
import org.scalatest.time.{ Seconds, Millis, Span }
import org.scalatest.{ Matchers, WordSpec }
import org.scalatest.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import org.mockito.Matchers._
import org.mockito.Mockito._

/**
 * Created by mglvl on 5/05/15.
 */
class PageRenderingExampleTest extends WordSpec with ScalaFutures with PageRenderingExample with Matchers with MockitoSugar {

  implicit val patience =
    PatienceConfig(timeout = Span(1, Seconds), interval = Span(15, Millis))

  "PageRenderingExample" must {

    def getValueAt[A](dataCache: DataCache[ExampleRequest])(k: ExampleRequest[A]): A = {
      val Some(atom) = dataCache.lookup(k)
      val FetchSuccess(value) = atom()
      value
    }

    "return results when the fetches are succesfull" in {

      val dataSource = spy(ExampleDataSource)
      def dataSourceF(request: ExampleRequest[_]): DataSource[ExampleRequest] = dataSource
      whenReady(Fetch.run(pageHTML)(dataSourceF)) {
        case (html, cache, stats) =>
          html shouldEqual (HTML)
          stats.numberOfRounds shouldEqual (3)
          def cacheValueAt[A]: ExampleRequest[A] => A = getValueAt(cache)

          verify(dataSource, times(1)).getPostIdsImpl()
          cacheValueAt(GetPostIds) shouldEqual (postsIds)

          postInfoData foreach {
            case (pid, pinfo) =>
              cacheValueAt(GetPostInfo(pid)) shouldEqual (pinfo)
              verify(dataSource, times(1)).getPostInfoImpl(pid)
          }
          postViewsData foreach {
            case (pid, pviews) =>
              cacheValueAt(GetPostViews(pid)) shouldEqual (pviews)
              verify(dataSource, times(1)).getPostViewsImpl(pid)
          }

          val cacheSize = 1 /*post ids fetch*/ + postInfoData.size + postViewsData.size + 5 /*los 5 de PostContent*/
          cache.size shouldEqual (cacheSize)

      }
    }

    "when replaying with the same cache it just reruns without making any fetch" in {
      val dataSource = spy(ExampleDataSource)
      def dataSourceF(request: ExampleRequest[_]): DataSource[ExampleRequest] = dataSource
      val future = for {
        (html1, cache1, stats1) <- Fetch.run(pageHTML)(dataSourceF)
        (html2, cache2, stats2) <- Fetch.run(pageHTML)(dataSourceF, cache1)
      } yield {
        (html1, cache1, stats1, html2, cache2, stats2)
      }

      whenReady(future) {
        case (html1, cache1, stats1, html2, cache2, stats2) =>
          html1 shouldEqual (html2) //Results are the same when replaying
          cache1 shouldEqual (cache2) //Caches should be the same when replaying
          verify(dataSource, times(1)).getPostIdsImpl()
          //@TODO agregar mas verificaciones de llamadas
          stats2.numberOfRounds shouldEqual (0) //Number of rounds should be zero when replaying
      }
    }

  }

}
