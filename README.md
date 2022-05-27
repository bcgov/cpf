# RECREATE CPF SETUP IN OPENSHIFT ENVIRONMENT
## Build cpf-sidecar image in tools namespace (may need to update the target namespace)
Need artifactory secret for the binary pull in cpf-sidecar.yaml to work
```
oc -n f8d63-tools create imagestream cpf-sidecar
oc -n f8d638-tools create -f cpf-sidecar.yaml
```

Make sure artifactory-creds secret is setup before starting the build:
```
oc -n f8d638-tools start-build cpf-sidecar
```

Retag the build
```
oc -n f8d638-tools tag cpf-sidecar:latest cpf-sidecar:dev
```

Add image puller
```
oc -n f8d638-tools policy add-role-to-user system:image-puller system:serviceaccount:f8d638-dev:default
```

## Create config maps

```
oc process -f cfg-maps.yaml | oc apply -f -
```
or with non-default parameters

```
 oc process -f cfg-maps.yaml -p BASE_URL=test-cpf.apps.gov.bc.ca -p DATA_PROVIDER=https://geocoder-datastore-prod.apps.silver.devops.gov.bc.ca/geocoder/ | oc apply -f -
```

## Create relevant secrets

For example, cpf-db requires loc-tools-repo secret to pull database initialization scripts (check 988040 namespace for secret's content).

## Redeploy (may need to update the target namespace, as 988040-prod is used only for demonstration purposes)
```
oc -n 988040-prod delete route,deployment,service,secret,networkpolicy -l app=cpf-tomcat
oc -n 988040-prod delete secret,deployment,service,configmap -l app=cpf-db

oc process -f cpf-db.yaml | oc apply -f -
oc process -f cpf-tomcat.yaml | oc apply -f -
```
