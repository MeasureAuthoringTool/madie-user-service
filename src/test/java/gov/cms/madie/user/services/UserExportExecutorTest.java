package gov.cms.madie.user.services;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class UserExportExecutorTest {

  @Test
  void mapOrderedReturnsEmptyForNullInput() {
    UserExportExecutor executor = new UserExportExecutor(2);
    try {
      assertThat(executor.mapOrdered(null, Function.identity()), is(empty()));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedReturnsEmptyForEmptyInput() {
    UserExportExecutor executor = new UserExportExecutor(2);
    try {
      assertThat(executor.mapOrdered(List.of(), Function.identity()), is(empty()));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedMapsAndPreservesInputOrder() {
    UserExportExecutor executor = new UserExportExecutor(4);
    try {
      List<Integer> result = executor.mapOrdered(List.of(1, 2, 3, 4, 5), i -> i * 10);
      assertThat(result, contains(10, 20, 30, 40, 50));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedRunsTasksConcurrently() {
    int taskCount = 4;
    UserExportExecutor executor = new UserExportExecutor(taskCount);
    // A barrier of size taskCount only trips when all tasks are running simultaneously; if
    // execution
    // were serial the barrier would time out and the mapper would throw.
    CyclicBarrier barrier = new CyclicBarrier(taskCount);
    Function<Integer, Integer> mapper =
        i -> {
          try {
            barrier.await(5, TimeUnit.SECONDS);
          } catch (Exception ex) {
            throw new IllegalStateException("tasks did not run concurrently", ex);
          }
          return i * 2;
        };
    try {
      List<Integer> result = executor.mapOrdered(List.of(0, 1, 2, 3), mapper);
      assertThat(result, contains(0, 2, 4, 6));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void constructorClampsNonPositiveConcurrencyToSingleWorker() {
    UserExportExecutor executor = new UserExportExecutor(0);
    try {
      assertThat(executor.mapOrdered(List.of(1, 2, 3), i -> i + 1), contains(2, 3, 4));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedPropagatesRuntimeExceptionFromMapper() {
    UserExportExecutor executor = new UserExportExecutor(2);
    IllegalArgumentException boom = new IllegalArgumentException("boom");
    try {
      IllegalArgumentException thrown =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  executor.mapOrdered(
                      List.of(1),
                      i -> {
                        throw boom;
                      }));
      // The original runtime exception is rethrown as-is (not wrapped).
      assertThat(thrown, is(sameInstance(boom)));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedWrapsNonRuntimeThrowableFromMapper() {
    UserExportExecutor executor = new UserExportExecutor(2);
    AssertionError error = new AssertionError("fatal");
    try {
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  executor.mapOrdered(
                      List.of(1),
                      i -> {
                        throw error;
                      }));
      assertThat(thrown.getCause(), is(sameInstance(error)));
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedWrapsInterruptionAsIllegalState() throws InterruptedException {
    UserExportExecutor executor = new UserExportExecutor(1);
    CountDownLatch taskStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Function<Integer, Integer> blockingMapper =
        i -> {
          taskStarted.countDown();
          try {
            release.await();
          } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
          }
          return i;
        };

    AtomicReference<Throwable> thrown = new AtomicReference<>();
    Thread caller =
        new Thread(
            () -> {
              try {
                executor.mapOrdered(List.of(1), blockingMapper);
              } catch (Throwable t) {
                thrown.set(t);
              }
            });
    caller.start();

    try {
      // Ensure the worker task is running (so the caller is blocked in Future.get()).
      assertTrue(taskStarted.await(2, TimeUnit.SECONDS));
      caller.interrupt();
      caller.join(2000);

      assertThat(thrown.get(), is(instanceOf(IllegalStateException.class)));
      assertThat(thrown.get().getCause(), is(instanceOf(InterruptedException.class)));
    } finally {
      release.countDown();
      executor.shutdown();
    }
  }

  @Test
  void mapOrderedRejectsWorkAfterShutdown() {
    UserExportExecutor executor = new UserExportExecutor(1);
    executor.shutdown();

    assertThrows(
        RejectedExecutionException.class,
        () -> executor.mapOrdered(List.of(1), Function.identity()));
  }
}
