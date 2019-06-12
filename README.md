# vaultapp
vault quarkus application example

Create namespaces
```
kubectl create namespace vault
kubectl create namespace vaultapp
```

Deploy Postgres
```
kubectl config set-context $(kubectl config current-context) --namespace=vaultapp
kubectl apply -f src/test/k8s/postgres.yaml
postgres=$(kubectl get pod -n vaultapp -l app=postgres -o jsonpath="{.items[0].metadata.name}")

# execute next commands from within shell
kubectl exec -n vaultapp -it $postgres bash

    psql -U postgres
    create database mydb;
    \c mydb
    create user myuser with encrypted password 'mypass';
    \du
    CREATE TABLE "public"."gift" (id bigint PRIMARY KEY NOT NULL,name varchar(255));
    \dt
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO myuser;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public to myuser;
    \q
    
    psql mydb myuser
    select * from gift;
    \q

exit
```

Deploy Vault
```
kubectl config set-context $(kubectl config current-context) --namespace=vault
./src/test/k8s/create-cert.sh
kubectl apply -f src/test/k8s/vault.yaml
vault=$(kubectl get pod -n vault -l app=vault -o jsonpath="{.items[0].metadata.name}")

# execute next commands from within shell
kubectl exec -n vault -it $vault sh
    vault operator init -key-shares=1 -key-threshold=1 --tls-skip-verify
    # => Unseal Key 1: <KEY>
    # => Initial Root Token: <TOKEN>
    export VAULT_TOKEN=<TOKEN>
    export VAULT_CACERT=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt
    vault operator unseal <KEY>
        
    # k8s auth
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
  capabilities = ["read"]
  }
path "database/creds/mydbrole" {
  capabilities = [ "read" ]
}
EOF

    # create vault role
    vault write auth/kubernetes/role/myapprole \
      bound_service_account_names='*' \
      bound_service_account_namespaces='vaultapp' \
      policies=mypolicy ttl=2h max_ttl=12h
      
    # static secrets
    vault secrets enable -path=secret kv
    vault kv put secret/foo password=mypass
    vault kv get secret/foo
      
    # dynamic secrets
    vault secrets enable database
    
    vault write database/config/mydb \
        plugin_name=postgresql-database-plugin \
        allowed_roles="mydbrole" \
        connection_url="postgresql://{{username}}:{{password}}@postgres.vaultapp.svc.cluster.local:5432/mydb?sslmode=disable" \
        username="postgres" \
        password="bar"  
    
    #default_ttl="1m"
    #max_ttl="3m"
    default_ttl="2h"
    max_ttl="12h"
    
cat << EOF > /tmp/readonly.sql
CREATE ROLE "{{name}}" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}';
-- GRANT USAGE ON SCHEMA public TO "{{name}}";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "{{name}}";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public to "{{name}}";
-- ALTER DEFAULT PRIVILEGES FOR ROLE "{{name}}" IN SCHEMA public
-- GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "{{name}}";
EOF
    
    vault write database/roles/mydbrole \
        db_name=mydb \
        creation_statements=@/tmp/readonly.sql \
        revocation_statements="ALTER ROLE \"{{name}}\" NOLOGIN;" \
        renew_statements="ALTER ROLE \"{{name}}\" VALID UNTIL '{{expiration}}';" \
        default_ttl="$default_ttl" \
        max_ttl="$max_ttl"
    
    vault read database/creds/mydbrole
    # => username & password

exit
```

Deploy Vault App
```
kubectl config set-context $(kubectl config current-context) --namespace=vaultapp
kubectl apply -f src/test/k8s/vaultapp.yaml
vaultapp=$(kubectl get pod -n vaultapp -l app=vaultapp -o jsonpath="{.items[0].metadata.name}")
kubectl logs --follow -n vaultapp $vaultapp
```

Invoke gift endpoint
```
# add gift
curl --request POST http://localhost:30400/gift?name=toto
# list gifts
curl http://localhost:30400/gift
# call in a loop
while :; do curl http://localhost:30400/gift; sleep 9; done
```

Useful: Create self signed certificate
```
openssl req -x509 -nodes -days 730 -newkey rsa:2048 -keyout vault-selfsigned-key.pem -out vault-selfsigned.pem -config vault-selfsigned.conf -extensions 'v3_req'
cp vault-selfsigned-key.pem tls.key
cp vault-selfsigned.pem tls.crt
kubectl delete secret vault-selfsigned-tls -n vault
kubectl create secret tls vault-selfsigned-tls --key ./tls.key --cert ./tls.crt -n vault
```

Cleanup
```
kubectl delete namespace vault
kubectl delete namespace vaultapp 
```


