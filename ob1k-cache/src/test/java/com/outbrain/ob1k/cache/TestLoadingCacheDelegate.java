package com.outbrain.ob1k.cache;

import com.outbrain.ob1k.concurrent.ComposableFuture;
import com.outbrain.ob1k.concurrent.ComposableFutures;
import junit.framework.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by aronen on 10/27/14.
 *
 * emulate a loader that takes a while and test to se that values are loaded only once.
 */
public class TestLoadingCacheDelegate {
  @Test
  public void testPartialLoading() throws ExecutionException, InterruptedException {
    final AtomicInteger loaderCounter = new AtomicInteger();
    final TypedCache<String, String> cache = new LocalAsyncCache<>();
    final TypedCache<String, String> loadingCache = new LoadingCacheDelegate<>(cache, new CacheLoader<String, String>() {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      @Override
      public ComposableFuture<String> load(final String cacheName, final String key) {
        System.out.println("loading(single): " + key);
        loaderCounter.incrementAndGet();
        return ComposableFutures.schedule(new Callable<String>() {
          @Override
          public String call() throws Exception {
            return "res-" + key;
          }
        }, random.nextLong(1, 19), TimeUnit.MILLISECONDS);
      }

      @Override
      public ComposableFuture<Map<String, String>> load(final String cacheName, final Iterable<? extends String> keys) {
        final Map<String, ComposableFuture<String>> res = new HashMap<>();
        for (final String key: keys) {
          System.out.println("loading(multiple): " + key);
          loaderCounter.incrementAndGet();
          res.put(key, ComposableFutures.schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
              return "res-" + key;
            }
          }, random.nextLong(1, 19), TimeUnit.MILLISECONDS));
        }

        System.out.println("returning keys: " + res.keySet());
        return ComposableFutures.all(true, res);
      }
    }, "default");

    for (int i=0;i < 100; i++) {
      final ComposableFuture<String> res1 = loadingCache.getAsync("1");
      final ComposableFuture<Map<String, String>> res2 = loadingCache.getBulkAsync(Arrays.asList("1", "2"));
      final ComposableFuture<Map<String, String>> res3 = loadingCache.getBulkAsync(Arrays.asList("1", "2", "3", "4", "5"));

      Assert.assertEquals(res1.get(), "res-1");
      Assert.assertEquals(res2.get().size(), 2);
      Assert.assertEquals(res3.get().size(), 5);
      Assert.assertEquals(res3.get().get("5"), "res-5");

      Assert.assertTrue(loaderCounter.get() <= 5);

      loadingCache.deleteAsync("1").get();
      loadingCache.deleteAsync("2").get();
      loadingCache.deleteAsync("3").get();
      loadingCache.deleteAsync("4").get();
      loadingCache.deleteAsync("5").get();

//      Thread.sleep(200L);
      loaderCounter.set(0);

      System.out.println("---------------------------");
    }

  }

}
