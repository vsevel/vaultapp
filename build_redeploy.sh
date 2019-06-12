#!/usr/bin/env bash -e

./mvnw clean package
docker build -f src/main/docker/Dockerfile.jvm -t quarkus/vaultapp-jvm .
kubectl delete deploy vaultapp -n vaultapp --ignore-not-found=true
kubectl apply -n vaultapp -f src/test/k8s/vaultapp.yaml
