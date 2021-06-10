package ru.javawebinar.topjava.util;

import ru.javawebinar.topjava.model.Meal;
import ru.javawebinar.topjava.model.MealWithExceed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toList;
import static ru.javawebinar.topjava.util.TimeUtil.isBetweenHalfOpen;

public class MealsUtil {
    public static Integer DEFAULT_CALORIES_PER_DAY = 2000;

    public static void main(String[] args) throws InterruptedException {
        List<Meal> meals = Arrays.asList(
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 30, 10, 0), "Завтрак", 500),
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 30, 13, 0), "Обед", 1000),
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 30, 20, 0), "Ужин", 500),
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 31, 0, 0), "Еда на граничное значение", 100),
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 31, 10, 0), "Завтрак", 1000),
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 31, 13, 0), "Обед", 500),
                new Meal(LocalDateTime.of(2020, Month.JANUARY, 31, 20, 0), "Ужин", 410)
        );

        final LocalTime startTime = LocalTime.of(7, 0);
        final LocalTime endTime = LocalTime.of(12, 0);

        List<MealWithExceed> mealsTo = filteredByStreams(meals, startTime, endTime, 2000);
        mealsTo.forEach(System.out::println);

        System.out.println(filteredByCycles(meals, startTime, endTime, 2000));

// Optional2 recursion
        System.out.println(filteredByRecursion(meals, startTime, endTime, 2000));
        System.out.println(filteredBySetterRecursion(meals, startTime, endTime, 2000));

//  Optional2 reference type
//        System.out.println(filteredByAtomic(meals, startTime, endTime, 2000));  // or boolean[1]
//        System.out.println(filteredByReflection(meals, startTime, endTime, 2000));

//   Optional2 delayed execution
//      System.out.println(filteredByClosure(meals, startTime, endTime, 2000));
        System.out.println(filteredByExecutor(meals, startTime, endTime, 2000));
        System.out.println(filteredByLock(meals, startTime, endTime, 2000));
        System.out.println(filteredByCountDownLatch(meals, startTime, endTime, 2000));
        System.out.println(filteredByPredicate(meals, startTime, endTime, 2000));
        System.out.println(filteredByConsumerChain(meals, startTime, endTime, 2000));

//   Optional2 streams
        System.out.println(filteredByFlatMap(meals, startTime, endTime, 2000));
        System.out.println(filteredByCollector(meals, startTime, endTime, 2000));
    }

    public static List<MealWithExceed> filteredByStreams(Collection<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> caloriesSumByDate = meals.stream()
                .collect(
                        Collectors.groupingBy(Meal::getDate, Collectors.summingInt(Meal::getCalories))
//                      Collectors.toMap(Meal::getDate, Meal::getCalories, Integer::sum)
                );

        return meals.stream()
                .filter(meal -> isBetweenHalfOpen(meal.getTime(), startTime, endTime))
                .map(meal -> createTo(meal, caloriesSumByDate.get(meal.getDate()) > caloriesPerDay))
                .collect(Collectors.toList());
    }

    public static List<MealWithExceed> filteredByCycles(Collection<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {

        final Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        meals.forEach(meal -> caloriesSumByDate.merge(meal.getDate(), meal.getCalories(), Integer::sum));

        final List<MealWithExceed> mealsTo = new ArrayList<>();
        meals.forEach(meal -> {
            if (isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
                mealsTo.add(createTo(meal, caloriesSumByDate.get(meal.getDate()) > caloriesPerDay));
            }
        });
        return mealsTo;
    }

    private static List<MealWithExceed> filteredByRecursion(Collection<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        ArrayList<MealWithExceed> result = new ArrayList<>();
        filterWithRecursion(new LinkedList<>(meals), startTime, endTime, caloriesPerDay, new HashMap<>(), result);
        return result;
    }

    private static void filterWithRecursion(LinkedList<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay,
                                            Map<LocalDate, Integer> dailyCaloriesMap, List<MealWithExceed> result) {
        if (meals.isEmpty()) return;

        Meal meal = meals.pop();
        dailyCaloriesMap.merge(meal.getDate(), meal.getCalories(), Integer::sum);
        filterWithRecursion(meals, startTime, endTime, caloriesPerDay, dailyCaloriesMap, result);
        if (isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
            result.add(createTo(meal, dailyCaloriesMap.get(meal.getDate()) > caloriesPerDay));
        }
    }

    private static List<MealWithExceed> filteredBySetterRecursion(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        class MealNode {
            private final MealNode prev;
            private final MealWithExceed mealTo;

            public MealNode(MealWithExceed mealTo, MealNode prev) {
                this.mealTo = mealTo;
                this.prev = prev;
            }

            public void setExceed() {
                mealTo.setExceed(true);
                if (prev != null) {
                    prev.setExceed();
                }
            }
        }

        Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        Map<LocalDate, MealNode> mealNodeByDate = new HashMap<>();
        List<MealWithExceed> mealsTo = new ArrayList<>();
        meals.forEach(meal -> {
            LocalDate localDate = meal.getDate();
            boolean exceed = caloriesSumByDate.merge(localDate, meal.getCalories(), Integer::sum) > caloriesPerDay;
            if (TimeUtil.isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
                MealWithExceed mealTo = createTo(meal, exceed);
                mealsTo.add(mealTo);
                if (!exceed) {
                    MealNode prevNode = mealNodeByDate.get(localDate);
                    mealNodeByDate.put(localDate, new MealNode(mealTo, prevNode));
                }
            }
            if (exceed) {
                MealNode mealNode = mealNodeByDate.remove(localDate);
                if (mealNode != null) {
                    // recursive set for all interval day meals
                    mealNode.setExceed();
                }
            }
        });
        return mealsTo;
    }

    /*
        private static List<MealWithExceed> filteredByAtomic(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
            Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
            Map<LocalDate, AtomicBoolean> exceededMap = new HashMap<>();

            List<MealWithExceed> mealsTo = new ArrayList<>();
            meals.forEach(meal -> {
                AtomicBoolean wrapBoolean = exceededMap.computeIfAbsent(meal.getDate(), date -> new AtomicBoolean());
                Integer dailyCalories = caloriesSumByDate.merge(meal.getDate(), meal.getCalories(), Integer::sum);
                if (dailyCalories > caloriesPerDay) {
                    wrapBoolean.set(true);
                }
                if (isBetween(meal.getTime(), startTime, endTime)) {
                  mealsTo.add(createTo(meal, wrapBoolean));  // also change createTo and MealWithExceed.excess
                }
            });
            return mealsTo;
        }

        private static List<MealWithExceed> filteredByReflection(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) throws NoSuchFieldException, IllegalAccessException {
            Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
            Map<LocalDate, Boolean> exceededMap = new HashMap<>();
            Field field = Boolean.class.getDeclaredField("value");
            field.setAccessible(true);

            List<MealWithExceed> mealsTo = new ArrayList<>();
            for (Meal meal : meals) {
                Boolean mutableBoolean = exceededMap.computeIfAbsent(meal.getDate(), date -> new Boolean(false));
                Integer dailyCalories = caloriesSumByDate.merge(meal.getDate(), meal.getCalories(), Integer::sum);
                if (dailyCalories > caloriesPerDay) {
                    field.setBoolean(mutableBoolean, true);
                }
                if (isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
                    mealsTo.add(createTo(meal, mutableBoolean));  // also change createTo and MealWithExceed.excess
                }
            }
            return mealsTo;
        }

        private static List<MealWithExceed> filteredByClosure(List<Meal> mealList, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
            final Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
            List<MealWithExceed> mealsTo = new ArrayList<>();
            mealList.forEach(meal -> {
                        caloriesSumByDate.merge(meal.getDate(), meal.getCalories(), Integer::sum);
                        if (isBetween(meal.getTime(), startTime, endTime)) {
                            mealsTo.add(createTo(meal, () -> (caloriesSumByDate.get(meal.getDate()) > caloriesPerDay))); // also change createTo and MealWithExceed.excess
                        }
                    }
            );
            return mealsTo;
        }
    */

    private static List<MealWithExceed> filteredByExecutor(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) throws InterruptedException {
        Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        final List<MealWithExceed> mealsTo = Collections.synchronizedList(new ArrayList<>());

        meals.forEach(meal -> {
            caloriesSumByDate.merge(meal.getDate(), meal.getCalories(), Integer::sum);
            if (isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
                tasks.add(() -> {
                    mealsTo.add(createTo(meal, caloriesSumByDate.get(meal.getDate()) > caloriesPerDay));
                    return null;
                });
            }
        });
        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.invokeAll(tasks);
        executorService.shutdown();
        return mealsTo;
    }

    public static List<MealWithExceed> filteredByLock(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) throws InterruptedException {
        Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        List<MealWithExceed> mealsTo = new ArrayList<>();
        ExecutorService executor = Executors.newCachedThreadPool();
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        for (Meal meal : meals) {
            caloriesSumByDate.merge(meal.getDateTime().toLocalDate(), meal.getCalories(), Integer::sum);
            if (isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime))
                executor.submit(() -> {
                    lock.lock();
                    mealsTo.add(createTo(meal, caloriesSumByDate.get(meal.getDateTime().toLocalDate()) > caloriesPerDay));
                    lock.unlock();
                });
        }
        lock.unlock();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        return mealsTo;
    }

    private static List<MealWithExceed> filteredByCountDownLatch(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) throws InterruptedException {
        Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        List<MealWithExceed> mealsTo = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latchCycles = new CountDownLatch(meals.size());
        CountDownLatch latchTasks = new CountDownLatch(meals.size());
        for (Meal meal : meals) {
            caloriesSumByDate.merge(meal.getDateTime().toLocalDate(), meal.getCalories(), Integer::sum);
            if (isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime)) {
                new Thread(() -> {
                    try {
                        latchCycles.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mealsTo.add(createTo(meal, caloriesSumByDate.get(meal.getDateTime().toLocalDate()) > caloriesPerDay));
                    latchTasks.countDown();
                }).start();
            } else {
                latchTasks.countDown();
            }
            latchCycles.countDown();
        }
        latchTasks.await();
        return mealsTo;
    }

    public static List<MealWithExceed> filteredByPredicate(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> caloriesSumByDate = new HashMap<>();
        List<MealWithExceed> mealsTo = new ArrayList<>();

        Predicate<Boolean> predicate = b -> true;
        for (Meal meal : meals) {
            caloriesSumByDate.merge(meal.getDateTime().toLocalDate(), meal.getCalories(), Integer::sum);
            if (TimeUtil.isBetweenHalfOpen(meal.getDateTime().toLocalTime(), startTime, endTime)) {
                predicate = predicate.and(b -> mealsTo.add(createTo(meal, caloriesSumByDate.get(meal.getDateTime().toLocalDate()) > caloriesPerDay)));
            }
        }
        predicate.test(true);
        return mealsTo;
    }

    public static List<MealWithExceed> filteredByConsumerChain(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Map<LocalDate, Integer> caloriesPerDays = new HashMap<>();
        List<MealWithExceed> result = new ArrayList<>();
        Consumer<Void> consumer = dummy -> {
        };

        for (Meal meal : meals) {
            caloriesPerDays.merge(meal.getDate(), meal.getCalories(), Integer::sum);
            if (TimeUtil.isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
                consumer = consumer.andThen(dummy -> result.add(createTo(meal, caloriesPerDays.get(meal.getDateTime().toLocalDate()) > caloriesPerDay)));
            }
        }
        consumer.accept(null);
        return result;
    }

    private static List<MealWithExceed> filteredByFlatMap(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        Collection<List<Meal>> list = meals.stream()
                .collect(Collectors.groupingBy(Meal::getDate)).values();

        return list.stream()
                .flatMap(dayMeals -> {
                    boolean exceed = dayMeals.stream().mapToInt(Meal::getCalories).sum() > caloriesPerDay;
                    return dayMeals.stream().filter(meal ->
                            isBetweenHalfOpen(meal.getTime(), startTime, endTime))
                            .map(meal -> createTo(meal, exceed));
                }).collect(toList());
    }

    private static List<MealWithExceed> filteredByCollector(List<Meal> meals, LocalTime startTime, LocalTime endTime, int caloriesPerDay) {
        final class Aggregate {
            private final List<Meal> dailyMeals = new ArrayList<>();
            private int dailySumOfCalories;

            private void accumulate(Meal meal) {
                dailySumOfCalories += meal.getCalories();
                if (isBetweenHalfOpen(meal.getTime(), startTime, endTime)) {
                    dailyMeals.add(meal);
                }
            }

            // never invoked if the upstream is sequential
            private Aggregate combine(Aggregate that) {
                this.dailySumOfCalories += that.dailySumOfCalories;
                this.dailyMeals.addAll(that.dailyMeals);
                return this;
            }

            private Stream<MealWithExceed> finisher() {
                final boolean exceed = dailySumOfCalories > caloriesPerDay;
                return dailyMeals.stream().map(meal -> createTo(meal, exceed));
            }
        }

        Collection<Stream<MealWithExceed>> values = meals.stream()
                .collect(Collectors.groupingBy(Meal::getDate,
                        Collector.of(Aggregate::new, Aggregate::accumulate, Aggregate::combine, Aggregate::finisher))
                ).values();

        return values.stream().flatMap(identity()).collect(toList());
    }

    private static MealWithExceed createTo(Meal meal, boolean exceed) {
        return new MealWithExceed(meal.getId(), meal.getDateTime(), meal.getDescription(), meal.getCalories(), exceed);
    }
}
