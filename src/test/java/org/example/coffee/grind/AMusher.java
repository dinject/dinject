package org.example.coffee.grind;

import io.dinject.core.BeanLifecycle;

import javax.inject.Singleton;

@Singleton
public class AMusher implements BeanLifecycle {

  private int countInit;
  private int countClose;

  String mush() {
    return "amush";
  }

  @Override
  public void postConstruct() {
    countInit++;
  }

  @Override
  public void preDestroy() {
    countClose++;
  }

  int getCountInit() {
    return countInit;
  }

  int getCountClose() {
    return countClose;
  }
}
