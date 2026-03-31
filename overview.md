# Overview

> We're not making `spin` to be average, we making it to be awesome! 

The following document introduces `spin`, outlines its core concepts, terminology the philosophies. This is 
foundation knowledge for developers adopting `spin`, and especially those wishing to understand its implementation,
or develop `spin` *Extensions*.

# Introduction

`spin` is a program inference and execution engine.  It's designed to *infer* and then *execute* programs to accomplish 
some specified or inferred goals based on the environment in which it is launched. 

While `spin` is not a build tool, with the appropriate extensions, it is very good at compiling, testing, documenting 
and packaging software, without the need to develop the traditional build scripts or programs to do so.

Technically speaking, the core of `spin` doesn't provide any extensions or configuration, apart from customizing the 
operation of the inference and execution engine itself.  All capabilities are instead provided by Extensions, and 
without these `spin` would not be able to infer or execute any programs.  Fortunately, to ease adoption of `spin`, it's 
default distribution includes a number of *certified* extensions, that the `spin` team develop and maintiain, that
are especially designed for Java-based development. 

# How it works?

...

# Terminology

### Project

* A *Path* to a folder defining zero or many files, folders and other associated content for which one or more 
  *Resources* and/or *Plugins* have been detected as applicable by an *Engine*.

Projects are **hierarchical** in nature.  They may have zero or many *Child Projects* and optionally a single 
*Parent Project*.

Projects without a parent are often called *Root Projects* or *Workspaces*.

Projects may contain any number of nested sub-folders to structure content.  Sub-folders themselves may be considered 
Projects when one or more Resources and/or Plugins detect them as applicable.  Sub-folders that aren't Projects are 
thus simply means to structure content.

> `spin` is unaware of the nature of folders, structure and their content.  It is unable by itself to determine whether 
> folders define Projects, or their relationship to each other.  It is only through the use of discovered 
> or specified Resources and Plugins that `spin` can determine the presence, structure and nature of Projects.

### Workspace

* A Project for which no Parent Project has been discovered.

Workspaces are often called the *Root Projects*.

> There is no semantic difference between the capabilities of Workspaces and Projects, except that Workspaces don't
> have a parent project.

### Engine

* A facility providing Workspace and Project discovery through the use of *Extensions*, together with *Program* 
  inference and execution.

### Extension

* Provides functionality and capabilities to an Engine.

Examples of an Extension include *Services*, *Resources* and *Plugins*.

### Service

* An Extension that can be **injected** into all Resources, Plugins and *Tasks*, **across all** Projects in a
  Workspace. 

Services are objects that are typically used across different types of Projects, Resources, Plugins and Tasks.  A good
example is the *Java Platform* Service, that can provide information about and access to the currently installed Java 
Development Kits.    

### Server

* A type of Service that can be *started* by, and can request the Spin Command Line Application Interface (CLI) to 
 *stop* when it's running in *Server Mode*.

Servers are objects that are used to provide back-ground processing across different types of Projects, Resources,
Plugins and Tasks created by an Engine.

### Resource

* A Project-specific Extension that may be **injected** into Plugins and Tasks for Project in which the
  Resource is discovered, together with Plugin and Tasks of descendant Projects.

Unlike Services, that are injectable into **all** Resources, Plugins and Tasks in a Workspace, Resources 
**are Project specific**.  Only Resources discovered for a Project are injectable into said Project and 
descendants, but not other Projects.

### Daemon

* A type of Resource that is *started* by, and can request the Spin Command Line Application Interface (CLI) to *stop*
  when it's running in *Server Mode*.

### Plugin

* An Extension defining one or more related Tasks that may be invoked and executed in a Project.

Plugins themselves aren't executable.  They instead define the Tasks and provide Task-level shared state for a Project.  

### Task

* A well-defined unit of work, defined by a Plugin, that produces an **immutable asset**, once 
  executed in a Project, as part of a Program.

Tasks are executed once-and-only-once in a Program for a Project.  Tasks may have dependencies on other
Tasks in order for them to be executed.  Tasks that must be performed before and/or provide input into 
other Tasks are called *Dependent Tasks*.

Tasks may also be specified to pre-process the input of, or post-process the assets produced by other Tasks.  Tasks  
that perform pre or post-processing are called *Codependent Tasks*.

Tasks may not have dependencies on themselves, or tasks that eventually have dependencies on said task.  These
are *cyclic-dependencies*, and are not permitted.  Programs can't be inferred containing such Tasks definitions.

The result of executing a Task is called an *Asset* for the Program in which the Task was executed.

### Program
...

### Invocable

* Captures, defines and represents Project specific information concerning the execution of a specific Task in said
  Project. 

Importantly, Invocables are used to determine the dependencies on other Invocable Tasks, both with in a project and 
across a Workspace.  This information is then used to construct *Programs* and determine the order of execution of 
their *Instructions*.

### Instruction
...

### Asset

* The immutable result produced by executing an Invocable Task.

# Extension Discovery

... 

### Service Discovery
...

### Resource Discovery
...

### Plugin Discovery
...

# Program Inference
...

# Telemetry
...


