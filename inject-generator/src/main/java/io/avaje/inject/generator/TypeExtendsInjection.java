package io.avaje.inject.generator;

import io.avaje.inject.Bean;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.*;

/**
 * Read points for field injection and method injection
 * on baseType plus inherited injection points.
 */
class TypeExtendsInjection {

  private MethodReader injectConstructor;
  private final List<MethodReader> otherConstructors = new ArrayList<>();
  private final List<MethodReader> factoryMethods = new ArrayList<>();
  private final List<FieldReader> injectFields = new ArrayList<>();
  private final Map<String, MethodReader> injectMethods = new LinkedHashMap<>();
  private final Set<String> notInjectMethods = new HashSet<>();

  private final TypeElement baseType;
  private final ProcessingContext context;
  private final boolean factory;
  private Element postConstructMethod;
  private Element preDestroyMethod;

  TypeExtendsInjection(TypeElement baseType, ProcessingContext context, boolean factory) {
    this.baseType = baseType;
    this.context = context;
    this.factory = factory;
  }

  void read(TypeElement type) {
    for (Element element : type.getEnclosedElements()) {
      switch (element.getKind()) {
        case CONSTRUCTOR:
          readConstructor(element, type);
          break;
        case FIELD:
          readField(element);
          break;
        case METHOD:
          readMethod(element, type);
          break;
      }
    }
  }

  private void readField(Element element) {
    Inject inject = element.getAnnotation(Inject.class);
    if (inject != null) {
      injectFields.add(new FieldReader(element));
    }
  }

  private void readConstructor(Element element, TypeElement type) {
    if (type != baseType) {
      // only interested in the top level constructors
      return;
    }

    ExecutableElement ex = (ExecutableElement) element;
    MethodReader methodReader = new MethodReader(context, ex, baseType).read();
    Inject inject = element.getAnnotation(Inject.class);
    if (inject != null) {
      injectConstructor = methodReader;
    } else {
      if (methodReader.isNotPrivate()) {
        otherConstructors.add(methodReader);
      }
    }
  }

  private void readMethod(Element element, TypeElement type) {
    ExecutableElement methodElement = (ExecutableElement) element;
    if (factory) {
      Bean bean = element.getAnnotation(Bean.class);
      if (bean != null) {
        addFactoryMethod(methodElement, bean);
      }
    }
    Inject inject = element.getAnnotation(Inject.class);
    final String methodKey = methodElement.getSimpleName().toString();
    if (inject != null && !notInjectMethods.contains(methodKey)) {
      if (!injectMethods.containsKey(methodKey)) {
        MethodReader methodReader = new MethodReader(context, methodElement, type).read();
        if (methodReader.isNotPrivate()) {
          injectMethods.put(methodKey, methodReader);
        }
      }
    } else {
      notInjectMethods.add(methodKey);
    }
    if (AnnotationUtil.hasAnnotationWithName(element, "PostConstruct")) {
      postConstructMethod = element;
    }
    if (AnnotationUtil.hasAnnotationWithName(element, "PreDestroy")) {
      preDestroyMethod = element;
    }
  }

  private void addFactoryMethod(ExecutableElement methodElement, Bean bean) {
    // Not yet reading Qualifier annotations, Named only at this stage
    Named named = methodElement.getAnnotation(Named.class);
    factoryMethods.add(new MethodReader(context, methodElement, baseType, bean, named).read());
  }

  List<FieldReader> getInjectFields() {
    List<FieldReader> list = new ArrayList(injectFields);
    Collections.reverse(list);
    return list;
  }

  List<MethodReader> getInjectMethods() {
    List<MethodReader> list = new ArrayList<>(injectMethods.values());
    Collections.reverse(list);
    return list;
  }

  List<MethodReader> getFactoryMethods() {
    return factoryMethods;
  }

  Element getPostConstructMethod() {
    return postConstructMethod;
  }

  Element getPreDestroyMethod() {
    return preDestroyMethod;
  }

  MethodReader getConstructor() {
    if (injectConstructor != null) {
      return injectConstructor;
    }
    if (otherConstructors.size() == 1) {
      return otherConstructors.get(0);
    }
    // check if there is only one public constructor
    List<MethodReader> allPublic = new ArrayList<>();
    for (MethodReader ctor : otherConstructors) {
      if (ctor.isPublic()) {
        allPublic.add(ctor);
      }
    }
    if (allPublic.size() == 1) {
      // fallback to the single public constructor
      return allPublic.get(0);
    }
    return null;
  }
}
