#!/bin/bash
set -e

# --- CONFIGURAZIONE ---
# Modifica questi valori con le credenziali del tuo server
SERVER_USER="root"
SERVER_IP="192.168.1.111"
SERVER_PATH="/home/$SERVER_USER/finmanager-k8s"

echo "========================================="
echo "1. Costruzione immagini Docker per K8s..."
echo "========================================="

# Costruisci i microservizi backend
echo "-> Costruisco transaction-service..."
docker build -t finmanager/transaction-service:latest ./financial-manager-transaction

echo "-> Costruisco dashboard-service..."
docker build -t finmanager/dashboard-service:latest ./financial-manager-dashboard

echo "-> Costruisco user-service..."
docker build -t finmanager/user-service:latest ./financial-manager-user

# Costruisci il frontend passando i path relativi (verranno gestiti dal proxy Nginx interno)
echo "-> Costruisco frontend-service..."
docker build \
  --build-arg VITE_TRANSACTION_API_URL=/api/transaction \
  --build-arg VITE_DASHBOARD_API_URL=/api/dashboard \
  --build-arg VITE_USER_API_URL=/api/user \
  -t finmanager/frontend-service:latest ./financial-manager-frontend

echo ""
echo "========================================="
echo "2. Salvataggio immagini in formato tar..."
echo "========================================="
docker save finmanager/transaction-service:latest > transaction.tar
docker save finmanager/dashboard-service:latest > dashboard.tar
docker save finmanager/user-service:latest > user.tar
docker save finmanager/frontend-service:latest > frontend.tar

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
  # sudo k3s ctr images import transaction.tar dashboard.tar user.tar frontend.tar
  
  # Se usi MicroK8s, decommenta la riga sotto:
  # microk8s ctr image import transaction.tar dashboard.tar user.tar frontend.tar
  
  # Se usi Docker normale nel server K8s:
  sudo docker load < transaction.tar
  sudo docker load < dashboard.tar
  sudo docker load < user.tar
  sudo docker load < frontend.tar
  
  echo "-> Applico i manifest Kubernetes..."
  kubectl apply -f k8s/finmanager.yaml
  
  echo "-> Deploy completato! Verifica i pod con: kubectl get pods -n finmanager"
EOF

# Pulizia file temporanei locali
rm *.tar
echo "Tutto fatto con successo!"
