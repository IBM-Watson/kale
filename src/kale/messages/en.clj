;;
;; (C) Copyright IBM Corp. 2016 All Rights Reserved.
;;

(ns kale.messages.en
  (:require [kale.version :refer [kale-version]]))

(def ^:const new-line (System/getProperty "line.separator"))

(def messages
  "English output text, keyed by area."
  {:help-messages
   {:help (str "
██╗  ██╗ █████╗ ██╗     ███████╗
██║ ██╔╝██╔══██╗██║     ██╔════╝
█████╔╝ ███████║██║     █████╗
██╔═██╗ ██╔══██║██║     ██╔══╝
██║  ██╗██║  ██║███████╗███████╗
╚═╝  ╚═╝╚═╝  ╚═╝╚══════╝╚══════╝
Version " (kale-version) "

The command line administration tool for Enhanced Information
Retrieval.

The tool is used for the deployment of the Watson Document
Conversion service and the Watson Retrieve and Rank service.
Solr collections can be created on Retrieve and Rank services
and crawler configurations can be created for the Data
Crawler to index document data into these collections.

Commands:
    kale login
    kale logout

    kale create space <name>
    kale create document_conversion <name>
    kale create retrieve_and_rank <name>
    kale create cluster <name>
    kale create solr-configuration english|german|spanish
    kale create collection <name>
    kale create crawler-configuration

    kale delete space <name>
    kale delete document_conversion <name>
    kale delete retrieve_and_rank <name>
    kale delete cluster <name>
    kale delete solr-configuration <name>
    kale delete collection <name>

    kale select organization <name>
    kale select space <name>
    kale select document_conversion <service-name>
    kale select conversion-configuration <file.json>
    kale select retrieve_and_rank <service-name>
    kale select cluster <cluster-name>
    kale select solr-configuration <config-name>
    kale select collection <collection-name>

    kale list
    kale list organizations
    kale list spaces
    kale list services

    kale refresh
    kale assemble <name> english|german|spanish
    kale get solr-configuration <name>
    kale dry_run <file>
    kale search <query>

Help on individual commands can be found by running:
    kale help <command>

There are two global options for each command:
  --trace   Output logging for any API calls made to Bluemix and
            Watson services.
  --help    Retrieve help information on the specified command.
")

     :login
"The 'login' command connects the user to Bluemix and starts a
session for creating and managing services. A new user must log
in before doing anything else with the command line tool.

A set of user credentials can be acquired by creating an IBM ID
account on the Bluemix website.

The command uses a username, password and endpoint when logging the
user in. The term 'endpoint' here refers to the Bluemix region/data
center to manage services on. By default the endpoint is set to
https://api.ng.bluemix.net. Using the 'login' command without
arguments will prompt all three parameters.

To log in without prompting endpoint:
    kale login <endpoint>

To log in without prompting endpoint or username:
    kale login <endpoint> <username>

The password prompt can be bypassed by setting the environment
variable 'KALE_PASSWORD' to the user's password.

There is option for the 'login' command:
  --sso   Log in using a one-time password. The user will be provided
          a URL where they can acquire the password.
"

     :logout
"Logs the user out and clears credentials information from the
recent session. The only information that is retained the user
selections that were made during the session.

If you wish to completely clear all session information, delete
the kale-state.json file that is generated for the session.
"

     :create
"Creates a new space or service, or a new resource within a service.

Create a new space within Bluemix:
    kale create space <name>

Create a new service instance in Bluemix:
    kale create <service-type> <name>

Supported services and short names for them are:
    document_conversion - doc, conversion, conv, dc, d, c
    retrieve_and_rank   - retrieve, ret, rnr, r

Example of creating a service:
    kale create retrieve_and_rank cars

Services are provisioned under different service plans, which determine
how the user is charged for traffic and the resources available to
the services. By default services are provisioned using the 'standard'
plan. The 'premium' plan provides improved resources than the
typical 'standard' plan. Services are created using this plan by
setting the premium option when creating the service, eg:

    kale create <service-type> <name> --premium

Note that this plan is only available on orgs that allow for premium
provisioning.

The 'create' command can create clusters, Solr configurations and
collections within an instance of the Retrieve and Rank service.

Supported Retrieve and Rank resources and short names are:
    cluster            - clus, clu
    solr-configuration - configuration, config, conf
    collection         - coll

Each Retrieve and Rank resource being created needs to be given a
name. When there are more than one containing service or resource,
please specify which container to use when creating the new resource.

Without specifying a cluster size, a 'free' cluster size is created
where the user is not charged for its usage.  Users only receive
one 'free' cluster per Retrieve and Rank instance.  By specifying a
cluster size, non-free clusters are created, where the number
corresponds to the amount of resources available to the cluster.

    kale create cluster <name>
    kale create cluster <name> <cluster-size>

Cluster creation typically takes some time.  Kale by default will just start
the cluster creation process without waiting for the creation to complete.
By setting the '--wait' option the tool wait until the cluster is READY
before returning control back to the user.

Kale includes default Solr configurations for the following languages:
arabic, brazilian, german, english, spanish, french, italian, and japanese.
When creating a Solr configuration, one can either specify one of these
configurations or please specify the zip file containing the details of
the configuration being created.

    kale create configuration <langauge>
    kale create configuration <name> <solr-config.zip>

    kale create collection <name>

Once you have created a document_conversion service and a Solr
collection, you can create configuration files formatted for use by
the Data Crawler. This command:

    kale create crawler-configuration

creates two files that can be copied into the Data Crawler's
'config' directory.

    'orchestration_service.conf' contains document_conversion
                        service connection information.
    'orchestration_service_config.json' contains configurations
                        sent to the 'index_document' API call.
"

     :delete
"Delete a space, service or resource.

Delete a space within Bluemix:
    kale delete space <name>

Delete a service:
    kale delete document_conversion <name>
    kale delete retrieve_and_rank <name>

Resources within a Retrieve and Rank service can also be deleted.
Additional information may be needed if the resource name is not unique.

Supported Retrieve and Rank resources and short names are:
    cluster            - clus, clu
    solr-configuration - configuration, config, conf
    collection         - coll

    kale delete cluster <name>
    kale delete configuration <name>
    kale delete collection <name>

If an element contains resources (e.g. spaces with services, clusters
with collections), then a prompt will appear to verify the deletion.

There is one option for deletion:
  --yes Do not prompt the user about deleting; assume the answer is yes
"

     :select
"The 'select' commands allow you to choose a different organization or space.

    kale select organization <name>
    kale select space <name>

If you have multiple service instances available, you will need to
select which service you want to use. When there is a single instance
of a type of service, that instance will be selected automatically.

    kale select document_conversion <service-name>
    kale select retrieve_and_rank <service-name>

Similarly, if you have one cluster or collection available, it will be
selected automatically. If you have selected a collection, its
containing cluster and Retrieve and Rank service will also be
selected.

    kale select cluster <cluster-name>
    kale select collection <collection-name>

By default, an empty configuration is used for the Document Conversion
service.  A Document Conversion configuration file that you have created
can also be selected.

    kale select conversion-configuration <file.json>
"

     :dry-run
"Dry run of conversion for one or more files through the Document
Conversion service. The conversion is done via the 'dry_run' option to
the 'index_document' API of the Document Conversion service. 'dry_run'
is used to test how files will be converted before running the Data
Crawler to upload, convert and index large volumes of documents.

The converted files will be placed in the 'converted' directory
under your current working directory.

    kale dry_run reliability-study.doc

Will create a converted file: 'converted/reliability-study.doc.json'

Multiple files can be converted via:

    kale dry_run fileA.doc fileB.doc ...
"

     :refresh
"Reload service information from Bluemix.  This is useful for when
one may suspect that services and resources may have changed outside
the user's current session."

     :search
"Get results from querying a Retrieve and Rank collection.  This is
useful for testing what data is indexed in the collection.

   kale search <query>

Without specifying a query, the search will return all documents.
Queries should be in the form 'field:value'.  For example:

   kale search body:John

will return documents from the collection that contain the string 'John'
in the 'body' field of the document.  The available fields for searching
depend on the Solr configuration used to create the collection. Typically
there is at least a 'body' and 'title' field.

It is also possible to search using multiple terms with this command.
"

     :list-info
"The 'list' command displays information about the organizations,
spaces and services available currently to the user.

    kale list organizations
    kale list spaces
    kale list services

The command can also be used to list the environment and selections
that the user has made.

    kale list selections

Running the command without any subcommands ('kale list') will list
all of the above information.

For retrieve_and_rank service instances, additional detail
information is displayed: each cluster, and that cluster's
configurations and collections.

For 'kale list' and 'kale list services', there are two options for
displaying more information:
  --credentials displays each service's endpoint and credentials.
  --guid        displays each service's GUID.

For example:

>> kale list services -g
retrieve_and_rank service named: rnr-trial
    service GUID: 11111111-1111-1111-1111-111111111111
    Cluster name: doc, size: 1, status: READY
            configs: basic-config
            collections: documents

document_conversion service named: convert-1
    service GUID: 22222222-2222-2222-2222-222222222222

dialog service named: Dialog-lf
    service GUID: 33333333-3333-3333-3333-333333333333
"

     :get-command
"The 'get' command is used to pull file data from services in the
cloud to be saved on the user's local machine.  The command can be
used to download Solr configurations from Retrieve and Rank service
clusters.

    kale get solr-configuration <name>

By running the above command, a zip file containing the Solr
configuration will saved to your current working directory.
"

     :assemble
"The 'assemble' command is useful for running all the commands for
creating the two services and Solr collection for an Enhanced
Information Retrieval instance in a single operation:

    kale assemble <base-name> <langauge>
    kale assemble <base-name> <langauge> <cluster-size>
    kale assemble <base-name> <config-name> <solr-config.zip>
    kale assemble <base-name> <config-name> <solr-config.zip> <cluster-size>

The user provides a base name to determine the name of the components
being created. The values <language>, <config-name> and <solr-config.zip>
are the same parameters used to create a Solr configuration on a Solr
cluster. When <cluster-size> is not specified the command will use the
'free' size when creating a Solr cluster. A new space will be created to
store the components.

Services can be provisioned using the 'premium' plan by setting the
premium flag:

    kale assemble <base-name> <language> --premium

Note that 'premium' provisioning is not currently available for
Retrieve and Rank services.  Regardless of setting the premium flag,
these services will be provisioned using the 'standard' plan.
"

     :no-help-msg "I'm sorry. I don't have any help for '%s'."}

  :main-messages
    {:not-implemented "'%s' is not implemented yet."
     :please-login "Please login to use the '%s' command."}

  :common-messages
    {:unknown-language "Language '%s' is not available."
     :http-call "A call to: %s"
     :http-exception "triggered an unexpected exception:"
     :http-error-status "returned an unexpected error status code %s"
     :other-exception (str "Something unexpected failed while trying to "
                           "process your command. This exception was thrown:")

     :unknown-option "Unknown option: %s"
     :too-many-args
     "Too many arguments provided to '%s'. Please omit '%s'."
     :invalid-input "Invalid input."
     :missing-filenames "Please specify one or more file names."
     :unreadable-file "Cannot %s the file named '%s'."
     :unreadable-files "Cannot %s these files: %s"
     :read "read"
     :write-to "write to"
     :missing-action "Please specify what you want to %s."
     :unknown-action  "Don't know how to '%s %s'."
     :available-actions "Available actions for %s:"}

  :login-messages
    {:login-start "Logging in..."
     :loading-services "Loading services..."
     :login-done "Log in successful!"

     :using-username "Using username '%s'"
     :prompt-username "Username? "
     :prompt-username-default "Username (default: %s)? "
     :invalid-endpoint
     (str "WARNING: The parameter '%s' doesn't appear to be an endpoint."
          new-line "         Arguments to login are in the form "
          "'kale login <endpoint> <username>'")
     :using-endpoint "Using endpoint '%s'"
     :prompt-endpoint-default "Endpoint (default: %s)? "
     :using-password "Using password from environment variable 'KALE_PASSWORD'"
     :prompt-password "Password? "

     :no-orgs-in-region
     "Unable to find any available orgs for the given endpoint."
     :no-spaces-in-org
     "Unable to find any available spaces for the org."
     :alternative-org "Unable to find org '%s', using org '%s' instead"
     :using-org "Using org '%s'"
     :alternative-space "Unable to find space '%s', using space '%s' instead"
     :using-space "Using space '%s'"

     :logout-start "Logging out..."
     :logout-done "Log out successful!"}

  :create-messages
    {:missing-space-name "Please specify a name for the space."
     :space-created
     "Space '%s' has been created and selected for future actions."

     :creating-service "Creating %s service '%s' using the '%s' plan."
     :plan-not-available
     "Plan '%s' is not available for service type '%s' in this organization."
     :create-failed "Service creation failed."
     :creating-key "Creating key for service '%s'."
     :missing-service-name "Please specify a name for the service."
     :service-created
     "Service '%s' has been created and selected for future actions."

     :missing-cluster-name "Please specify the name of the cluster to create."
     :cluster-size "Cluster size must be an integer in the range of 1 to 99."
     :unknown-rnr-service
     (str "Couldn't determine which service to create the cluster in." new-line
          "Please create a retrieve_and_rank service or select "
          "an existing one.")
     :existing-cluster "A cluster named '%s' already exists."
     :creating-cluster "Creating cluster '%s' in '%s'."
     :waiting-on-cluster "Waiting for cluster to become ready."
     :still-waiting-on-cluster "Still waiting on cluster to become ready."
     :cluster-timed-out "Timed out waiting for cluster to become available."
     :cluster-created
     "Cluster '%s' has been created and selected for future actions."
     :cluster-created-soon
     (str "Cluster '%s' has been created and selected for future actions."
          new-line "It will take a few minutes to become available.")

     :missing-config-name
     "Please specify the name of the Solr configuration to create."
     :unknown-packaged-config
     (str "'%s' is not a prepackaged Solr configuration." new-line
          "Please select one of the prepackaged configurations, "
          "or specify" new-line
          "the name of a zip file containing the Solr configuration."
          new-line new-line
          "Available packages can be found by running 'kale help create'.")
     :unknown-cluster-config
     (str "Couldn't determine which cluster to create the configuration in."
          new-line "Please create a Solr cluster or select "
                   "an existing one.")
     :creating-config "Creating configuration '%s' in '%s/%s'."
     :config-created  (str "Solr configuration named '%s' has been created "
                           "and selected for future actions.")

     :missing-collection-name
     "Please specify the name of the collection to create."
     :unknown-cluster-collection
     (str "Couldn't determine which cluster to create the collection in."
          new-line "Please create a Solr cluster or select "
                   "an existing one.")
     :unknown-config
     (str "Couldn't determine which Solr configuration to use." new-line
          "Please upload a Solr configuration or select an existing one.")
     :creating-collection
     "Creating collection '%s' in '%s/%s' using config '%s'."
     :collection-created (str "Collection '%s' has been created"
                              " and selected for future actions.")

     :missing-item
     (str "Couldn't determine which %s to tell the crawler to use." new-line
          "Please create a %s or select an existing one.")
     :collection-item "collection"
     :dc-service-item "document_conversion service"
     :crawl-config-created
     (str "Created two files for setting up the Data Crawler:" new-line
          "    'orchestration_service.conf' contains document_conversion"
          " service connection information."  new-line
          "    'orchestration_service_config.json' contains"
          " configurations sent to the 'index_document' API call.")}

  :assemble-messages
    {:missing-base-name
     "Please specify the base name to use for the components."
     :missing-config-name
     "Please specify the name of the Solr configuration to create."
     :unknown-packaged-config
     (str "'%s' is not a prepackaged Solr configuration." new-line
          "Please select one of the prepackaged configurations, "
          "or specify" new-line
          "the name of a zip file containing the Solr configuration."
          new-line new-line
          "Available packages can be found by running 'kale help create'.")
     :unknown-cluster-config
     (str "Couldn't determine which cluster to create the configuration in."
          new-line "Please create a Solr cluster or select "
                   "an existing one.")
     :cluster-size "Cluster size must be an integer in the range of 1 to 99."

     :no-rnr-premium
     (str "Warning: The 'premium' plan is currently not available for"
          new-line
          "retrieve_and_rank services. Using the 'standard' plan instead.")
     :running-cmd "[Running command 'kale %s%s']"
     :failure
     (str "Unable to create Enhanced Information Retrieval instance '%s'"
          " due to errors.")
     :starting-rollback "[An error occurred, starting rollback]"
     :success
     "Enhanced Information Retrieval instance '%s' creation successful!"}

  :delete-messages
    {:missing-space-name "Please specify the name of the space to delete."
     :no-delete-current-space
     "You cannot delete the space you are currently working in."
     :unknown-space "No space named '%s' was found."
     :space-service-num "This space contains %d service(s)."
     :space-delete-confirm "Are you sure you want to delete space '%s'"
     :delete-cancel "Deletion cancelled."
     :space-deleted (str "Deletion initiated for space '%s'."
                         new-line "The space will be deleted shortly.")

     :missing-rnr-name
     "Please specify the name of the retrieve_and_rank service to delete."
     :unknown-rnr "No retrieve_and_rank service named '%s' was found."
     :not-rnr-service
     "The service named '%s' is not a retrieve_and_rank service."
     :rnr-cluster-num "This retrieve_and_rank instance contains %d cluster(s)."
     :service-delete-confirm "Are you sure you want to delete service '%s'"
     :deleting-rnr-key "Deleting key for retrieve_and_rank service '%s'."
     :deleting-rnr-service "Deleting retrieve_and_rank service '%s'."
     :rnr-deleted
     (str "Deletion initiated for retrieve_and_rank service '%s'."
          new-line "The service will be deleted shortly.")

     :missing-dc-name
     "Please specify the name of the document_conversion service to delete."
     :unknown-dc "No document_conversion service named '%s' was found."
     :not-dc-service
     "The service named '%s' is not a document_conversion service."
     :deleting-dc-key "Deleting key for document_conversion service '%s'."
     :deleting-dc-service "Deleting document_conversion service '%s'."
     :dc-deleted
     (str "Deletion initiated for document_conversion service '%s'."
          new-line "The service will be deleted shortly.")

     :missing-cluster-name "Please specify the name of the cluster to delete."
     :unknown-cluster-rnr
     (str "Couldn't determine which service to delete the cluster from."
          new-line "Please select a retrieve_and_rank service.")
     :unknown-cluster "Didn't find cluster '%s' in '%s'."
     :cluster-obj-num
     "This cluster contains %d Solr configuration(s) and %d collection(s)."
     :cluster-delete-confirm "Are you sure you want to delete cluster '%s'"
     :cluster-deleted "Cluster '%s' has been deleted from '%s'."

     :missing-config-name
     "Please specify the name of the Solr configuration to delete."
     :unknown-config-cluster
     (str "Couldn't determine which cluster to delete the configuration from."
          new-line "Please select a Solr cluster to work with.")
     :config-deleted "Solr configuration '%s' has been deleted from '%s/%s'."

     :missing-collection-name
     "Please specify the name of the collection to delete."
     :unknown-collection-cluster
     (str "Couldn't determine which cluster to delete the collection "
          "from." new-line "Please select a Solr cluster.")
     :collection-deleted "Collection '%s' has been deleted from '%s/%s'."}

  :select-messages
    {:missing-org-name "Please specify an org to change to."
     :unknown-org "Unable to locate org '%s'."
     :switch-org (str "Switched to using org '%s'." new-line
                      "Switched to using space '%s'." new-line)
     :other-spaces-num "There are %d other spaces in this org."
     :other-spaces "Other space(s) in this org include [%s]."

     :missing-space-name "Please specify a space to change to."
     :unknown-space "Unable to locate space '%s'."
     :switch-space "Switched to using space '%s'."

     :unknown-service "No service named '%s' was found."
     :wrong-service-type "'%s' is a %s service, not a %s service."
     :unclear-default-service
     "Couldn't figure out a default %s service to use."
     :service-selected "You have selected '%s' as your current %s service."

     :missing-convert-filename (str "Please give the name of a file that "
                                    "contains conversion configuration JSON.")
     :cant-read-file "Cannot read the file '%s'."
     :invalid-json "The contents of '%s' is not JSON."
     :convert-file-selected "Conversion configuration is now set to '%s'."

     :unclear-base-rnr "Please select or create a retrieve_and_rank service."
     :no-clusters "No Solr clusters found in '%s'."
     :unknown-cluster (str "No Solr cluster named '%s' found in '%s'."
                           new-line "Available clusters: %s")
     :unclear-default-cluster (str "Please select a cluster to use." new-line
                                   "Available clusters: %s")
     :multiple-clusters "There are %d with the name '%s'."
     :cluster-selected "You have selected '%s' as your current Solr cluster."

     :unclear-base-cluster
     "Please select or create a retrieve_and_rank cluster."
     :no-configs "No Solr configurations found in '%s'."
     :unknown-config
     (str "No Solr configurations named '%s' found in '%s/%s'."
          new-line "Available configurations: %s")
     :unclear-default-config
     (str "Please select a Solr configuration to use." new-line
          "Available configurations: %s")
     :config-selected
     "You have selected '%s' as your current Solr configuration."

     :no-collections "No Solr collections found in '%s/%s'."
     :unknown-collection (str "No Solr collection named '%s' found in '%s/%s'."
                              new-line "Available collections: %s")
     :unclear-default-collection (str "Please select a collection to use."
                                      new-line "Available collections: %s")
     :collection-selected
     "You have selected '%s' as your current Solr collection."}

  :list-messages
    {:available-orgs "Available organizations:"
     :current-org "Currently using organization '%s'"
     :available-spaces "Available spaces in the '%s' organization:"
     :current-space "Currently using space '%s'"

     :cluster-info "      Cluster name: %s, size: %s, status: %s"
     :cluster-free-size "free"
     :cluster-configs "         configs: "
     :cluster-collections "         collections: "
     :available-services "Available services in the '%s' space:"
     :service-info "   %s%s service named: %s"
     :service-guid "      Service GUID: %s"
     :service-cred "      Service credentials:"
     :service-no-cred "      WARNING: This service has no access credentials."

     :current-environment "Current environment:"
     :user-select "user"
     :endpoint-select "endpoint"
     :org-select "org"
     :space-select "space"
     :dc-select "document_conversion service"
     :rnr-select "retrieve_and_rank service"
     :cluster-select "cluster"
     :config-select "Solr configuration"
     :collection-select "collection"
     :convert-select "conversion configuration"
     :dc-version-select "document conversion version"

     :no-services (str "No services found in the '%s' space." new-line
                       "Use the 'create' command to add services.")
     :current-selections "Currently using the following selections:"
     :unknown-list  (str "Don't know how to '%s %s'." new-line
                         "Available actions for %s:" new-line
                         "   %s" new-line
                         "Or use 'kale %s' to list everything.")}

  :refresh-messages
    {:reloaded-services "Reloaded services in space '%s'."}

  :get-messages
    {:missing-config "Please specify a Solr configuration name to download."
     :missing-cluster "Please select or create a cluster to work with."
     :unknown-config "A Solr configuration named '%s' does not exist."
     :saved-config "Configuration saved into '%s.zip'."}

  :dry-run-messages
    {:empty-config "Note: Using the default conversion configuration: \"{}\"."
     :converting "Converting '%s' ..."
     :conversion-failed "Conversion failed for '%s':"
     :completed " completed."
     :warnings " warnings:"
     :invalid-json " completed, but output is invalid JSON."
     :conversion-completed
     (str "Conversion completed. Please find the converted output in "
          "the directory 'converted'.")}

  :search-messages
    {:id-label "     id: "
     :title-label "  title: "
     :snippet-label "snippet: "
     :missing-collection "Please create or select a collection to work with."
     :found-num-results "Found %s results."}

  :service-messages
    {:trace-request "REQUEST:"
     :trace-response "RESPONSE:"
     :trace-zip-content "[ZIP CONTENT]"

     :bad-cf-token (str "The authentication token for this session "
                        "is either invalid or expired." new-line
                        "Please run 'kale login' to acquire a new one.")
     :passcode-msg
     (str new-line "To log in, you will need to provide a passcode from:"
          new-line "%s" new-line new-line
          "If you already have a passcode, type it in now; otherwise " new-line
          "press ENTER to automatically open a browser to the URL: ")
     :prompt-passcode "Passcode? "
     :opening-url "Opening browser to URL..."
     :browser-fail
     (str "Unable to open browser. You'll need to open the URL yourself,"
          new-line "either on this machine or a seperate one.")

     :invalid-solr-name
     (str "Invalid object name '%s'." new-line
          "Solr object names should only contain "
          "alphanumeric characters, periods, hyphens and underscores.")
     :user-id-fail "Unable to determine user ID."
     :rnr-no-creds
     (str "Target retrieve_and_rank service has no access credentials."
          new-line "Please select a service that does have credentials.")
     :cluster-hint (str "Try specifying a cluster size instead of "
                        "using the default 'free' size.")
     :dc-no-creds
     (str "Target document_conversion service has no access credentials."
          new-line "Please select a service that does have credentials.")}

  :getter-messages
    {:cant-read-file "Couldn't read conversion configuration file '%s'."
     :invalid-json
     "The contents of conversion configuration '%s' is not valid JSON."}})
