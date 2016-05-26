# Kale

`kale` is a command line tool for provisioning and configuring the [IBM Watson
Retrieve and Rank
Service](http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/retrieve-rank.html)
and the [IBM Watson Document Conversion
Service](http://www.ibm.com/smarterplanet/us/en/ibmwatson/developercloud/document-conversion.html).

## Use: download and run

### Download

Download the `kale` tool from our
[releases](https://github.com/IBM-Watson/kale/releases).

### Setup and run

Recommendation: create a short alias to setup a command named `kale`.

On Linux, OSX and other Unix-style systems, the `alias` command can be used like this:

    alias kale="java -jar /full/path/to/kale-1.4.0-standalone.jar"

And on Windows, the `doskey` command can be used like this:

    doskey kale=java -jar C:\full\path\to\kale-1.4.0-standalone.jar $*

Now the `kale` command should be available. Try:

    kale help

## Development

### Preparation for build, test and run

Get the Leiningen tool, `lein`, from http://leiningen.org/, and put it
on your PATH. On Mac OSX, `brew install leiningen` works well. Most
Linux package managers do not do a good job of packaging Leiningen, so
please do the direct installation from http://leiningen.org/.

### Run tests

```bash
$ lein test
```

Or, to run the tests and produce a code coverage report:

```bash
$ lein cloverage
```

### Development run of the tool itself

```bash
$ lein run <command>
```

### Building a `jar`

```bash
$ lein uberjar
Compiling ...
...
Created .../kale/target/uberjar/kale-1.5.0-SNAPSHOT.jar
Created .../kale/target/uberjar/kale-1.5.0-SNAPSHOT-standalone.jar
```

Now you can run the tool with a simple `java` command line:

```bash
$ java -jar target/uberjar/kale-1.5.0-SNAPSHOT-standalone.jar
```

To make running `kale` easier, it's recommended that you create an alias (see
instructions under ["Setup and run"](#setup-and-run)).

## Licensing

All code contained within this project repository or any
subdirectories is licensed according to the terms of the MIT license,
which can be viewed in the file [LICENSE](LICENSE).

## Open Source @ IBM
[Find more open source projects on the IBM Github Page](http://ibm.github.io/)
