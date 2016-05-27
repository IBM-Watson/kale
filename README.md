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

### Example run

Here is an unedited run through of provisioning commands to prepare a
Solr collection as a target for running the Data Crawler.

After downloading `kale` from
[releases](https://github.com/IBM-Watson/kale/releases) I opened a
terminal window. The `$` is my command prompt. I typed each command
shown on the lines starting with `$`. All other text is output from
running `kale`.

```
$ alias 'kale=java -jar /Users/ba/Downloads/kale-1.4.0-standalone.jar'
$ kale login
Username? ba@us.ibm.com
Endpoint (default: https://api.ng.bluemix.net)?
Using endpoint 'https://api.ng.bluemix.net'
Password?
Logging in...
Using org 'ba@us.ibm.com'
Using space 'dev'
Loading services...
Log in successful!

Current environment:
   user:                         ba@us.ibm.com
   endpoint:                     https://api.ng.bluemix.net
   org:                          ba@us.ibm.com
   space:                        dev

$ kale create space example

Space 'example' has been created and selected for future actions.

$ kale create document_conversion example-dc
Creating document_conversion service 'example-dc' using the 'standard' plan.
Creating key for service 'example-dc'.

Service 'example-dc' has been created and selected for future actions.

$ kale create retrieve_and_rank example-rnr
Creating retrieve_and_rank service 'example-rnr' using the 'standard' plan.
Creating key for service 'example-rnr'.

Service 'example-rnr' has been created and selected for future actions.

$ kale create cluster example-cluster
Creating cluster 'example-cluster' in 'example-rnr'.

Cluster 'example-cluster' has been created and selected for future actions.
It will take a few minutes to become available.

$ kale list services
Available services in the 'example' space:
   [standard] document_conversion service named: example-dc

   [standard] retrieve_and_rank service named: example-rnr
      Cluster name: example-cluster, size: free, status: NOT_AVAILABLE

Currently using the following selections:
   document_conversion service:  example-dc
   retrieve_and_rank service:    example-rnr
   cluster:                      example-cluster

$ kale list services
Available services in the 'example' space:
   [standard] document_conversion service named: example-dc

   [standard] retrieve_and_rank service named: example-rnr
      Cluster name: example-cluster, size: free, status: READY
         configs:
         collections:

Currently using the following selections:
   document_conversion service:  example-dc
   retrieve_and_rank service:    example-rnr
   cluster:                      example-cluster

$ kale create solr-config english
Creating configuration 'english' in 'example-rnr/example-cluster'.

Solr configuration named 'english' has been created and selected for future actions.

$ kale create collection example-collection
Creating collection 'example-collection' in 'example-rnr/example-cluster' using config 'english'.

Collection 'example-collection' has been created and selected for future actions.

$ kale create crawler-config

Created two files for setting up the Data Crawler:
    'orchestration_service.conf' contains document_conversion service connection information.
    'orchestration_service_config.json' contains configurations sent to the 'index_document' API call.
```

The two files created at the end of this run,
`orchestration_service.conf` and `orchestration_service_config.json`
can be dropped, unmodified, into a Data Crawler configuration to point
the crawler run at the services we just created.

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

## Contributing

We are thrilled you are considering contributing to <code>kale</code>!
Please read our [guidelines for contributing](CONTRIBUTING.md).

## Licensing

:copyright: Copyright IBM Corp. 2016

All code contained within this project repository or any
subdirectories is licensed according to the terms of the MIT license,
which can be viewed in the file [LICENSE](LICENSE).

## Open Source @ IBM
[Find more open source projects on the IBM Github Page](http://ibm.github.io/)
