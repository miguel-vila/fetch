package fetch.examples

import fetch.{ FetchSuccess, FetchStatus, DataCache, Fetch }
import fetch.examples.page_rendering.PageRenderingExample
import org.scalatest.{ Matchers, WordSpec }
import org.scalatest.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by mglvl on 5/05/15.
 */
class PageRenderingExampleTest extends WordSpec with ScalaFutures with PageRenderingExample with Matchers {

  "PageRenderingExample" must {

    def getValueAt[A](dataCache: DataCache[ExampleRequest])(k: ExampleRequest[A]): FetchStatus[A] = {
      val Some(atom) = dataCache.lookup(k)
      atom()
    }

    "return results when the fetches are succesfull" in {
      whenReady(Fetch.run(pageHTML)(dataSource)) {
        case (html, cache, stats) =>
          html shouldEqual (HTML)
          stats.numberOfRounds shouldEqual (3)
          def cacheValueAt[A]: ExampleRequest[A] => FetchStatus[A] = getValueAt(cache)
          cacheValueAt(GetPostIds) shouldEqual (FetchSuccess(postsIds))
          postInfoData foreach {
            case (pid, pinfo) =>
              cacheValueAt(GetPostInfo(pid)) shouldEqual (FetchSuccess(pinfo))
          }
          postViewsData foreach {
            case (pid, pviews) =>
              cacheValueAt(GetPostViews(pid)) shouldEqual (FetchSuccess(pviews))
          }
          val cacheSize = 1 /*post ids fetch*/ + postInfoData.size + postViewsData.size + 5 /*los 5 de PostContent*/
          cache.size shouldEqual (cacheSize)
      }
    }

  }

}
