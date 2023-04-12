package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final List<Pattern> ignoredUrls;
  private final int maxDepth;
  private final PageParserFactory parserFactory;

  @Inject
  ParallelWebCrawler(Clock clock, @Timeout Duration timeout, @PopularWordCount int popularWordCount, @TargetParallelism int threadCount, @IgnoredUrls List<Pattern> ignoredUrls, @MaxDepth int maxDepth, PageParserFactory parserFactory) {
    this.clock = clock;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, this.getMaxParallelism()));
    this.ignoredUrls = ignoredUrls;
    this.maxDepth = maxDepth;
    this.parserFactory = parserFactory;
  }

  public CrawlResult crawl(List<String> startingUrls) {
    Instant deadline = this.clock.instant().plus(this.timeout);
    ConcurrentMap<String, Integer> counts = new ConcurrentHashMap();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet();
    Iterator i = startingUrls.iterator();

    while(i.hasNext()) {
      String url = (String)i.next();
      this.pool.invoke(new parallelCrawler(url, deadline, this.maxDepth, counts, visitedUrls));
    }

    return counts.isEmpty() ? (new CrawlResult.Builder()).setWordCounts(counts).setUrlsVisited(visitedUrls.size()).build() : (new CrawlResult.Builder()).setWordCounts(WordCounts.sort(counts, this.popularWordCount)).setUrlsVisited(visitedUrls.size()).build();
  }

  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }

  public class parallelCrawler extends RecursiveTask<Boolean> {
    private String url;
    private Instant deadline;
    private int maxDepth;
    private ConcurrentMap<String, Integer> counts;
    private ConcurrentSkipListSet<String> visitedUrls;

    public parallelCrawler(String url, Instant deadline, int maxDepth, ConcurrentMap<String, Integer> counts, ConcurrentSkipListSet<String> visitedUrls) {
      this.url = url;
      this.deadline = deadline;
      this.maxDepth = maxDepth;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
    }

    protected Boolean compute() {
      if (maxDepth != 0 && !ParallelWebCrawler.this.clock.instant().isAfter(deadline)) {
        Iterator iterator = ParallelWebCrawler.this.ignoredUrls.iterator();

        while(iterator.hasNext()) {
          Pattern pattern = (Pattern)iterator.next();
          if (pattern.matcher(url).matches()) {
            return false;
          }
        }

        if (!this.visitedUrls.add(url)) {
          return false;
        } else {
          visitedUrls.add(url);
          PageParser.Result result = ParallelWebCrawler.this.parserFactory.get(url).parse();
          Iterator iterator2 = result.getWordCounts().entrySet().iterator();

          while(iterator2.hasNext()) {
            Map.Entry<String, Integer> e = (Map.Entry)iterator2.next();
            this.counts.compute(e.getKey(), (k, v) -> v == null ? e.getValue() : e.getValue() + v);
          }

          List<parallelCrawler> subtasks = new ArrayList();
          Iterator iterator3 = result.getLinks().iterator();

          while(iterator3.hasNext()) {
            String link = (String)iterator3.next();
            subtasks.add(ParallelWebCrawler.this.new parallelCrawler(link, this.deadline, this.maxDepth - 1, this.counts, this.visitedUrls));
          }

          invokeAll(subtasks);
          return true;
        }
      } else {
        return false;
      }
    }
  }
}

