package com.outbrain.ob1k.concurrent;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.outbrain.ob1k.concurrent.combiners.BiFunction;
import com.outbrain.ob1k.concurrent.combiners.TriFunction;
import com.outbrain.ob1k.concurrent.handlers.*;
import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import rx.Observable;

import static com.outbrain.ob1k.concurrent.ComposableFutures.*;
import static com.outbrain.ob1k.concurrent.ComposableFutures.toObservable;

/**
 * User: aronen
 * Date: 7/2/13
 * Time: 10:20 AM
 */
public class ComposableFutureTest {

    public static final int ITERATIONS = 100000;

    @Test
    public void testForeach() throws ExecutionException, InterruptedException {
        final List<Integer> numbers = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            numbers.add(i);
        }

        final List<Integer> empty = new ArrayList<>();
        final ComposableFuture<List<Integer>> first3Even = foreach(numbers, empty, new ForeachHandler<Integer, List<Integer>>() {
            @Override
            public ComposableFuture<List<Integer>> handle(final Integer element, final List<Integer> aggregateResult) {
                if (aggregateResult.size() < 3 && element % 2 == 0) {
                    aggregateResult.add(element);
                }

                return fromValue(aggregateResult);
            }
        });

        final List<Integer> result = first3Even.get();
        Assert.assertEquals(result.size(), 3);
        Assert.assertEquals(result.get(0), new Integer(2));
        Assert.assertEquals(result.get(1), new Integer(4));
        Assert.assertEquals(result.get(2), new Integer(6));
    }

    @Test
    public void testRepeat() throws Exception {
        final ComposableFuture<Integer> future = repeat(10, 0, new FutureSuccessHandler<Integer, Integer>() {
            @Override
            public ComposableFuture<Integer> handle(final Integer result) {
                return fromValue(result + 1);
            }
        });
        Assert.assertEquals(10, (int) future.get());
    }

    @Test
    public void testRecursive() throws Exception {
        final AtomicInteger atomicInteger = new AtomicInteger();
        final ComposableFuture<Integer> future = recursive(new Supplier<ComposableFuture<Integer>>() {
            @Override
            public ComposableFuture<Integer> get() {
                return fromValue(atomicInteger.incrementAndGet());
            }
        }, new Predicate<Integer>() {
            @Override
            public boolean apply(final Integer input) {
                return input >= 10;
            }
        });
        Assert.assertEquals(10, (int) future.get());
    }

    @Test
    @Ignore("performance test")
    public void testThreadPool() {
        testWithRegularThreadPool(true);
        //    testWithRegularThreadPool(false);
    }

    @Test
    @Ignore("performance test")
    public void testSingleThreadBenchmark() {
        final long t1 = System.currentTimeMillis();
        long sum = 0;

        for (long i = 0; i < ITERATIONS; i++) {
            final long phase1 = computeHash(i);
            final long phase2 = computeHash(phase1);
            final long phase3 = computeHash(phase2);
            sum += phase3;
        }

        final long t2 = System.currentTimeMillis();
        System.out.println("total time: " + (t2 - t1) + " for sum: " + sum);
    }

    //  private static class ComputeHashTask extends RecursiveTask<Long> {
    //    private final long seed;
    //    private final int phase;
    //
    //    private ComputeHashTask(long seed, int phase) {
    //      this.seed = seed;
    //      this.phase = phase;
    //    }
    //
    //    @Override
    //    protected Long compute() {
    //      long value = computeHash(seed);
    //      if (phase < 3) {
    //        ComputeHashTask nextPhase = new ComputeHashTask(value, phase + 1);
    //        value = nextPhase.fork().join();
    //      }
    //
    //      return value;
    //    }
    //  }

    //  @Test
    //  public void testWithForkJoin() {
    //    long t1 = System.currentTimeMillis();
    //
    //    ForkJoinPool pool = new ForkJoinPool();
    //    List<ForkJoinTask<Long>> tasks = new ArrayList<ForkJoinTask<Long>>();
    //    for (int i=0; i< ITERATIONS; i++) {
    //      final long seed = i;
    //      ForkJoinTask<Long> task = pool.submit(new ComputeHashTask(seed, 1));
    //      tasks.add(task);
    //    }
    //
    //    long sum = 0;
    //    for (ForkJoinTask<Long> task : tasks) {
    //      try {
    //        sum += task.get();
    //      } catch (Exception e) {
    //        e.printStackTrace();
    //      }
    //    }
    //
    //    long t2 = System.currentTimeMillis();
    //    System.out.println("total time: " + (t2 - t1) + " for sum: " + sum);
    //  }

    private void testWithRegularThreadPool(final boolean delegate) {
        final List<ComposableFuture<Long>> futures = new ArrayList<>();

        final long t1 = System.currentTimeMillis();

        for (int i = 0; i < ITERATIONS; i++) {
            final long seed = i;
            final ComposableFuture<Long> f1 = submit(delegate, new Callable<Long>() {
                @Override
                public Long call() throws Exception {
                    return computeHash(seed);
                }
            });

            final ComposableFuture<Long> f2 = f1.continueOnSuccess(new SuccessHandler<Long, Long>() {
                @Override
                public Long handle(final Long seed) {
                    return computeHash(seed);
                }
            });

            final ComposableFuture<Long> f3 = f2.continueOnSuccess(new SuccessHandler<Long, Long>() {
                @Override
                public Long handle(final Long seed) {
                    return computeHash(seed);
                }
            });

            futures.add(f3);

        }

        final ComposableFuture<List<Long>> all = all(futures);
        try {
            final List<Long> res = all.get();
            final long t2 = System.currentTimeMillis();
            long sum = 0;
            for (final long num : res) {
                sum += num;
            }
            System.out.println("total time: " + (t2 - t1) + " for sum: " + sum);
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static long computeHash(final long seed) {
        long value = seed;
        for (int i = 0; i < 10000; i++) {
            value ^= value << 13;
            value ^= value >>> 17;
            value ^= value << 5;
        }

        return value;
    }

    @Test
    public void testContinuations() {
        final ComposableFuture<String> res =
            schedule(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    System.out.println("in first phase");
                    return "lala";
                }
            }, 100, TimeUnit.MILLISECONDS).continueWith(new FutureResultHandler<String, String>() {
                @Override
                public ComposableFuture<String> handle(final Try<String> result) {
                    System.out.println("in second phase, throwing exception");
                    return fromError(new RuntimeException("bhaaaaa"));
                }
            }).continueOnSuccess(new SuccessHandler<String, String>() {
                @Override
                public String handle(final String result) {
                    System.out.println("in third phase, ****** shouldn't be here !!!!!  ******** returning second lala");
                    return "second lala";
                }
            }).continueOnError(new ErrorHandler<String>() {
                @Override
                public String handle(final Throwable error) {
                    System.out.println("in forth, returning third lala. error type is: " + error.getClass().getName());
                    return "third lala";
                }
            }).continueOnError(new ErrorHandler<String>() {
                @Override
                public String handle(final Throwable error) {
                    System.out.println("***** shouldn't be here *****");
                    return "baaaaddddd";
                }
            }).continueOnSuccess(new SuccessHandler<String, String>() {
                @Override
                public String handle(final String result) throws ExecutionException {
                    System.out.println("got: " + result + " throwing exception.");
                    throw new ExecutionException(new RuntimeException("booo"));
                }
            });

        res.consume(new Consumer<String>() {
            @Override
            public void consume(final Try<String> element) {
                if (element.isSuccess()) {
                    System.out.println("should be here !!!!");
                } else {
                    System.out.println("got exception of type: " + element.getError().getClass().getName());
                }
            }
        });

        try {
            final String result = res.get();
            System.out.println("got final result: " + result);
            Assert.fail("got result instead of an exception");
        } catch (InterruptedException | ExecutionException e) {
            final String exTypeName = e.getCause().getClass().getName();
            System.out.println("got final exception of type: " + exTypeName);
            Assert.assertEquals(exTypeName, RuntimeException.class.getName());
        }
    }

    private static final class Person {
        public final int age;
        public final String name;
        public final double weight;


        private Person(final int age, final String name, final double weight) {
            this.age = age;
            this.name = name;
            this.weight = weight;
        }
    }

    @Test
    public void testComposingFutureTypes() {
        final String name = "haim";
        final int age = 23;
        final double weight = 70.3;

        final ComposableFuture<String> futureName = fromValue(name);
        final ComposableFuture<Integer> futureAge = fromValue(age);
        final ComposableFuture<Double> futureWeight = fromValue(weight);

//      final ComposableFuture<Double> weight = fromError(new RuntimeException("Illegal Weight error!"));

        final ComposableFuture<Person> person = combine(futureName, futureAge, futureWeight, new TriFunction<String, Integer, Double, Person>() {
            @Override
            public Person apply(final String name, final Integer age, final Double weight) {
                return new Person(age, name, weight);
            }
        });

        try {
            final Person result = person.get();
            Assert.assertEquals(result.age, age);
            Assert.assertEquals(result.name, name);
            Assert.assertEquals(result.weight, weight);
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail(e.getMessage());
        }

        final ComposableFuture<String> first = fromValue("1");
        final ComposableFuture<Integer> second = fromValue(2);
        final ComposableFuture<Object> badRes = combine(first, second, new BiFunction<String, Integer, Object>() {
            @Override
            public Object apply(final String left, final Integer right) throws ExecutionException {
                throw new ExecutionException(new RuntimeException("not the same..."));
            }
        });

        try {
            badRes.get();
            Assert.fail("should get an error");
        } catch (final InterruptedException e) {
            Assert.fail(e.getMessage());
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause().getMessage().contains("not the same..."));
        }

    }

    @Test
    public void testSlowFuture() {
        final ComposableFuture<String> f1 = schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "slow";
            }
        }, 1, TimeUnit.SECONDS);

        final ComposableFuture<String> f2 = fromValue("fast1");
        final ComposableFuture<String> f3 = fromValue("fast2");

        final ComposableFuture<List<String>> res = all(Arrays.asList(f1, f2, f3));
        final long t1 = System.currentTimeMillis();
        try {
            final List<String> results = res.get();
            final long t2 = System.currentTimeMillis();
            Assert.assertTrue("time is: " + (t2 - t1), (t2 - t1) > 900); // not
        } catch (InterruptedException | ExecutionException e) {
            Assert.fail(e.getMessage());
        }

        final ComposableFuture<String> f4 = schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "slow";
            }
        }, 1, TimeUnit.SECONDS);
        final ComposableFuture<String> f5 = fromError(new RuntimeException("oops"));
        final ComposableFuture<List<String>> res2 = all(true, Arrays.asList(f4, f5));
        final long t3 = System.currentTimeMillis();
        try {
            final List<String> results = res2.get();
            Assert.fail("should get error.");
        } catch (InterruptedException | ExecutionException e) {
            final long t4 = System.currentTimeMillis();
            Assert.assertTrue((t4 - t3) < 100);
        }

        final ComposableFuture<String> f6 = schedule(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "slow";
            }
        }, 1, TimeUnit.SECONDS);
        final ComposableFuture<String> f7 = fromError(new RuntimeException("oops"));
        final ComposableFuture<List<String>> res3 = all(true, Arrays.asList(f6, f7));
        final long t5 = System.currentTimeMillis();
        try {
            final List<String> results = res3.get();
            Assert.fail("should get error.");
        } catch (InterruptedException | ExecutionException e) {
            final long t6 = System.currentTimeMillis();
            System.out.println("time took to fail: " + (t6 - t5));
            Assert.assertTrue((t6 - t5) < 100);
        }

    }

    @Test
    public void testFuturesToStream() throws InterruptedException {
        final ComposableFuture<Long> first = schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        }, 1, TimeUnit.SECONDS);

        final ComposableFuture<Long> second = schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        }, 2, TimeUnit.SECONDS);

        final ComposableFuture<Long> third = schedule(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                return System.currentTimeMillis();
            }
        }, 3, TimeUnit.SECONDS);

        final Iterable<Long> events = toHotObservable(Arrays.asList(first, second, third), true).toBlocking().toIterable();
        long prevEvent = 0;
        int counter = 0;
        for (final Long event : events) {
            counter++;
            Assert.assertTrue("event should have bigger timestamp than the previous one", event > prevEvent);
            prevEvent = event;
        }

        Assert.assertEquals("should receive 3 events", counter, 3);

    }

    @Test
    public void testFutureProviderToStream() {
        final Observable<Long> stream = toObservable(new FutureProvider<Long>() {
            private volatile int index = 3;
            private volatile ComposableFuture<Long> currentRes;

            @Override
            public boolean moveNext() {
                if (index > 0) {
                    index--;
                    currentRes = schedule(new Callable<Long>() {
                        @Override
                        public Long call() throws Exception {
                            return System.currentTimeMillis();
                        }
                    }, 100, TimeUnit.MILLISECONDS);

                    return true;
                } else {
                    return false;
                }
            }

            @Override
            public ComposableFuture<Long> current() {
                return currentRes;
            }
        });

        long current = System.currentTimeMillis();
        final Iterable<Long> events = stream.toBlocking().toIterable();
        int counter = 0;
        for (final Long event : events) {
            Assert.assertTrue(event > current);
            current = event;
            counter++;
        }

        Assert.assertTrue(counter == 3);

    }

    @Test
    public void testFirstNoTimeout() throws Exception {
        final Map<String, ComposableFuture<String>> elements = createElementsMap();

        final Map<String, String> res = first(elements, 3).get();
        Assert.assertEquals(res.size(), 3);
        Assert.assertEquals(res.get("one"), "one");
        Assert.assertEquals(res.get("three"), "three");
        Assert.assertEquals(res.get("five"), "five");
    }

    @Test
    public void testFirstWithTimeout() throws Exception {
        final Map<String, ComposableFuture<String>> elements = createElementsMap();

        final Map<String, String> res = first(elements, 3, 10, TimeUnit.MILLISECONDS).get();

        Assert.assertEquals(2, res.size());
        Assert.assertEquals("one", res.get("one"));
        Assert.assertEquals("five", res.get("five"));
    }

    @Test
    public void testAllFailOnError() throws Exception {
        final Map<String, ComposableFuture<String>> elements = createElementsMap();

        try {
            all(true, elements).get();
            Assert.fail("should get an exception");
        } catch (final ExecutionException e) {
            Assert.assertTrue(e.getCause().getMessage().contains("bad element"));
        }

    }

    @Test
    public void testAllFailFast() throws Exception {
        final Map<String, ComposableFuture<String>> elements = new HashMap<>();

        elements.put("one", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Thread.sleep(100);
                return "one";
            }
        }));

        elements.put("two", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                throw new RuntimeException("error...");
            }
        }));

        final long t1 = System.currentTimeMillis();
        try {
            all(true, elements).get();
            Assert.fail("should fail");
        } catch (final ExecutionException e) {
            final long t2 = System.currentTimeMillis();
            System.out.println("time: " + (t2 - t1));
            Assert.assertTrue("should fail fast", (t2 - t1) < 50);
        }
    }

    private Map<String, ComposableFuture<String>> createElementsMap() {
        final Map<String, ComposableFuture<String>> elements = new HashMap<>();

        elements.put("one", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "one";
            }
        }));
        elements.put("two", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Thread.sleep(50);
                return "two";
            }
        }));
        elements.put("three", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Thread.sleep(25);
                return "three";
            }
        }));
        elements.put("four", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                Thread.sleep(50);
                throw new RuntimeException("bad element");
                //return "four";
            }
        }));
        elements.put("five", submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                return "five";
            }
        }));
        return elements;
    }

}
