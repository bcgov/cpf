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
Create config maps

```
oc process -f cfg-maps.yaml | oc apply -f -
```

## Redeploy (may need to update the target namespace, as 988040-prod is used only for demonstration purposes)
```
oc -n 988040-prod delete route,deployment,service,secret,networkpolicy -l app=cpf-tomcat
oc -n 988040-prod delete secret,deployment,service,configmap -l app=cpf-db

oc process -f cpf-db.yaml -p NAMESPACE=988040-prod | oc apply -f -
oc process -f cpf-tomcat.yaml -p NAMESPACE=988040-prod -p TOOLS_NAMESPACE= 988040-tools | oc apply -f -
```
