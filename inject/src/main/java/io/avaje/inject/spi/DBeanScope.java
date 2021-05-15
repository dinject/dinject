package io.avaje.inject.spi;

import io.avaje.inject.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static io.avaje.inject.spi.KeyUtil.key;

class DBeanScope implements BeanScope {

  private static final Logger log = LoggerFactory.getLogger(DBeanScope.class);

  private final ReentrantLock lock = new ReentrantLock();
  private final List<BeanLifecycle> lifecycleList;
  private final DBeanMap beans;
  private final Map<String, RequestScopeMatch<?>> reqScopeProviders;

  private boolean closed;

  DBeanScope(List<BeanLifecycle> lifecycleList, DBeanMap beans, Map<String, RequestScopeMatch<?>> reqScopeProviders) {
    this.lifecycleList = lifecycleList;
    this.beans = beans;
    this.reqScopeProviders = reqScopeProviders;
  }

  @Override
  public RequestScopeBuilder newRequestScope() {
    return new DRequestScopeBuilder(this);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> RequestScopeMatch<T> requestProvider(Class<T> type, String name) {
    return (RequestScopeMatch<T>) reqScopeProviders.get(key(type, name));
  }

  @Override
  public <T> T get(Class<T> beanClass) {
    return get(beanClass, null);
  }

  @Override
  public <T> BeanEntry<T> candidate(Class<T> type, String name) {
    // sort candidates by priority - Primary, Normal, Secondary
    EntrySort<T> entrySort = new EntrySort<>();
    entrySort.add(beans.candidate(type, name));
    return entrySort.get();
  }

  @Override
  public <T> T get(Class<T> beanClass, String name) {
    BeanEntry<T> candidate = candidate(beanClass, name);
    return (candidate == null) ? null : candidate.getBean();
  }

  @Override
  public <T> List<T> list(Class<T> interfaceType) {
    List<T> list = new ArrayList<>();
    beans.addAll(interfaceType, list);
    return list;
  }

  @Override
  public <T> List<T> listByPriority(Class<T> interfaceType) {
    return listByPriority(interfaceType, Priority.class);
  }

  @Override
  public <T> List<T> listByPriority(Class<T> interfaceType, Class<? extends Annotation> priorityAnnotation) {
    List<T> list = getBeans(interfaceType);
    return list.size() > 1 ? sortByPriority(list, priorityAnnotation) : list;
  }

  @Override
  public <T> List<T> sortByPriority(List<T> list) {
    return sortByPriority(list, Priority.class);
  }

  @Override
  public <T> List<T> sortByPriority(List<T> list, final Class<? extends Annotation> priorityAnnotation) {
    boolean priorityUsed = false;
    List<SortBean<T>> tempList = new ArrayList<>(list.size());
    for (T bean : list) {
      SortBean<T> sortBean = new SortBean<>(bean, priorityAnnotation);
      tempList.add(sortBean);
      if (!priorityUsed && sortBean.priorityDefined) {
        priorityUsed = true;
      }
    }
    if (!priorityUsed) {
      // nothing with Priority annotation so return original order
      return list;
    }
    Collections.sort(tempList);
    // unpack into new sorted list
    List<T> sorted = new ArrayList<>(tempList.size());
    for (SortBean<T> sortBean : tempList) {
      sorted.add(sortBean.bean);
    }
    return sorted;
  }

  @Override
  public List<Object> getBeansWithAnnotation(Class<?> annotation) {
    List<Object> list = new ArrayList<>();
    beans.addAll(annotation, list);
    return list;
  }

  @Override
  public void start() {
    lock.lock();
    try {
      log.trace("firing postConstruct");
      for (BeanLifecycle bean : lifecycleList) {
        bean.postConstruct();
      }
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    lock.lock();
    try {
      if (!closed) {
        // we only allow one call to preDestroy
        closed = true;
        log.trace("firing preDestroy");
        for (BeanLifecycle bean : lifecycleList) {
          bean.preDestroy();
        }
      }
    } finally {
      lock.unlock();
    }
  }

  static class EntrySort<T> {

    private BeanEntry<T> supplied;
    private BeanEntry<T> primary;
    private int primaryCount;
    private BeanEntry<T> secondary;
    private int secondaryCount;
    private BeanEntry<T> normal;
    private int normalCount;

    private final List<BeanEntry<T>> all = new ArrayList<>();

    void add(BeanEntry<T> candidate) {
      if (candidate == null) {
        return;
      }
      if (candidate.isSupplied()) {
        // a supplied bean trumps all
        supplied = candidate;
        return;
      }
      all.add(candidate);
      if (candidate.isPrimary()) {
        primary = candidate;
        primaryCount++;
      } else if (candidate.isSecondary()) {
        secondary = candidate;
        secondaryCount++;
      } else {
        normal = candidate;
        normalCount++;
      }
    }

    BeanEntry<T> get() {
      if (supplied != null) {
        return supplied;
      }
      if (primaryCount > 1) {
        throw new IllegalStateException("Multiple @Primary beans when only expecting one? Beans: " + all);
      }
      if (primaryCount == 1) {
        return primary;
      }
      if (normalCount > 1) {
        throw new IllegalStateException("Multiple beans when only expecting one? Maybe use @Primary or @Secondary? Beans: " + all);
      }
      if (normalCount == 1) {
        return normal;
      }
      if (secondaryCount > 1) {
        throw new IllegalStateException("Multiple @Secondary beans when only expecting one? Beans: " + all);
      }
      return secondary;
    }
  }

  private static class SortBean<T> implements Comparable<SortBean<T>> {

    private final T bean;

    private boolean priorityDefined;

    private final int priority;

    SortBean(T bean, Class<? extends Annotation> priorityAnnotation) {
      this.bean = bean;
      this.priority = initPriority(priorityAnnotation);
    }

    int initPriority(Class<? extends Annotation> priorityAnnotation) {
      // Avoid adding hard dependency on javax.annotation-api by using reflection
      try {
        Annotation ann = bean.getClass().getDeclaredAnnotation(priorityAnnotation);
        if (ann != null) {
          int priority = (Integer) priorityAnnotation.getMethod("value").invoke(ann);
          priorityDefined = true;
          return priority;
        }
      } catch (Exception e) {
        // If this happens, something has gone very wrong since a non-confirming @Priority was found...
        throw new UnsupportedOperationException("Problem instantiating @Priority", e);
      }
      // Default priority as per javax.ws.rs.Priorities.USER
      // User-level filter/interceptor priority
      return 5000;
    }

    @Override
    public int compareTo(SortBean<T> o) {
      return Integer.compare(priority, o.priority);
    }
  }
}