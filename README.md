
[Link da Apresentação](https://docs.google.com/presentation/d/1_iAoqxvkhfIbr3lNTG8QRVDxEJ2wivG1znHNzM7zkho/edit?usp=sharing).

helm install my-release oci://registry-1.docker.io/bitnamicharts/postgresql --set auth.postgresPassword=admin

# Nodeport

psql  -h 172.20.0.2 -p30432 -U postgres postgres


export POSTGRES_PASSWORD=$(kubectl get secret --namespace default my-release-postgresql -o jsonpath="{.data.postgres-password}" | base64 -d)


kubectl run my-release-postgresql-client --rm --tty -i --restart='Never' --namespace default --image docker.io/bitnami/postgresql:16.0.0-debian-11-r3 --env="PGPASSWORD=$POSTGRES_PASSWORD"       --command -- bash

psql --host my-release-postgresql -U postgres -d postgres -p 5432

CREATE ROLE "rw" NOINHERIT;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO "rw";
GRANT USAGE ON SCHEMA public TO "rw";



https://developer.hashicorp.com/vault/docs/platform/k8s/csi


helm repo add hashicorp https://helm.releases.hashicorp.com
helm repo update
helm install vault hashicorp/vault \
    --set "server.dev.enabled=true" \
    --set "injector.enabled=false" \
    --set "csi.enabled=true"

kubectl exec -it vault-0 -- sh

vault secrets enable database

vault write database/config/postgresql \
    plugin_name=postgresql-database-plugin \
    allowed_roles="*" \
    connection_url="postgresql://postgres:admin@my-release-postgresql/postgres?sslmode=disable" \
    username="postgres" \
    password="admin"

tee readwrite.sql <<EOF
CREATE ROLE "{{name}}" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}' INHERIT;
GRANT rw TO "{{name}}";
EOF

vault write database/roles/readwrite \
      db_name=postgresql \
      creation_statements=@readwrite.sql \
      default_ttl=40s \
      max_ttl=40s



vault read database/creds/readwrite


vault auth enable kubernetes

vault write auth/kubernetes/config \
    kubernetes_host="https://$KUBERNETES_PORT_443_TCP_ADDR:443"

vault policy write internal-app - <<EOF
path "database/creds/readwrite" {
  capabilities = ["read"]
}
EOF


vault write auth/kubernetes/role/database \
    bound_service_account_names=webapp-sa \
    bound_service_account_namespaces=default \
    policies=internal-app \
    ttl=1h


helm repo add secrets-store-csi-driver https://kubernetes-sigs.github.io/secrets-store-csi-driver/charts

helm install csi secrets-store-csi-driver/secrets-store-csi-driver \
    --set syncSecret.enabled=true --set rotationPollInterval=30s \
     --set secrets-store-csi-driver.enableSecretRotation=true --set enableSecretRotation=True




cat > spc-vault-database.yaml <<EOF
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: vault-database
spec:
  provider: vault
  parameters:
    vaultAddress: "http://vault.default:8200"
    roleName: "database"
    objects: |
      - objectName: "db-password"
        secretPath: "database/creds/readwrite"
EOF

kubectl create serviceaccount webapp-sa

cat > webapp-pod.yaml <<EOF
kind: Pod
apiVersion: v1
metadata
  name: webapp
  labels:
    app: webapp
spec:
  containers:
  - image: tanquetav/quarkusvault
    name: webapp
    volumeMounts:
    - name: secrets-store-inline
      mountPath: "/mnt/secrets-store"
      readOnly: true
  serviceAccountName: webapp-sa
  volumes:
    - name: secrets-store-inline
      csi:
        driver: secrets-store.csi.k8s.io
        readOnly: true
        volumeAttributes:
          secretProviderClass: "vault-database"
EOF


cat > webapp-svc.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  labels:
    app: webapp
  name: webapp
spec:
  ports:
  - port: 80
    protocol: TCP
    targetPort: 8080
    nodePort: 32200
  type: NodePort
  selector:
    app: webapp
EOF


https://medium.com/hashicorp-engineering/hashicorp-vault-delivering-secrets-with-kubernetes-1b358c03b2a3
