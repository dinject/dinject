package org.example.missing;

import jakarta.inject.Singleton;

@Singleton // improve error when @Singleton missing
public class MFoo {

//  final Object more;
//
//  public MFoo(Object more) {
//    this.more = more;
//  }

  public String mf() {
    return "mf";
  }
}
