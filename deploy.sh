#!/bin/bash
set -e

# --- CONFIGURAZIONE ---
# Sovrascrivibili da ambiente: SERVER_USER=me SERVER_IP=10.0.0.5 ./deploy.sh
SERVER_USER="${SERVER_USER:-root}"
SERVER_IP="${SERVER_IP:-192.168.1.111}"
SERVER_PATH="/home/$SERVER_USER/finmanager-k8s"

# Tag unico per build: con ":latest" fisso K8s non riavvia i pod dopo un nuovo deploy
TAG="$(date +%Y%m%d%H%M%S)"

# Password dei database. Serve solo se il Secret "finmanager-db" non esiste ancora nel cluster.
# Se i database esistono gia' usa la password attuale (prima dell'introduzione del Secret era "password"):
# Postgres la applica solo alla prima creazione del volume.
DB_PASSWORD="${DB_PASSWORD:-}"

echo "========================================="
echo "1. Costruzione immagini Docker per K8s..."
echo "========================================="

# Costruisci i microservizi backend
echo "-> Costruisco transaction-service..."
docker build -t finmanager/transaction-service:$TAG ./financial-manager-transaction

echo "-> Costruisco user-service..."
docker build -t finmanager/user-service:$TAG ./financial-manager-user

# Costruisci il frontend passando i path relativi (verranno gestiti dal proxy Nginx interno)
echo "-> Costruisco frontend-service..."
docker build \
  --build-arg VITE_TRANSACTION_API_URL=/api/transaction \
  --build-arg VITE_USER_API_URL=/api/user \
  -t finmanager/frontend-service:$TAG ./financial-manager-frontend

echo ""
echo "========================================="
echo "2. Salvataggio immagini in formato tar..."
echo "========================================="
docker save finmanager/transaction-service:$TAG > transaction.tar
docker save finmanager/user-service:$TAG > user.tar
docker save finmanager/frontend-service:$TAG > frontend.tar

echo ""
echo "========================================="
echo "3. Trasferimento file sul server ($SERVER_IP)..."
echo "========================================="
ssh $SERVER_USER@$SERVER_IP "mkdir -p $SERVER_PATH/k8s"
scp *.tar $SERVER_USER@$SERVER_IP:$SERVER_PATH/
scp -r k8s/* $SERVER_USER@$SERVER_IP:$SERVER_PATH/k8s/

echo ""
echo "========================================="
echo "4. Importazione immagini e Deploy su K8s..."
echo "========================================="
ssh $SERVER_USER@$SERVER_IP << EOF
  cd $SERVER_PATH
  
  echo "-> Importo le immagini in K8s..."
  # Se usi k3s, decommenta la riga sotto e commenta docker load:
  # sudo k3s ctr images import transaction.tar user.tar frontend.tar
  
  # Se usi MicroK8s, decommenta la riga sotto:
  # microk8s ctr image import transaction.tar user.tar frontend.tar
  
  # Se usi Docker normale nel server K8s:
  sudo docker load < transaction.tar
  sudo docker load <
  sudo docker load < user.tar
  sudo docker load < frontend.tar
  
  echo "-> Preparo namespace e Secret del database..."
  kubectl create namespace finmanager --dry-run=client -o yaml | kubectl apply -f -
  if ! kubectl -n finmanager get secret finmanager-db >/dev/null 2>&1; then
    if [ -z "$DB_PASSWORD" ]; then
      echo "ERRORE: Secret finmanager-db assente. Rilancia con DB_PASSWORD=... ./deploy.sh"
      exit 1
    fi
    kubectl -n finmanager create secret generic finmanager-db --from-literal=password='$DB_PASSWORD'
  fi

  echo "-> Rimuovo i vecchi dashboard-service e Kafka, se presenti (il volume pg-dash-pvc resta: eliminalo a mano quando vuoi)..."
  kubectl -n finmanager delete deployment dashboard-service kafka pg-dash --ignore-not-found
  kubectl -n finmanager delete service dashboard-service kafka pg-dash --ignore-not-found

  echo "-> Applico i manifest Kubernetes (immagini taggate $TAG)..."
  sed "s#\(image: finmanager/[a-z-]*\):latest#\1:$TAG#" k8s/finmanager.yaml | kubectl apply -f -
  
  echo "-> Deploy completato! Verifica i pod con: kubectl get pods -n finmanager"
EOF

# Pulizia file temporanei locali
rm *.tar
echo "Tutto fatto con successo!"
