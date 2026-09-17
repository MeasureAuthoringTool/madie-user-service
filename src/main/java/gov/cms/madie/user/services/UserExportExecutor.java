package gov.cms.madie.user.services;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bounded worker pool used to fan out the per-user work of the Full User Export so that the
 * (otherwise serial) downstream measure-service calls run concurrently.
 *
 * <p>This is deliberately <em>not</em> exposed as a Spring {@link java.util.concurrent.Executor} /
 * {@code TaskExecutor} bean: doing so would make Spring Boot's auto-configured application task
 * executor back off and change the executor used by existing {@code @Async} methods (e.g. the user
 * refresh job). Owning a private {@link ExecutorService} keeps this pool isolated to the export.
 */
@Slf4j
@Component
public class UserExportExecutor {

  private final ExecutorService executor;

  public UserExportExecutor(@Value("${user-export.concurrency:16}") int concurrency) {
    int size = Math.max(1, concurrency);
    this.executor = Executors.newFixedThreadPool(size, namedDaemonThreadFactory());
    log.info("Initialized user-export worker pool with concurrency={}", size);
  }

  /**
   * Applies {@code mapper} to every item concurrently and returns the results in the same order as
   * the input. Blocks until all tasks complete.
   *
   * @param items the inputs to process (may be empty or null)
   * @param mapper the (thread-safe) mapping function to apply to each item
   * @param <I> the input type
   * @param <O> the output type
   * @return the mapped results, in input order
   */
  public <I, O> List<O> mapOrdered(List<I> items, Function<I, O> mapper) {
    if (items == null || items.isEmpty()) {
      return List.of();
    }
    List<Future<O>> futures = new ArrayList<>(items.size());
    for (I item : items) {
      futures.add(executor.submit(() -> mapper.apply(item)));
    }
    List<O> results = new ArrayList<>(items.size());
    for (Future<O> future : futures) {
      results.add(awaitResult(future));
    }
    return results;
  }

  private <O> O awaitResult(Future<O> future) {
    try {
      return future.get();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while awaiting a user-export task", ex);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new IllegalStateException("A user-export task failed", cause);
    }
  }

  /** Creates named, daemon worker threads so they never block JVM shutdown. */
  private static ThreadFactory namedDaemonThreadFactory() {
    AtomicInteger counter = new AtomicInteger(1);
    return runnable -> {
      Thread thread = new Thread(runnable, "user-export-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
  }

  @PreDestroy
  void shutdown() {
    executor.shutdown();
  }
}
