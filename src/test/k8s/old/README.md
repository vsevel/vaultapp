





start postgres:
```
docker run --rm --name mypostgres -it -p 5432:5432 -v /Users/vsevel/dev/github/quarkus/pgdata:/var/lib/postgresql/data -e POSTGRES_PASSWORD=bar postgres
docker exec -it mypostgres bash
```

connect as admin:
```
psql -U postgres
```

list databases:
```
\l
```

connect to mydb:
```
\c mydb
```

list tables:
```
\dt
```

create db & user:
```
create database mydb;
create user myuser with encrypted password 'mypass';
# grant all privileges on database mydb to myuser;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO myuser;


revoke ALL ON ALL TABLES IN SCHEMA public from myuser;
revoke all privileges on database mydb from myuser;

```

connect as user:
```
psql -d mydb -U myuser
```

change user password:
```
ALTER USER myuser WITH PASSWORD 'new_password';
```

---------------------------------

Create Vault Namespace:
```
kubectl create namespace vault
kubectl config set-context $(kubectl config current-context) --namespace=vault
```
Deploy Vault:
```
./create-cert.sh
export K8S=extensions/agroal/deployment/src/test/vault
kubectl create configmap vault-config --from-file=$K8S/vault-config.json
kubectl apply -f $K8S/vault-deployment.yaml
kubectl apply -f $K8S/vault-service-clusterip.yaml
kubectl apply -f $K8S/vault-service-nodeport.yaml
```
Authorize auth-delegator for Vault:
```
kubectl apply -f $K8S/cluster-role-auth-delegator.yaml
```

Initialize Vault:
```
pod=$(kubectl get pods --output=jsonpath={.items..metadata.name})
kubectl exec -it $pod sh
    export VAULT_ADDR=http://127.0.0.1:8200
    vault status
    vault operator init -key-shares=1 -key-threshold=1
        => Unseal Key 1: ...
        => Initial Root Token: ...
    vault operator unseal XXX
    export VAULT_TOKEN=XXX
    
    # write password
    vault secrets enable -path=secret kv
    vault kv put secret/foo password=bar
    vault kv get secret/foo
    
    vault auth enable kubernetes
    
    cd /var/run/secrets/kubernetes.io/serviceaccount
    
    # configure vault kubernetes authentication
    vault write auth/kubernetes/config \
      token_reviewer_jwt=$(cat token) \
      kubernetes_host=https://kubernetes.default.svc \
      kubernetes_ca_cert=@ca.crt
    
    # create vault policy  
cat <<EOF | vault policy write mypolicy -
path "secret/foo" {
  capabilities = ["read", "list"]
  }
path "database/creds/my-role" {
  capabilities = [ "read" ]
}
EOF

    # create vault role
    vault write auth/kubernetes/role/myapprole \
      bound_service_account_names='*' \
      bound_service_account_namespaces='default' \
      policies=mypolicy ttl=2h max_ttl=12h
exit
```

Fetch /var/run/secrets/kubernetes.io/serviceaccount/ca.crt from any pod and add it to extensions/agroal/deployment/src/test/vault/local-test

```
jwt=$(kubectl get $(kubectl get secrets -n default -o name | grep default-token) -n default --output=jsonpath={.data.token} | base64 --decode)
```

Copy this value into extensions/agroal/deployment/src/test/vault/local-test/token.jwt
You can now execute VaultDataSourceConfigITCase.testDataSource()

Start postgres locally:
```
docker run --rm --name mypostgres -it -p 5432:5432 -v /Users/vsevel/dev/github/quarkus/pgdata:/var/lib/postgresql/data -e POSTGRES_PASSWORD=bar postgres
```

Create a quarkus vaultapp sample.

Add this application.properties in src/test/resources:
```
# configure your datasource
quarkus.datasource.url=jdbc:postgresql://localhost:5432/postgres
quarkus.datasource.driver=org.postgresql.Driver
quarkus.datasource.username=postgres

quarkus.datasource.vault-password.jwt-path=src/test/vault/local-test/token.jwt
quarkus.datasource.vault-password.role=myapprole
quarkus.datasource.vault-password.secret-path=foo
quarkus.datasource.vault-password.secret-key=password
quarkus.datasource.vault-password.url=http://localhost:30200

# drop and create the database at startup (use `update` to only update the schema)
quarkus.hibernate-orm.database.generation=drop-and-create
```

Copy previous extensions/agroal/deployment/src/test/vault/local-test/token.jwt in src/test/resources.

Change src/main/resources/application.properties to (replace <POSTGRES> below):
```
# configure your datasource
quarkus.datasource.url=jdbc:postgresql://<POSTGRES>:5432/postgres
quarkus.datasource.driver=org.postgresql.Driver
quarkus.datasource.username=postgres

quarkus.datasource.vault-password.role=myapprole
quarkus.datasource.vault-password.secret-path=foo
quarkus.datasource.vault-password.secret-key=password
quarkus.datasource.vault-password.url=http://vault.vault:8200

# drop and create the database at startup (use `update` to only update the schema)
quarkus.hibernate-orm.database.generation=drop-and-create
```

Build a docker image for the getting started application:
```
./mvnw clean package
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/vaultapp-jvm .
```

Try with docker:
```
jwtpath=<PATH to token.jwt>
docker run -i --rm -v $jwtpath:/var/run/secrets/kubernetes.io/serviceaccount/token -e myvault.url=http://$(ipconfig getifaddr en1):30200 -p 8080:8080 quarkus/vaultapp-jvm
```

You should see: 
```
using password properties: {password=bar}
```

Deploy in default ns:
```
kubectl apply -f $K8S/vaultapp.yaml -n default
kubectl logs vaultapp -n default
```

You should see: 
```
using password properties: {password=bar}
```

Configure db secret engine for access to db 'mydb': 
```
vault secrets enable database

vault write database/config/mydb \
    plugin_name=postgresql-database-plugin \
    allowed_roles="my-role" \
    connection_url="postgresql://{{username}}:{{password}}@postgres.default.svc.cluster.local:5432/mydb?sslmode=disable" \
    username="postgres" \
    password="bar"


default_ttl="1m"
max_ttl="3m"
# or    
# default_ttl="2h"
# max_ttl="12h"


cat << EOF > readonly.sql
CREATE ROLE "{{name}}" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
-- GRANT USAGE ON SCHEMA public TO "{{name}}";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "{{name}}";
-- ALTER DEFAULT PRIVILEGES FOR ROLE "{{name}}" IN SCHEMA public
-- GRANT SELECT ON TABLES TO "{{name}}";
EOF

vault write database/roles/my-role \
    db_name=mydb \
    creation_statements=@readonly.sql \
    revocation_statements="ALTER ROLE \"{{name}}\" NOLOGIN;" \
    renew_statements="ALTER ROLE \"{{name}}\" VALID UNTIL '{{expiration}}';" \
    default_ttl="$default_ttl" \
    max_ttl="$max_ttl"

vault read database/creds/my-role
```

Create self signed certificate:
```
openssl req -x509 -nodes -days 730 -newkey rsa:2048 -keyout vault-selfsigned-key.pem -out vault-selfsigned.pem -config vault-selfsigned.conf -extensions 'v3_req'
cp vault-selfsigned-key.pem tls.key
cp vault-selfsigned.pem tls.crt
kubectl delete secret vault-selfsigned-tls -n vault
kubectl create secret tls vault-selfsigned-tls --key ./tls.key --cert ./tls.crt -n vault
```

Call endpoint infinitely:
```
while :; do curl http://localhost:30400/gift; sleep 9; done
```

Create a gift:
```
curl http://localhost:30400/gift?name=toto
```

Rebase from upstream branch:
```
git remote add upstream https://github.com/quarkusio/quarkus.git
...
git fetch upstream
git rebase upstream/master
```

Cleanup
```
kubectl delete pod vaultapp -n default
kubectl delete namespace vault 
```



