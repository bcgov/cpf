# CPF Template Instructions

Note: I initailly did this a long time ago and may not recall all the steps exactly.  I've written what I believe are the necessarry steps here.

I just realized, we don't use Docker anymore so these instructions will need revision

## Logon to Artifactory
There are docs provided by platforms and services (P&S) describing how to authenticate with the P&S provided Artifactory server.  The Artifactory server is used as a image repository - rather than pulling from docker hub.

```bash
:<<NOTE

Follow instructions from:

https://developer.gov.bc.ca/Artifact-Repositories-(Artifactory)#maven

You'll need to gather the provided artifactory account and password from your tools namespace.

NOTE

:<<COMMENT
  oc project 988040-tools

  # NOTE: Your secret will have a different name.
  oc get secret/artifacts-default-hdtmmt -o json | jq '.data.username' | tr -d "\"" | base64 -d
  oc get secret/artifacts-default-hdtmmt -o json | jq '.data.password' | tr -d "\"" | base64 -d
COMMENT

# These will be returned by running the command above.

USERNAME=${RAPLACE_WITH_THE_CORREC_USERNMAE}
PASSWORD=${RAPLACE_WITH_THE_CORREC_PASSWORD}

docker login -u  $USERNAME -p $PASSWORD artifacts.developer.gov.bc.ca/docker-remote
```

## Pull Postgres locally.

```bash
#!/bin/bash

docker pull artifacts.developer.gov.bc.ca/docker-remote/postgres:13.4

```

## Import Busybox

```bash
#!/bin/bash

# oc import-image busybos:1.33.1 --from=registry.hub.docker.com/library/postgres:13.4 --confirm
:<<COMMENT

There is a docker-secrete setup however it works for the host docker-remote.artifacts.developer.gov.bc.ca and not
artifacts.developer.gov.bc.ca/docker-remote.

COMMENT

oc import-image busybox:latest \
   --from=docker-remote.artifacts.developer.gov.bc.ca/busybox:1.33.1 \
   --confirm \
  -n 988040-tools
```

## Import Postgres

```bash
#!/bin/bash

# oc import-image postgres:13.4 --from=registry.hub.docker.com/library/postgres:13.4 --confirm
:<<COMMENT

There is a docker-secrete setup however it works for the host docker-remote.artifacts.developer.gov.bc.ca and not
artifacts.developer.gov.bc.ca/docker-remote.

COMMENT

oc import-image postgres:13.4 \
   --from=docker-remote.artifacts.developer.gov.bc.ca/postgres:13.4 \
   --confirm \
  -n 988040-tools
```


## How to list repos in artifactory

```bash
#!/bin/bash

:<<COMMENT
  oc project 988040-tools
  oc get secret/artifacts-default-hdtmmt -o json | jq '.data.username' | tr -d "\"" | base64 -d
  oc get secret/artifacts-default-hdtmmt -o json | jq '.data.password' | tr -d "\"" | base64 -d
COMMENT

USERNAME=${GET THE USER NAME FROM ABOVE}
PASSWORD=${GET THE PASSWORD FROM ABOVE}

curl -s -u $USERNAME:$PASSWORD -X GET "https://artifacts.developer.gov.bc.ca/artifactory/api/repositories?type=remote" | \
  jq -r '(["ARTIFACTORYKEY","SOURCEURL"] | (., map(length*"-"))), (.[] | [.key, .url]) | @tsv' | column -t
```

# Create BuildConfig

This will create a buildconfig for the sidecar container.
Note: you must define or specify the NAMESPACE
```bash
oc apply -f cpf-sidecar.yaml \
  -n ${NAMESPACE}
```

# Deploy CPF

## Example `params-file.dev.txt`  
```
CPF_IS_TAG=latest
DATA_ADMIN_APP_IS_TAG=latest
TOOLS_NAMESPACE=988040-tools
BASE_URL=https\://bcarg-cpf-d.apps.gov.bc.ca
```

## Provision CPF
### note: you need to defind NAMESPACE
```bash
oc process -f ./cpf-template.yaml \
  --params-file=params-file.dev.txt -o yaml | \
  oc apply -f - -n ${NAMESPACE}
```
