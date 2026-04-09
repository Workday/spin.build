# `spin` Coding Conventions & Guidelines

This following outlines the coding conventions and guidelines for `spin` development.

> The `spin` code-base may not yet be entirely compliant to these guidelines.  Developers should thus make an
> effort to bring non-compliant code inline with these guidelines, especially when significant divergence is discovered.
>
> All **new code** should follow these guidelines.

* Code should be formatted according to the [Google Java Coding Style](https://google.github.io/styleguide/javaguide.html).

* Checkstyle is used to enforce basic style compliance, however it can't do everything.  Hence this guide.

## Semantic Versioning

`spin` is strictly versioned using [Semantic Versioning](https://semver.org).  Importantly, we're not concerned with
increasing *major* version numbers when necessary to move things forward.

## Functional Style

`spin` is developed in Java using [*Functional Style*](https://www.baeldung.com/java-functional-programming).

The following article is a great introduction to this style of development, specifically using Java.

* [A new Java functional style](https://theboreddev.com/a-new-java-functional-style/)

In a nutshell, the style means:

* Prefer *immutability*.  Where possible make all state immutable.  Where this is not possible, use the 
  [Builder Pattern](https://en.wikipedia.org/wiki/Builder_pattern) pattern to capture mutable state, after which 
  *building* produces immutable objects.

* Never use `null`.  Variables (member, method, parameters) must never accept or store `null` values.  The only 
  exception being when this is impossible when integrating with 3rd-party libraries.

* Use `Stream`s. Avoid use of `Array`s, `Collection`s, `Iterator`s and/or `Iterable`s, especially in interfaces and
  method signatures.

* Never define `static` stateful global variables, unless they are constants.

* Use Dependency Injection to construct Objects, and avoid using `new` where possible.

* Avoid using `getters` to obtain state from other objects when it can or should be *injected*.

* Never define or use global static singletons.

## Package Names

* Package Names should always be singular and not plural.

## Interface Names

* Interface names **must not** be prefixed with an `I`.

For example: An interface for a Truck should not be called `ITruck`, but instead `Truck`.

## Class Names

* Class names **must not** be post-fixed with `Impl` or `DAO` etc.

For example: A class implementing the `Truck` interface should not be called `TruckImpl`.

* Classes that are the only implementation of an interface should be prefixed with `Default` as they
represent the default implementation.

For example: A class implementing the `Truck` interface should not be called `DefaultTruck`.

* Abstract classes must be prefixed with `Abstract`, simply to indicate that one should not cast to them.

For example: An abstract class implementing the `Truck` interface should be called `AbstractTruck`.

## Method Names

### Property Method Names

* Method names for methods that return immutable properties, for which there is no **set**ter method on
the defining class, are **not** required to prefix the method name with **get**.  There is no requirement
to use Java Bean-style method naming conventions for immutable property getters.

For example:  The method name for an immutable "age" property on the class should be called
`age()` and not `getAge`.

* Method names for methods with mutable values **must* adopt Java Bean-style method naming conventions, and
thus define **both** `set` and `get` methods.

For example:  The method names for a mutable "age" property on the class should simply be called
`getAge()` and `setAge(...)`.

### Optional, Exceptional, Stream, Streamable, Iterable and Iterator Method Names

* Methods that return `Optional`, `Exceptional`, `Stream`, `Streamable`, `Iterator`, `Iterable` or any other immutable collection,
should avoid using prefixing method names with `get`.  There is no requirement to use Java Bean-style
method naming conventions for such methods.

For example:  A method that returns a `Stream` of `Address`es, should have the signature
`Stream<Address> addresses()` and not `Stream<Address> getAddresses()`.

## Constructors

* Where possible, make *constructors* `private` or package `protected`.  This is to ensure that the `new` operator is
  only available for internal implementation use.

* To support `public` construction of classes, the `Builder` pattern and/or `public static` creation helper methods
  should be adopted.  

This pattern allows:

1. Constructors to be refactored without breaking the public API of classes.
2. Caching of immutable object-creation requests.

## Class Layout

Class declarations should be organized in the following order:

1. `public` `static` `final` constants
2. `private` `static` `final` constants
3. `private` `final` members
4. `private` members
5. default constructor
6. constructors
7. methods (non-interface)
8. overridden methods
9. `static` methods
10. `static` types

## null Policy

* The use of `null` should be avoided at all times!   This is to prevent `NullPointerException`s being produced.  
* This includes accepting or requiring them as input parameters, and returning them from methods.
* Thus, there's no requirement for `@Nullable` or `@NotNullable`-style annotations appearing in interfaces or implementations.

## Logging Policy

* `spin` contains **zero logging**.  It does not use any logging framework, including that of the Java Platform.
* To provide information and feedback to users and developers, `spin` instead uses a Telemetry Framework, that allows
  clean integration with Integrated Development Environments (IDEs) and Commandline Tooling, which themselves define
  how the logging of Telemetry occurs.

## Dependency Injection

* Dependency Injection is used extensively in the `spin` code base.  It should be used where ever possible
