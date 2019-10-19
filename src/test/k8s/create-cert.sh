#!/bin/bash

BASEDIR=$(dirname $0)
echo executing from $BASEDIR
rm -rf $BASEDIR/local-test
mkdir $BASEDIR/local-test
cp $BASEDIR/vault-csr.json $BASEDIR/local-test
pushd $BASEDIR/local-test

kubectl delete csr vault.vault --ignore-not-found=false

# Create private key and CSR
cfssl genkey vault-csr.json | cfssljson -bare vault

# Create CSR k8s object
cat <<EOF | kubectl create -f -
apiVersion: certificates.k8s.io/v1beta1
kind: CertificateSigningRequest
metadata:
  name: vault.vault
spec:
  groups:
  - system:authenticated
  request: $(cat vault.csr | base64 | tr -d '\n')
  usages:
  - digital signature
  - key encipherment
  - server auth
EOF

# Approve certificate
kubectl certificate approve vault.vault

sleep 5s

# Download public key
kubectl get csr vault.vault -o jsonpath='{.status.certificate}' | base64 --decode > vault.crt

cp vault-key.pem tls.key
cp vault.crt tls.crt
kubectl delete secret vault-tls --ignore-not-found=false
kubectl create secret tls vault-tls --key ./tls.key --cert ./tls.crt -n vault

# build a jks from k8s ca.crt
postgres=$(kubectl get pod -n vaultapp -l app=postgres -o jsonpath="{.items[0].metadata.name}")
kubectl exec -n vaultapp $postgres cat /var/run/secrets/kubernetes.io/serviceaccount/ca.crt > ca.crt
keytool -import -file ca.crt -alias vault -keystore ca-crt.jks -noprompt -storepass changeit

# Display public key content
openssl x509 -in tls.crt -text
  #Propriétaire : CN=vault.vault.svc.cluster.local
  #Emetteur : CN=kubernetes

popd