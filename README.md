spin
====

Welcome to `spin`, a Modular Java-based Program Inference and Execution Engine for the purpose of automating modular 
build execution.

The goal of `spin` is to provide the necessary infrastructure to:

1. Compile, test, debug, package and deploy Modular (Java) Applications
2. Integrate with VSCode and/or other Language Server-based Development Environments to support rich IDE
   experiences.

> More information about `spin`, philosophy and concepts is covered in the `spin` [overview](overview.md).

Building
========

### Prerequisites

To bootstrap, build and run `spin` you **MUST** do the following:

1. **Install** *both* the Java 8 **and** 25 Java Development Kits.

We recommend using [Azul Open JDK distributions](https://www.azul.com/downloads/zulu-community/?package=jdk).

>  `spin` is written using Java 25 and thus to develop, compile, test, Java 25 **is required**.
   
>  `spin` is also designed to work with Java 8, thus for testing it, `spin` also **requires** a Java 8 Development Kit to be installed.

> `spin` will automatically determine where Java Development Kits are installed, however it currently **won't** install them for you!

2. **Create and Configure** an Apache Maven [Settings](https://maven.apache.org/configure.html) file in `~/.m2/settings.xml`  
 
This allows `spin` to resolve dependencies, including those dynamically requested by Plugins.  
   
> `spin` internally uses the Apache Maven libraries (not the commandline) to resolve dependencies and interact 
> with Artifact Repositories, including Maven Central.

### Building the Code

To initially bootstrap `spin` (without using itself), the Apache Maven (wrapper) is used.  Once this is 
achieved, `spin` can be used for building itself and anything else you would like to `spin`.

The following shell command, executed in the top-level `spin` folder, will request Apache Maven to build
`spin`: 

   `./mvnw clean package`

This will compile, test and package `spin`.  Once this has successfully completed the following assets 
will be available.

1. The `./spin.build/spin/.build/spin` folder will contain a custom Java Runtime Image for `spin`
   (produced by jlink).

2. The `./spin.build/spin/target/` folder will contain a `spin-<version>-bin.zip` file of the 
   aforementioned Java Runtime Image.

You can move this folder into your executable applications area, set a path to `spin/bin`, and
then execute `spin` using:

   `./spin.sh` 

in any folder.

For example, after this executing the following, within the `spin` source tree, will cause `spin` to build itself.

   `./spin.sh clean package`

### Code Formatting and Conventions

* To configure code formatting for IntelliJ, please import the scheme defined at `configs/idea/intellij-idea-workday-java-code-style.xml`.
* To configure checkstyle, use the configuration in `checkstyle/checkstyle.xml`

* The coding-conventions are documented [here](coding-conventions.md).  **Please Follow Them!**

