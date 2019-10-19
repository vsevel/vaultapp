#!/usr/bin/env bash

./mvnw clean install
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/vaultapp-jvm .
kubectl config set-context $(kubectl config current-context) --namespace=vaultapp
kubectl delete deploy vaultapp -n vaultapp --ignore-not-found=true
kubectl apply -f src/test/k8s/vaultapp.yaml
sleep 5
vaultapp=$(kubectl get pod -n vaultapp -l app=vaultapp -o jsonpath="{.items[0].metadata.name}")
kubectl logs --follow -n vaultapp $vaultapp

